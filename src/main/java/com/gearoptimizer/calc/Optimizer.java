package com.gearoptimizer.calc;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.GameDataRepository;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.ItemRequirements;
import com.gearoptimizer.data.Monster;
import com.gearoptimizer.data.OwnedItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

/**
 * Finds the best owned loadout for each combat style.
 *
 * <p>For each candidate weapon the other slots are improved one at a time (coordinate ascent)
 * until nothing changes. Ascent is also started from every owned set (void, crystal, obsidian,
 * inquisitor) so set bonuses that only pay off with all pieces are not missed.
 */
public class Optimizer
{
	private static final int MAX_PASSES = 4;
	private static final int WEAPONS_TO_REFINE = 12;
	private static final int SPELLS_PER_WEAPON = 3;
	/** Per defence/prayer point; DPS always outweighs it. */
	private static final double TIE_BREAK = 1e-6;
	/** Per worn item; smaller than one defence point. */
	private static final double WORN_BONUS = 5e-7;
	/** Per gp; even a 2b item stays below the worn bonus. */
	private static final double PRICE_PENALTY = 1e-16;
	/** Smallest score gain an ascent step needs; small enough for the price tie-break to count. */
	private static final double MIN_IMPROVEMENT = 1e-12;
	/** Taken off setups whose weapon can't hurt the monster, so any working setup ranks above them. */
	private static final double CANNOT_DAMAGE_PENALTY = 1000;
	/** In max DPS mode, how many of the most defensive items each slot keeps for the defence slider. */
	private static final int MAX_MODE_DEFENSIVE = 5;

	private static final GearSlot[] ASCENT_ORDER = {
		GearSlot.AMMO, GearSlot.HEAD, GearSlot.NECK, GearSlot.BODY, GearSlot.LEGS, GearSlot.SHIELD,
		GearSlot.HANDS, GearSlot.FEET, GearSlot.RING, GearSlot.CAPE,
	};

	private static final String[][][] SETS = {
		{{"head", "void melee helm"}, {"body", "elite void top"}, {"legs", "elite void robe"}, {"hands", "void knight gloves"}},
		{{"head", "void melee helm"}, {"body", "void knight top"}, {"legs", "void knight robe"}, {"hands", "void knight gloves"}},
		{{"head", "void ranger helm"}, {"body", "elite void top"}, {"legs", "elite void robe"}, {"hands", "void knight gloves"}},
		{{"head", "void ranger helm"}, {"body", "void knight top"}, {"legs", "void knight robe"}, {"hands", "void knight gloves"}},
		{{"head", "void mage helm"}, {"body", "elite void top"}, {"legs", "elite void robe"}, {"hands", "void knight gloves"}},
		{{"head", "void mage helm"}, {"body", "void knight top"}, {"legs", "void knight robe"}, {"hands", "void knight gloves"}},
		{{"head", "crystal helm"}, {"body", "crystal body"}, {"legs", "crystal legs"}},
		{{"head", "obsidian helmet"}, {"body", "obsidian platebody"}, {"legs", "obsidian platelegs"}},
		{{"head", "inquisitor's great helm"}, {"body", "inquisitor's hauberk"}, {"legs", "inquisitor's plateskirt"}},
		{{"head", "ahrim's hood"}, {"body", "ahrim's robetop"}, {"legs", "ahrim's robeskirt"}, {"neck", "amulet of the damned"}},
	};

	@Value
	@Builder(toBuilder = true)
	public static class Input
	{
		Monster monster;
		AccountSnapshot account;
		Map<CombatStyle, CombatContext> contexts;
		/** 0 = pure DPS, 1 = pure defence. */
		double defenceWeight;
		Set<Integer> excludedItemIds;
		Set<Integer> lockedItemIds;
		/** GE prices of owned items and supplies; may be empty. */
		Map<Integer, Integer> prices;
		/** Prefer cheaper ammo/runes that keep most of the DPS. */
		boolean budget;
		/** Consider every item in the game, not just owned ones ("max DPS" mode). */
		boolean unrestricted;
	}

