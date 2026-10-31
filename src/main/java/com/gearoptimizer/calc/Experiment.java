package com.gearoptimizer.calc;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.OwnedItem;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Re-evaluates a loadout the player edited by hand, keeping the same assumptions as the recommendation.
 */
public final class Experiment
{
	private Experiment()
	{
	}

	/**
	 * @return false if the weapon fires ammo and cannot fire this item
	 */
	public static boolean canFire(EquipmentItem weapon, EquipmentItem ammo)
	{
		return !GearEffects.usesAmmoSlot(weapon) || !GearEffects.isProjectile(ammo) || AmmoRules.compatible(weapon, ammo);
	}

	/**
	 * @return true for variants not worth listing (locked, broken, minigame and cosmetic copies)
	 */
	public static boolean isJunkVariant(EquipmentItem item)
	{
		return UpgradeFinder.skip(item);
	}

	/**
	 * Puts an item in a slot, keeping the loadout wearable: a two-handed weapon removes the shield,
	 * and ammo the new weapon cannot fire is swapped for the best ammo it can.
	 *
	 * @param ammoOptions ammo to choose from when the current ammo no longer fits
	 * @return a note about anything else that changed, or null
	 */
	public static String apply(Loadout loadout, GearSlot slot, OwnedItem item, List<OwnedItem> ammoOptions,
		OwnedItem preferredAmmo, CombatStyle style, CombatContext ctx)
	{
		EquipmentItem weapon = loadout.weapon();
		if (slot == GearSlot.SHIELD && item != null && weapon != null && weapon.isTwoHanded())
		{
			loadout.set(GearSlot.WEAPON, null);
			loadout.set(GearSlot.SHIELD, item);
			return "Removed the two-handed " + weapon.getName();
		}
		loadout.set(slot, item);
		if (slot != GearSlot.WEAPON || item == null)
		{
			return null;
		}

		String note = null;
		if (item.getStats().isTwoHanded() && loadout.get(GearSlot.SHIELD) != null)
		{
			note = "Removed the shield (two-handed weapon)";
			loadout.set(GearSlot.SHIELD, null);
		}
		if (GearEffects.usesAmmoSlot(item.getStats()) && !AmmoRules.compatible(item.getStats(), loadout.stats(GearSlot.AMMO)))
		{
			OwnedItem best = null;
			if (preferredAmmo != null && AmmoRules.compatible(item.getStats(), preferredAmmo.getStats()))
			{
				// Go back to the ammo the recommendation used, e.g. when restoring the recommended weapon
				best = preferredAmmo;
			}
			else
			{
				double bestDps = -1;
				for (OwnedItem ammo : ammoOptions)
				{
					if (!AmmoRules.compatible(item.getStats(), ammo.getStats()))
					{
						continue;
					}
					Loadout trial = new Loadout(loadout);
					trial.set(GearSlot.AMMO, ammo);
					double dps = Optimizer.evaluate(trial, style, ctx).getDps();
					if (dps > bestDps)
					{
						bestDps = dps;
						best = ammo;
					}
				}
			}
			loadout.set(GearSlot.AMMO, best);
			String ammoNote = best == null ? "No ammo you own fits this weapon" : "Switched ammo to " + best.getStats().getName();
			note = note == null ? ammoNote : note + "; " + ammoNote;
		}
		return note;
	}

	/**
	 * @return true if both loadouts wear the same items (and blowpipe darts)
	 */
	public static boolean sameItems(Loadout a, Loadout b)
	{
		for (GearSlot slot : GearSlot.values())
		{
			EquipmentItem x = a.stats(slot);
			EquipmentItem y = b.stats(slot);
			if ((x == null) != (y == null) || (x != null && x.getId() != y.getId()))
			{
				return false;
			}
		}
		OwnedItem dartA = a.getBlowpipeDart();
		OwnedItem dartB = b.getBlowpipeDart();
		return (dartA == null) == (dartB == null) && (dartA == null || dartA.getStats().getId() == dartB.getStats().getId());
	}