	@Value
	public static class Result
	{
		Map<CombatStyle, StyleResult> styles;
		StyleResult best;
		StyleResult current;
		MonsterRules rules;
		/** Owned items skipped because of level requirements: name -> what is missing. */
		Map<String, String> unwearable;
	}

	private final GameDataRepository repository;
	@Getter
	private Map<GearSlot, List<OwnedItem>> candidates;
	private MonsterRules rules;
	private Input input;
	/** The input's prices, never null. */
	private Map<Integer, Integer> prices = Map.of();
	/** Dataset ids of the items the player is wearing. */
	private Set<Integer> wornIds = Set.of();

	public Optimizer(GameDataRepository repository)
	{
		this.repository = repository;
	}

	public Result optimize(Input input)
	{
		this.input = input;
		this.prices = input.getPrices() == null ? Map.of() : input.getPrices();
		this.rules = MonsterRules.forMonster(input.getMonster(), input.getAccount());
		Set<Integer> worn = new java.util.HashSet<>();
		if (input.getAccount().getEquipment() != null)
		{
			for (int itemId : input.getAccount().getEquipment().values())
			{
				OwnedItem item = repository.resolve(itemId);
				if (item != null)
				{
					worn.add(item.getStats().getId());
				}
			}
		}
		this.wornIds = worn;
		Map<String, String> unwearable = new LinkedHashMap<>();
		this.candidates = input.isUnrestricted() ? collectAllItems() : collectCandidates(unwearable);

		Map<CombatStyle, StyleResult> styles = new EnumMap<>(CombatStyle.class);
		StyleResult best = null;
		for (CombatStyle style : CombatStyle.values())
		{
			CombatContext ctx = input.getContexts().get(style);
			if (ctx == null)
			{
				continue;
			}
			StyleResult result = optimizeStyle(style, ctx);
			if (result != null)
			{
				styles.put(style, result);
				if (best == null || result.getScore() > best.getScore())
				{
					best = result;
				}
			}
		}

		return new Result(styles, best, evaluateCurrent(), rules, unwearable);
	}

	public double score(Loadout loadout, DpsResult dps, CombatContext ctx)
	{
		double weight = input.getDefenceWeight();
		int defence = defenceScore(loadout, ctx.getMonster());
		double tieBreak = TIE_BREAK * (loadout.total(i -> i.getDefensive().total()) + 3 * loadout.total(i -> i.getBonuses().getPrayer()));
		// Among otherwise equal items: keep what's worn (fewer swaps), then prefer the cheaper one
		for (OwnedItem item : loadout.items())
		{
			if (wornIds.contains(item.getStats().getId()))
			{
				tieBreak += WORN_BONUS;
			}
			Integer price = prices.get(item.getItemId());
			if (price != null && price > 0)
			{
				tieBreak -= price * PRICE_PENALTY;
			}
		}
		double effective = StyleResult.effectiveDps(dps.getDps(), ctx.getMonster().getHitpoints(), rules.finishingSeconds(loadout));
		return effective * (1 - weight) + weight * defence / 100.0 + tieBreak;
	}

	private Map<GearSlot, List<OwnedItem>> collectCandidates(Map<String, String> unwearable)
	{
		AccountSnapshot account = input.getAccount();
		Map<Integer, OwnedItem> byDatasetId = new HashMap<>();
		for (int itemId : account.ownedItemIds())
		{
			if (input.getExcludedItemIds().contains(itemId))
			{
				continue;
			}
			OwnedItem owned = repository.resolve(itemId);
			if (owned == null || input.getExcludedItemIds().contains(owned.getStats().getId())
				|| UpgradeFinder.isMinigameOnly(owned.getStats()))
			{
				continue;
			}
			String missing = ItemRequirements.missingRequirement(owned.getStats(), account.getLevels());
			if (missing != null && !account.getLevels().isEmpty())
			{
				unwearable.put(owned.getStats().getName(), missing);
				continue;
			}
			OwnedItem existing = byDatasetId.get(owned.getStats().getId());
			if (existing == null || (existing.isNeedsCharges() && !owned.isNeedsCharges()))
			{
				byDatasetId.put(owned.getStats().getId(), owned);
			}
		}

		Map<GearSlot, List<OwnedItem>> bySlot = new EnumMap<>(GearSlot.class);
		for (GearSlot slot : GearSlot.values())
		{
			bySlot.put(slot, new ArrayList<>());
		}
		for (OwnedItem owned : byDatasetId.values())
		{
			bySlot.get(owned.getStats().getGearSlot()).add(owned);
		}
		return bySlot;
	}

	/** Per slot and style, how many of the strongest unowned items max DPS mode considers. */
	private static final int MAX_MODE_PER_SLOT = 25;
	private static final java.util.regex.Pattern SPECIAL_EFFECT = java.util.regex.Pattern.compile(
		"slayer helmet|black mask|salve amulet|void|^crystal (helm|body|legs)|obsidian|inquisitor|berserker necklace|ahrim|amulet of the damned"
			+ "|^tome of|efaritay|chaos gauntlets|ava's|dizana|assembler|accumulator|amulet of avarice",
		java.util.regex.Pattern.CASE_INSENSITIVE);

	/**
	 * Candidates for max DPS mode: every wearable item in the game (within the player's levels), with
	 * cosmetic duplicates removed and each armour slot narrowed to the items that could matter.
	 */
	private Map<GearSlot, List<OwnedItem>> collectAllItems()
	{
		AccountSnapshot account = input.getAccount();
		Map<GearSlot, List<OwnedItem>> bySlot = new EnumMap<>(GearSlot.class);
		for (GearSlot slot : GearSlot.values())
		{
			bySlot.put(slot, new ArrayList<>());
		}
		Set<String> seen = new java.util.HashSet<>();
		for (EquipmentItem item : repository.getEquipment())
		{
			if (UpgradeFinder.skip(item) || input.getExcludedItemIds().contains(item.getId())
				|| (!account.getLevels().isEmpty() && ItemRequirements.missingRequirement(item, account.getLevels()) != null)
				|| !seen.add(Experiment.statSignature(item)))
			{
				continue;
			}
			bySlot.get(item.getGearSlot()).add(new OwnedItem(item, item.getId(), false));
		}

		for (GearSlot slot : GearSlot.values())
		{
			if (slot == GearSlot.WEAPON)
			{
				continue;
			}
			List<OwnedItem> all = bySlot.get(slot);
			java.util.Set<OwnedItem> keep = new java.util.LinkedHashSet<>();
			MonsterRules.SlotRequirement requirement = rules.requirementFor(slot);
			for (OwnedItem item : all)
			{
				EquipmentItem stats = item.getStats();
				if (SPECIAL_EFFECT.matcher(stats.getName()).find() || (requirement != null && requirement.matches(stats))
					|| (slot == GearSlot.AMMO && GearEffects.isProjectile(stats)))
				{
					keep.add(item);
				}
			}
			for (CombatStyle style : CombatStyle.values())
			{
				List<OwnedItem> sorted = new ArrayList<>(all);
				sorted.sort(Comparator.comparingDouble((OwnedItem item) -> -offenceHeuristic(item.getStats(), style)));
				keep.addAll(sorted.subList(0, Math.min(MAX_MODE_PER_SLOT, sorted.size())));
			}
			// A few of the most defensive items, for the defence slider
			List<OwnedItem> byDefence = new ArrayList<>(all);
			byDefence.sort(Comparator.comparingInt((OwnedItem item) -> -item.getStats().getDefensive().total()));
			keep.addAll(byDefence.subList(0, Math.min(MAX_MODE_DEFENSIVE, byDefence.size())));
			bySlot.put(slot, new ArrayList<>(keep));
		}
		return bySlot;
	}