	/**
	 * @return the edited loadout scored like a recommendation, with its own warnings and supplies
	 */
	public static StyleResult evaluate(StyleResult base, Loadout edited, MonsterRules rules, AccountSnapshot account)
	{
		Loadout loadout = new Loadout(edited);
		CombatContext ctx = base.getContext();
		DpsResult dps;
		Spell chosen = loadout.getSpell();
		if (chosen != null && WeaponStyles.canAutocast(loadout.weapon()) && chosen.usableWith(loadout, ctx.getMonster()))
		{
			// Keep the spell the player picked instead of re-choosing the strongest
			loadout.setStance(Stance.AUTOCAST);
			dps = DpsCalculator.calculate(loadout, ctx);
		}
		else
		{
			dps = Optimizer.evaluate(loadout, base.getStyle(), ctx);
		}

		List<String> warnings = new ArrayList<>();
		for (MonsterRules.SlotRequirement requirement : rules.getSlotRequirements())
		{
			if (!requirement.matches(loadout.stats(requirement.getSlot())))
			{
				warnings.add("UNSAFE: this setup has no " + requirement.getLabel() + " ("
					+ requirement.getSlot().getDisplayName().toLowerCase() + " slot).");
			}
		}
		if (dps.getDps() > 0 && !rules.allowsWeapon(loadout))
		{
			warnings.add("This weapon can't damage this monster.");
			dps = DpsResult.NONE;
		}
		else if (dps.getDps() <= 0)
		{
			warnings.add("This weapon can't attack with " + base.getStyle().getDisplayName().toLowerCase()
				+ ". Pick a " + base.getStyle().getDisplayName().toLowerCase() + " weapon.");
		}
		EquipmentItem weapon = loadout.weapon();
		if (base.getStyle() == CombatStyle.RANGED && GearEffects.usesAmmoSlot(weapon) && loadout.get(GearSlot.AMMO) == null)
		{
			warnings.add("No ammo equipped.");
		}
		for (OwnedItem item : loadout.items())
		{
			if (item.isNeedsCharges())
			{
				warnings.add(item.getStats().getName() + " needs charging");
			}
		}

		double finish = rules.finishingSeconds(loadout);
		double score = StyleResult.effectiveDps(dps.getDps(), ctx.getMonster().getHitpoints(), finish);
		return new StyleResult(base.getStyle(), loadout, dps, ctx, score, Optimizer.defenceScore(loadout, ctx.getMonster()),
			rules.checkInventory(loadout, account, ctx), warnings, base.getSpecOptions(), finish,
			Consumables.cost(loadout, dps, base.getPrices()), base.getConsumableOptions(), base.getPrices());
	}

	/** One choice for a slot, scored against the setup it would go into. */
	@lombok.Value
	public static class SlotOption
	{
		OwnedItem item;
		boolean owned;
		boolean inUse;
		double dps;
		double change;
		/** Why the swap is risky or changes other slots, or null. */
		String note;
		boolean unsafe;
	}

	private static final int NOT_OWNED_LIMIT = 40;