	private static double offenceHeuristic(EquipmentItem item, CombatStyle style)
	{
		EquipmentItem.Attack o = item.getOffensive();
		EquipmentItem.Bonuses b = item.getBonuses();
		switch (style)
		{
			case MELEE:
				return Math.max(o.getStab(), Math.max(o.getSlash(), o.getCrush())) + b.getStr() * 1.5 + b.getPrayer() * 0.1;
			case RANGED:
				return o.getRanged() + b.getRangedStr() * 1.5 + b.getPrayer() * 0.1;
			default:
				return o.getMagic() + b.getMagicStr() * 0.3 + b.getPrayer() * 0.1;
		}
	}

	private StyleResult optimizeStyle(CombatStyle style, CombatContext ctx)
	{
		List<Loadout> starts = weaponStarts(style, ctx);
		if (starts.isEmpty())
		{
			return null;
		}

		Loadout bestLoadout = null;
		DpsResult bestDps = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (Loadout start : starts)
		{
			List<Loadout> seeds = new ArrayList<>();
			seeds.add(start);
			seeds.addAll(setSeeds(start));
			for (Loadout seed : seeds)
			{
				Loadout refined = ascend(seed, style, ctx);
				DpsResult dps = evaluate(refined, style, ctx);
				double score = scoreOrInvalid(refined, dps, ctx);
				if (score > bestScore)
				{
					bestScore = score;
					bestLoadout = refined;
					bestDps = dps;
				}
			}
		}
		if (bestLoadout == null || bestDps == null || bestDps.getDps() <= 0)
		{
			return null;
		}

		List<String> warnings = new ArrayList<>();
		for (MonsterRules.SlotRequirement requirement : rules.getSlotRequirements())
		{
			if (!requirement.matches(bestLoadout.stats(requirement.getSlot())))
			{
				warnings.add("UNSAFE: you don't own a " + requirement.getLabel() + " (" + requirement.getSlot().getDisplayName().toLowerCase()
					+ " slot). Get one before fighting this monster.");
			}
		}
		for (OwnedItem item : bestLoadout.items())
		{
			if (item.isNeedsCharges())
			{
				warnings.add(item.getStats().getName() + " needs charging");
			}
		}
		if (!rules.allowsWeapon(bestLoadout))
		{
			warnings.add("No weapon you own can damage this monster with " + style.getDisplayName().toLowerCase());
		}

		List<OwnedItem> owned = new ArrayList<>();
		candidates.values().forEach(owned::addAll);
		StyleResult draft = draft(style, bestLoadout, bestDps, ctx, bestScore);
		if (input.isBudget())
		{
			Consumables.Option pick = Consumables.budgetPick(Consumables.options(draft, owned, prices));
			if (pick != null && !pick.isCurrent())
			{
				bestLoadout = pick.getLoadout();
				bestDps = pick.getDps();
				bestScore = scoreOrInvalid(bestLoadout, bestDps, ctx);
				draft = draft(style, bestLoadout, bestDps, ctx, bestScore);
			}
		}
		List<Consumables.Option> options = Consumables.options(draft, owned, prices);
		List<SpecialAttacks.SpecOption> specs = SpecialAttacks.rank(draft, candidates.get(GearSlot.WEAPON), rules, this::wearsOwnedProtection);
		return new StyleResult(style, bestLoadout, bestDps, ctx, bestScore, defenceScore(bestLoadout, ctx.getMonster()),
			rules.checkInventory(bestLoadout, input.getAccount(), ctx), warnings, specs, rules.finishingSeconds(bestLoadout),
			Consumables.cost(bestLoadout, bestDps, prices), options, prices);
	}

	/**
	 * @return a bare result for ranking supplies and special attacks: no inventory check or costs, which only the
	 * final result needs
	 */
	private StyleResult draft(CombatStyle style, Loadout loadout, DpsResult dps, CombatContext ctx, double score)
	{
		return new StyleResult(style, loadout, dps, ctx, score, 0, List.of(), List.of(), List.of(), rules.finishingSeconds(loadout),
			Consumables.Cost.FREE, List.of(), prices);
	}

	/**
	 * Builds one starting loadout per promising weapon (with its best ammo, darts or spell already chosen).
	 */
	private List<Loadout> weaponStarts(CombatStyle style, CombatContext ctx)
	{
		List<OwnedItem> weapons = new ArrayList<>(candidates.get(GearSlot.WEAPON));
		OwnedItem lockedWeapon = locked(GearSlot.WEAPON);
		if (lockedWeapon != null && !WeaponStyles.stancesFor(lockedWeapon.getStats(), style).isEmpty())
		{
			weapons = Collections.singletonList(lockedWeapon);
		}
		else if (style == CombatStyle.MELEE)
		{
			weapons.add(null);
		}

		List<Map.Entry<Double, Loadout>> scored = new ArrayList<>();
		for (OwnedItem weapon : weapons)
		{
			EquipmentItem stats = weapon == null ? null : weapon.getStats();
			if (WeaponStyles.stancesFor(stats, style).isEmpty())
			{
				continue;
			}
			Loadout loadout = new Loadout();
			loadout.set(GearSlot.WEAPON, weapon);
			applyLockedItems(loadout, stats);
			if (!applyProtection(loadout))
			{
				continue;
			}
			if (style == CombatStyle.RANGED)
			{
				if (!chooseAmmo(loadout, ctx))
				{
					continue;
				}
			}
			DpsResult dps = evaluate(loadout, style, ctx);
			if (dps.getDps() <= 0)
			{
				continue;
			}
			scored.add(Map.entry(scoreOrInvalid(loadout, dps, ctx), loadout));
		}
		scored.sort(Comparator.comparingDouble((Map.Entry<Double, Loadout> e) -> e.getKey()).reversed());

		List<Loadout> starts = new ArrayList<>();
		for (int i = 0; i < scored.size() && i < WEAPONS_TO_REFINE; i++)
		{
			starts.add(scored.get(i).getValue());
		}
		return starts;
	}

	private void applyLockedItems(Loadout loadout, EquipmentItem weapon)
	{
		for (GearSlot slot : ASCENT_ORDER)
		{
			OwnedItem lockedItem = locked(slot);
			if (lockedItem != null && !(slot == GearSlot.SHIELD && weapon != null && weapon.isTwoHanded()))
			{
				loadout.set(slot, lockedItem);
			}
		}
	}

	/**
	 * Picks ammunition (or blowpipe darts) for a ranged weapon.
	 *
	 * @return false if the weapon needs ammo the account does not have
	 */
	private boolean chooseAmmo(Loadout loadout, CombatContext ctx)
	{
		EquipmentItem weapon = loadout.weapon();
		if (loadout.isBlowpipe())
		{
			OwnedItem bestDart = null;
			for (OwnedItem item : candidates.get(GearSlot.WEAPON))
			{
				if (AmmoRules.blowpipeAccepts(weapon, item.getStats()) && (bestDart == null
					|| item.getStats().getBonuses().getRangedStr() > bestDart.getStats().getBonuses().getRangedStr()))
				{
					bestDart = item;
				}
			}
			loadout.setBlowpipeDart(bestDart);
			return true;
		}
		if (!GearEffects.usesAmmoSlot(weapon))
		{
			return true;
		}
		OwnedItem lockedAmmo = locked(GearSlot.AMMO);
		if (lockedAmmo != null && AmmoRules.compatible(weapon, lockedAmmo.getStats()))
		{
			loadout.set(GearSlot.AMMO, lockedAmmo);
			return true;
		}
		OwnedItem best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (OwnedItem ammo : candidates.get(GearSlot.AMMO))
		{
			if (!AmmoRules.compatible(weapon, ammo.getStats()))
			{
				continue;
			}
			loadout.set(GearSlot.AMMO, ammo);
			DpsResult dps = evaluate(loadout, CombatStyle.RANGED, ctx);
			double score = scoreOrInvalid(loadout, dps, ctx);
			if (score > bestScore)
			{
				bestScore = score;
				best = ammo;
			}
		}
		loadout.set(GearSlot.AMMO, best);
		return best != null;
	}