	/**
	 * Scores every owned item for the slot plus the strongest items the player doesn't own,
	 * as if each were swapped into the shown setup.
	 */
	public static List<SlotOption> slotOptions(StyleResult shown, GearSlot slot, List<OwnedItem> owned,
		List<EquipmentItem> everything, MonsterRules rules, OwnedItem preferredAmmo)
	{
		List<OwnedItem> ammo = new ArrayList<>();
		java.util.Map<Integer, OwnedItem> ownedBySlot = new java.util.LinkedHashMap<>();
		for (OwnedItem item : owned)
		{
			if (item.getStats().getGearSlot() == GearSlot.AMMO)
			{
				ammo.add(item);
			}
			if (item.getStats().getGearSlot() == slot && !UpgradeFinder.isMinigameOnly(item.getStats()))
			{
				ownedBySlot.putIfAbsent(item.getStats().getId(), item);
			}
		}

		OwnedItem current = shown.getLoadout().get(slot);
		double baseDps = shown.effectiveDps();
		// Owned copies with identical stats (glory charges, colour variants) are listed once, preferring the one in use
		java.util.Map<String, OwnedItem> ownedByStats = new java.util.LinkedHashMap<>();
		for (OwnedItem item : ownedBySlot.values())
		{
			String signature = statSignature(item.getStats());
			boolean inUse = current != null && current.getStats().getId() == item.getStats().getId();
			if (inUse || !ownedByStats.containsKey(signature))
			{
				ownedByStats.put(signature, item);
			}
		}
		List<SlotOption> ownedOptions = new ArrayList<>();
		for (OwnedItem item : ownedByStats.values())
		{
			ownedOptions.add(score(shown, slot, item, true, current, baseDps, ammo, rules, preferredAmmo));
		}

		List<SlotOption> others = new ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		// Cosmetic variants (colours, ornament kits) share stats; list each stat line once, preferring owned copies
		java.util.Set<String> seenStats = new java.util.HashSet<>();
		seenStats.addAll(ownedByStats.keySet());
		for (EquipmentItem item : everything)
		{
			if (item.getGearSlot() != slot || ownedBySlot.containsKey(item.getId()) || isJunkVariant(item) || !seen.add(item.getDisplayName())
				|| !seenStats.add(statSignature(item)))
			{
				continue;
			}
			SlotOption option = score(shown, slot, new OwnedItem(item, item.getId(), false), false, current, baseDps, ammo, rules, preferredAmmo);
			if (option.getDps() > 0)
			{
				others.add(option);
			}
		}
		others.sort(java.util.Comparator.comparingDouble(SlotOption::getDps).reversed());

		List<SlotOption> all = new ArrayList<>(ownedOptions);
		all.addAll(others.size() > NOT_OWNED_LIMIT ? others.subList(0, NOT_OWNED_LIMIT) : others);
		all.sort(java.util.Comparator.comparingDouble(SlotOption::getDps).reversed());
		return all;
	}

	/**
	 * Every dart type the shown blowpipe can be loaded with, owned or not, scored as if loaded, best first. One entry
	 * per dart type: the copy in use, else an owned copy, else the unpoisoned version.
	 */
	public static List<SlotOption> dartOptions(StyleResult shown, List<OwnedItem> owned, List<EquipmentItem> everything)
	{
		Loadout base = shown.getLoadout();
		if (!base.isBlowpipe())
		{
			return new ArrayList<>();
		}
		OwnedItem current = base.getBlowpipeDart();
		java.util.Map<String, OwnedItem> byType = new java.util.LinkedHashMap<>();
		if (current != null)
		{
			byType.put(GearEffects.lowerName(current.getStats()), current);
		}
		java.util.Set<String> ownedTypes = new java.util.HashSet<>();
		for (OwnedItem item : owned)
		{
			if (AmmoRules.blowpipeAccepts(base.weapon(), item.getStats()))
			{
				byType.putIfAbsent(GearEffects.lowerName(item.getStats()), item);
				ownedTypes.add(GearEffects.lowerName(item.getStats()));
			}
		}
		for (EquipmentItem item : everything)
		{
			String version = item.getVersion() == null ? "" : item.getVersion();
			if ((version.isEmpty() || version.equals("Unpoisoned")) && AmmoRules.blowpipeAccepts(base.weapon(), item))
			{
				byType.putIfAbsent(GearEffects.lowerName(item), new OwnedItem(item, item.getId(), false));
			}
		}

		CombatContext ctx = shown.getContext();
		double baseDps = shown.effectiveDps();
		List<SlotOption> options = new ArrayList<>();
		for (java.util.Map.Entry<String, OwnedItem> entry : byType.entrySet())
		{
			Loadout loadout = new Loadout(base);
			loadout.setBlowpipeDart(entry.getValue());
			DpsResult dps = Optimizer.evaluate(loadout, shown.getStyle(), ctx);
			double effective = StyleResult.effectiveDps(dps.getDps(), ctx.getMonster().getHitpoints(), shown.getFinishSeconds());
			boolean inUse = current != null && GearEffects.lowerName(current.getStats()).equals(entry.getKey());
			options.add(new SlotOption(entry.getValue(), ownedTypes.contains(entry.getKey()), inUse, effective, effective - baseDps, null, false));
		}
		options.sort(java.util.Comparator.comparingDouble(SlotOption::getDps).reversed());
		return options;
	}