	private List<Loadout> setSeeds(Loadout start)
	{
		List<Loadout> seeds = new ArrayList<>();
		for (String[][] set : SETS)
		{
			Loadout seed = new Loadout(start);
			boolean complete = true;
			for (String[] piece : set)
			{
				GearSlot slot = GearSlot.fromKey(piece[0]);
				if (locked(slot) != null)
				{
					complete = false;
					break;
				}
				OwnedItem match = null;
				for (OwnedItem item : candidates.get(slot))
				{
					if (item.getStats().getName().toLowerCase().startsWith(piece[1]))
					{
						match = item;
						break;
					}
				}
				if (match == null)
				{
					complete = false;
					break;
				}
				seed.set(slot, match);
			}
			if (complete && wearsOwnedProtection(seed))
			{
				seeds.add(seed);
			}
		}
		return seeds;
	}

	private Loadout ascend(Loadout start, CombatStyle style, CombatContext ctx)
	{
		Loadout current = new Loadout(start);
		DpsResult dps = evaluate(current, style, ctx);
		double currentScore = scoreOrInvalid(current, dps, ctx);
		EquipmentItem weapon = current.weapon();
		boolean twoHanded = weapon != null && weapon.isTwoHanded();

		for (int pass = 0; pass < MAX_PASSES; pass++)
		{
			boolean changed = false;
			for (GearSlot slot : ASCENT_ORDER)
			{
				if ((slot == GearSlot.SHIELD && twoHanded) || locked(slot) != null)
				{
					continue;
				}
				if (slot == GearSlot.AMMO && style == CombatStyle.RANGED && GearEffects.usesAmmoSlot(weapon))
				{
					continue;
				}
				OwnedItem original = current.get(slot);
				List<OwnedItem> options = slotOptions(slot);
				if (slot == GearSlot.AMMO)
				{
					// Only ammo the weapon can fire, or non-projectile items like blessings
					options.removeIf(item -> item != null && GearEffects.isProjectile(item.getStats())
						&& !AmmoRules.compatible(weapon, item.getStats()));
				}
				// A required item (earmuffs, mirror shield...) replaces whatever is there even if it scores lower
				boolean mustReplace = !options.contains(null) && !options.contains(original);
				OwnedItem bestItem = original;
				double bestScore = mustReplace ? Double.NEGATIVE_INFINITY : currentScore;
				for (OwnedItem candidate : options)
				{
					if (candidate == original)
					{
						continue;
					}
					current.set(slot, candidate);
					double score = scoreOrInvalid(current, evaluate(current, style, ctx), ctx);
					if (score > bestScore + MIN_IMPROVEMENT || (mustReplace && bestItem == original))
					{
						bestScore = score;
						bestItem = candidate;
					}
				}
				currentScore = bestScore;
				current.set(slot, bestItem);
				if (bestItem != original)
				{
					changed = true;
				}
			}
			if (!changed)
			{
				break;
			}
		}
		// Re-run so the chosen stance/spell is stored on the loadout
		evaluate(current, style, ctx);
		return current;
	}

	/**
	 * @return the options for a slot: matching items if the monster requires one, otherwise every owned item and "empty"
	 */
	private List<OwnedItem> slotOptions(GearSlot slot)
	{
		List<OwnedItem> all = candidates.get(slot);
		MonsterRules.SlotRequirement requirement = rules.requirementFor(slot);
		if (requirement != null)
		{
			List<OwnedItem> matching = new ArrayList<>();
			for (OwnedItem item : all)
			{
				if (requirement.matches(item.getStats()))
				{
					matching.add(item);
				}
			}
			if (!matching.isEmpty())
			{
				return matching;
			}
		}
		List<OwnedItem> options = new ArrayList<>(all);
		options.add(null);
		return options;
	}

	/**
	 * Tries every stance (and, for staves, the best spells) and leaves the best one on the loadout.
	 */
	public static DpsResult evaluate(Loadout loadout, CombatStyle style, CombatContext ctx)
	{
		EquipmentItem weapon = loadout.weapon();
		List<Stance> stances = WeaponStyles.stancesFor(weapon, style);
		List<Spell> spells = Collections.singletonList(null);
		if (style == CombatStyle.MAGIC && WeaponStyles.canAutocast(weapon))
		{
			spells = bestSpells(loadout, ctx);
			if (spells.isEmpty())
			{
				return DpsResult.NONE;
			}
		}

		DpsResult best = DpsResult.NONE;
		Stance bestStance = null;
		Spell bestSpell = null;
		for (Stance stance : stances)
		{
			for (Spell spell : spells)
			{
				loadout.setStance(stance);
				loadout.setSpell(spell);
				DpsResult result = DpsCalculator.calculate(loadout, ctx);
				if (result.getDps() > best.getDps())
				{
					best = result;
					bestStance = stance;
					bestSpell = spell;
				}
			}
		}
		loadout.setStance(bestStance);
		loadout.setSpell(bestSpell);
		return best;
	}

	private static List<Spell> bestSpells(Loadout loadout, CombatContext ctx)
	{
		List<Spell> usable = new ArrayList<>();
		for (Spell spell : Spell.values())
		{
			if (spell.castable(loadout, ctx))
			{
				usable.add(spell);
			}
		}
		usable.sort(Comparator.comparingInt(Spell::getBaseMaxHit).reversed());
		// Keep the strongest few, plus the strongest elemental spell for weaknesses and tomes
		List<Spell> picked = new ArrayList<>();
		for (Spell spell : usable)
		{
			if (picked.size() < SPELLS_PER_WEAPON)
			{
				picked.add(spell);
			}
		}
		// The strongest spell of the element the monster is weak to (weakness bonuses can beat higher base hits)
		Monster.Weakness weakness = ctx.getMonster().getWeakness();
		String weakElement = weakness == null ? null : weakness.getElement();
		for (Spell spell : usable)
		{
			if (spell.getElement() != null && (weakElement == null || spell.getElement().equals(weakElement)))
			{
				if (!picked.contains(spell))
				{
					picked.add(spell);
				}
				break;
			}
		}
		if (ctx.areaTargets() > 1)
		{
			// With several monsters stacked, a burst can beat a stronger single-target spell
			for (Spell spell : usable)
			{
				if (spell.isAoe())
				{
					if (!picked.contains(spell))
					{
						picked.add(spell);
					}
					break;
				}
			}
		}
		return picked;
	}

	private double scoreOrInvalid(Loadout loadout, DpsResult dps, CombatContext ctx)
	{
		if (dps.getDps() <= 0)
		{
			return Double.NEGATIVE_INFINITY;
		}
		if (!wearsOwnedProtection(loadout))
		{
			// Never recommend a setup that drops protective gear the player owns
			return Double.NEGATIVE_INFINITY;
		}
		double score = score(loadout, dps, ctx);
		if (!rules.allowsWeapon(loadout))
		{
			// Keep it rankable, but below anything that can actually hurt the monster
			score -= CANNOT_DAMAGE_PENALTY;
		}
		return score;
	}

	/**
	 * @return false if the monster requires protective gear in a slot, the player owns some, and the loadout lacks it
	 */
	private boolean wearsOwnedProtection(Loadout loadout)
	{
		for (MonsterRules.SlotRequirement requirement : rules.getSlotRequirements())
		{
			if (ownsMatching(requirement) && !requirement.matches(loadout.stats(requirement.getSlot())))
			{
				return false;
			}
		}
		return true;
	}