	/**
	 * @return a key that is equal for items with identical combat stats and special effects
	 */
	private static final Pattern VERSION_SUFFIX = Pattern.compile("\\s*\\(.*\\)$");
	private static final Pattern COLOUR_PREFIX = Pattern.compile(
		"^(black|green|red|purple|turquoise|hydra|twisted|tztok|vampyric|tzkal|araxyte|hooded) ");

	static String statSignature(EquipmentItem item)
	{
		EquipmentItem.Attack o = item.getOffensive();
		EquipmentItem.Attack d = item.getDefensive();
		EquipmentItem.Bonuses b = item.getBonuses();
		// The base name keeps items with name-based effects (salve, slayer helm, void...) apart from look-alikes
		String base = COLOUR_PREFIX.matcher(VERSION_SUFFIX.matcher(item.getLowerName()).replaceAll("")).replaceAll("");
		return base + "|" + item.getCategory() + "|" + item.getSpeed() + "|" + item.isTwoHanded() + "|"
			+ o.getStab() + "," + o.getSlash() + "," + o.getCrush() + "," + o.getMagic() + "," + o.getRanged() + "|"
			+ d.getStab() + "," + d.getSlash() + "," + d.getCrush() + "," + d.getMagic() + "," + d.getRanged() + "|"
			+ b.getStr() + "," + b.getRangedStr() + "," + b.getMagicStr() + "," + b.getPrayer();
	}

	private static SlotOption score(StyleResult shown, GearSlot slot, OwnedItem item, boolean owned, OwnedItem current,
		double baseDps, List<OwnedItem> ammo, MonsterRules rules, OwnedItem preferredAmmo)
	{
		Loadout loadout = new Loadout(shown.getLoadout());
		CombatContext ctx = shown.getContext();
		String note = apply(loadout, slot, item, ammo, preferredAmmo, shown.getStyle(), ctx);
		DpsResult dps = Optimizer.evaluate(loadout, shown.getStyle(), ctx);
		double effective = StyleResult.effectiveDps(dps.getDps(), ctx.getMonster().getHitpoints(), rules.finishingSeconds(loadout));

		boolean unsafe = false;
		for (MonsterRules.SlotRequirement requirement : rules.getSlotRequirements())
		{
			if (requirement.matches(shown.getLoadout().stats(requirement.getSlot())) && !requirement.matches(loadout.stats(requirement.getSlot())))
			{
				unsafe = true;
				note = "Unsafe: removes " + requirement.getLabel();
			}
		}
		if (dps.getDps() <= 0)
		{
			note = "Can't attack with " + shown.getStyle().getDisplayName().toLowerCase();
		}
		else if (!rules.allowsWeapon(loadout))
		{
			note = "Can't damage this monster";
			effective = 0;
		}
		if (note == null && ctx.areaTargets() > 1 && loadout.getSpell() != null && loadout.getSpell().isAoe())
		{
			note = loadout.getSpell().getDisplayName() + " hits " + ctx.areaTargets() + " monsters";
		}
		else if (note == null && ctx.areaTargets() > 1 && loadout.weapon() != null && "Chinchompas".equals(loadout.weapon().getCategory()))
		{
			note = "Hits " + ctx.areaTargets() + " monsters";
		}
		boolean inUse = current != null && current.getStats().getId() == item.getStats().getId();
		return new SlotOption(item, owned, inUse, effective, effective - baseDps, note, unsafe);
	}
}