	private boolean ownsMatching(MonsterRules.SlotRequirement requirement)
	{
		for (OwnedItem item : candidates.get(requirement.getSlot()))
		{
			if (requirement.matches(item.getStats()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Puts owned protective gear into its slot.
	 *
	 * @return false if the weapon makes that impossible (a two-handed weapon when a shield is required)
	 */
	private boolean applyProtection(Loadout loadout)
	{
		EquipmentItem weapon = loadout.weapon();
		for (MonsterRules.SlotRequirement requirement : rules.getSlotRequirements())
		{
			if (!ownsMatching(requirement) || requirement.matches(loadout.stats(requirement.getSlot())))
			{
				continue;
			}
			if (requirement.getSlot() == GearSlot.SHIELD && weapon != null && weapon.isTwoHanded())
			{
				return false;
			}
			for (OwnedItem item : candidates.get(requirement.getSlot()))
			{
				if (requirement.matches(item.getStats()))
				{
					loadout.set(requirement.getSlot(), item);
					break;
				}
			}
		}
		return true;
	}

	public static int defenceScore(Loadout loadout, Monster monster)
	{
		List<String> styles = monster.getStyle();
		int total = 0;
		int count = 0;
		for (String style : styles)
		{
			String s = style.toLowerCase();
			if (s.contains("magic"))
			{
				total += loadout.total(i -> i.getDefensive().getMagic());
			}
			else if (s.contains("ranged"))
			{
				total += loadout.total(i -> i.getDefensive().getRanged());
			}
			else if (s.contains("stab"))
			{
				total += loadout.total(i -> i.getDefensive().getStab());
			}
			else if (s.contains("slash"))
			{
				total += loadout.total(i -> i.getDefensive().getSlash());
			}
			else if (s.contains("crush"))
			{
				total += loadout.total(i -> i.getDefensive().getCrush());
			}
			else
			{
				continue;
			}
			count++;
		}
		if (count == 0)
		{
			return loadout.total(i -> i.getDefensive().total()) / 5;
		}
		return total / count;
	}

	private OwnedItem locked(GearSlot slot)
	{
		if (input.getLockedItemIds().isEmpty())
		{
			return null;
		}
		for (OwnedItem item : candidates.get(slot))
		{
			if (input.getLockedItemIds().contains(item.getItemId()) || input.getLockedItemIds().contains(item.getStats().getId()))
			{
				return item;
			}
		}
		return null;
	}

	/**
	 * Evaluates what the player is wearing right now, in whichever style their weapon is best at.
	 */
	private StyleResult evaluateCurrent()
	{
		Map<Integer, Integer> equipment = input.getAccount().getEquipment();
		if (equipment == null || equipment.isEmpty())
		{
			return null;
		}
		Loadout loadout = new Loadout();
		for (Map.Entry<Integer, Integer> entry : equipment.entrySet())
		{
			GearSlot slot = GearSlot.fromInventoryIndex(entry.getKey());
			OwnedItem item = slot == null ? null : repository.resolve(entry.getValue());
			if (item != null)
			{
				loadout.set(slot, item);
			}
		}

		StyleResult best = null;
		for (CombatStyle style : CombatStyle.values())
		{
			CombatContext ctx = input.getContexts().get(style);
			if (ctx == null)
			{
				continue;
			}
			Loadout copy = new Loadout(loadout);
			if (copy.isBlowpipe())
			{
				chooseAmmo(copy, ctx);
			}
			DpsResult dps = evaluate(copy, style, ctx);
			if (dps.getDps() <= 0)
			{
				continue;
			}
			double score = score(copy, dps, ctx);
			if (best == null || score > best.getScore())
			{
				best = new StyleResult(style, copy, dps, ctx, score, defenceScore(copy, ctx.getMonster()),
					Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), rules.finishingSeconds(copy),
					Consumables.cost(copy, dps, prices), List.of(), prices);
			}
		}
		return best;
	}
}
