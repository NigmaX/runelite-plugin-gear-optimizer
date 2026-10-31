package com.gearoptimizer.calc;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.GameDataRepository;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.ItemRequirements;
import com.gearoptimizer.data.OwnedItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Finds single-item swaps, from items the player does not own, that raise the score most.
 */
public final class UpgradeFinder
{
	private static final int PER_SLOT_LIMIT = 15;
	private static final Pattern SKIP_VERSION = Pattern.compile(
		"locked|broken|emir's arena|soul wars|deadman|last man standing|inactive|uncharged|empty|damaged|0$|\\(nz\\)|beta|historical",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern SKIP_NAME = Pattern.compile(
		"\\(deadman|\\(bh\\)|\\(nz\\)|\\(beta\\)|corrupted|\\(c\\)|\\(t\\)|\\(b\\)|halloween|\\(cr\\)|\\(last man standing|\\(league",
		Pattern.CASE_INSENSITIVE);

	@Data
	@AllArgsConstructor
	public static class Upgrade
	{
		private EquipmentItem item;
		private CombatStyle style;
		private double dpsGain;
		private double newDps;
		/** Filled in on the client thread; 0 when untradeable or unknown. */
		private long price;
	}

	/** The overall best upgrades, plus every better item for each slot of each style's setup. */
	@lombok.Value
	public static class Upgrades
	{
		public static final Upgrades NONE = new Upgrades(List.of(), Map.of());

		List<Upgrade> top;
		/** Per style and slot, every better item sorted by DPS gain. */
		Map<CombatStyle, Map<GearSlot, List<Upgrade>>> perSlot;

		public List<Upgrade> forSlot(CombatStyle style, GearSlot slot)
		{
			Map<GearSlot, List<Upgrade>> slots = perSlot.get(style);
			List<Upgrade> list = slots == null ? null : slots.get(slot);
			return list == null ? List.of() : list;
		}

		/** @return every upgrade, for price lookups */
		public List<Upgrade> all()
		{
			List<Upgrade> all = new ArrayList<>(top);
			for (Map<GearSlot, List<Upgrade>> slots : perSlot.values())
			{
				for (List<Upgrade> list : slots.values())
				{
					all.addAll(list);
				}
			}
			return all;
		}
	}

	private UpgradeFinder()
	{
	}

	public static Upgrades find(Optimizer optimizer, Optimizer.Result result, GameDataRepository repository,
		AccountSnapshot account, int limit)
	{
		Map<CombatStyle, Map<GearSlot, Map<String, Upgrade>>> bySlotAndName = new java.util.EnumMap<>(CombatStyle.class);
		Set<Integer> owned = new HashSet<>();
		for (List<OwnedItem> items : optimizer.getCandidates().values())
		{
			for (OwnedItem item : items)
			{
				owned.add(item.getStats().getId());
			}
		}

		// Which items could be bought and worn doesn't depend on the style: filter once, not once per style
		List<EquipmentItem> buyable = new ArrayList<>();
		for (EquipmentItem item : repository.getEquipment())
		{
			if (!owned.contains(item.getId()) && !skip(item) && ItemRequirements.missingRequirement(item, account.getLevels()) == null)
			{
				buyable.add(item);
			}
		}

		Map<String, Upgrade> bestByName = new HashMap<>();
		for (StyleResult style : result.getStyles().values())
		{
			double baseDps = style.effectiveDps();
			int hitpoints = style.getContext().getMonster().getHitpoints();
			for (EquipmentItem item : buyable)
			{
				MonsterRules.SlotRequirement required = result.getRules().requirementFor(item.getGearSlot());
				if (required != null && required.matches(style.getLoadout().stats(item.getGearSlot())) && !required.matches(item))
				{
					continue;
				}
				MonsterRules.SlotRequirement shieldRule = result.getRules().requirementFor(GearSlot.SHIELD);
				if (item.isTwoHanded() && shieldRule != null && shieldRule.matches(style.getLoadout().stats(GearSlot.SHIELD)))
				{
					// A two-handed weapon would drop the required shield
					continue;
				}
				Loadout swapped = swap(style, item);
				if (swapped == null)
				{
					continue;
				}
				DpsResult dps = Optimizer.evaluate(swapped, style.getStyle(), style.getContext());
				if (!result.getRules().allowsWeapon(swapped))
				{
					continue;
				}
				double newDps = StyleResult.effectiveDps(dps.getDps(), hitpoints, result.getRules().finishingSeconds(swapped));
				double gain = newDps - baseDps;
				if (gain < 0.01)
				{
					continue;
				}
				Upgrade upgrade = new Upgrade(item, style.getStyle(), gain, newDps, 0);
				Upgrade existing = bestByName.get(item.getName());
				if (existing == null || gain > existing.getDpsGain())
				{
					bestByName.put(item.getName(), upgrade);
				}
				Map<String, Upgrade> slotItems = bySlotAndName
					.computeIfAbsent(style.getStyle(), k -> new java.util.EnumMap<>(GearSlot.class))
					.computeIfAbsent(item.getGearSlot(), k -> new HashMap<>());
				Upgrade sameName = slotItems.get(item.getName());
				if (sameName == null || gain > sameName.getDpsGain())
				{
					slotItems.put(item.getName(), upgrade);
				}
			}
		}

		List<Upgrade> upgrades = new ArrayList<>(bestByName.values());
		upgrades.sort(Comparator.comparingDouble(Upgrade::getDpsGain).reversed());
		List<Upgrade> top = upgrades.size() > limit ? new ArrayList<>(upgrades.subList(0, limit)) : upgrades;

		Map<CombatStyle, Map<GearSlot, List<Upgrade>>> perSlot = new java.util.EnumMap<>(CombatStyle.class);
		for (Map.Entry<CombatStyle, Map<GearSlot, Map<String, Upgrade>>> style : bySlotAndName.entrySet())
		{
			Map<GearSlot, List<Upgrade>> slots = new java.util.EnumMap<>(GearSlot.class);
			for (Map.Entry<GearSlot, Map<String, Upgrade>> slot : style.getValue().entrySet())
			{
				List<Upgrade> list = new ArrayList<>(slot.getValue().values());
				list.sort(Comparator.comparingDouble(Upgrade::getDpsGain).reversed());
				slots.put(slot.getKey(), list.size() > PER_SLOT_LIMIT ? new ArrayList<>(list.subList(0, PER_SLOT_LIMIT)) : list);
			}
			perSlot.put(style.getKey(), slots);
		}
		return new Upgrades(top, perSlot);
	}

	private static Loadout swap(StyleResult style, EquipmentItem item)
	{
		GearSlot slot = item.getGearSlot();
		Loadout loadout = new Loadout(style.getLoadout());
		OwnedItem candidate = new OwnedItem(item, item.getId(), false);
		if (slot == GearSlot.WEAPON)
		{
			if (WeaponStyles.stancesFor(item, style.getStyle()).isEmpty())
			{
				return null;
			}
			if (style.getStyle() == CombatStyle.RANGED && GearEffects.usesAmmoSlot(item)
				&& !AmmoRules.compatible(item, loadout.stats(GearSlot.AMMO)))
			{
				return null;
			}
			loadout.set(GearSlot.WEAPON, candidate);
			if (item.isTwoHanded())
			{
				loadout.set(GearSlot.SHIELD, null);
			}
			return loadout;
		}
		if (slot == GearSlot.SHIELD && loadout.weapon() != null && loadout.weapon().isTwoHanded())
		{
			return null;
		}
		if (slot == GearSlot.AMMO && style.getStyle() == CombatStyle.RANGED && GearEffects.usesAmmoSlot(loadout.weapon())
			&& !AmmoRules.compatible(loadout.weapon(), item))
		{
			return null;
		}
		loadout.set(slot, candidate);
		return loadout;
	}

	/**
	 * Gear that only exists or only works inside a minigame, raid or temporary game mode
	 * (Gauntlet crystal gear, Dawnbringer, Leagues "echo" items, Castle Wars, Barbarian Assault...).
	 */
	private static final Pattern MINIGAME_ONLY = Pattern.compile(
		"\\((basic|attuned|perfected)\\)$|^dawnbringer|^echo |^wilderness champion amulet|\\(historical\\)"
			+ "|^castle ?wars|\\(castle wars\\)|\\(soul wars\\)|^healer hat|^penance (skirt|gloves)|naval shirt|\\(trouble brewing\\)",
		Pattern.CASE_INSENSITIVE);

	/**
	 * @return true for items that only work inside a minigame or raid; these are never recommended, even if owned
	 */
	public static boolean isMinigameOnly(EquipmentItem item)
	{
		return MINIGAME_ONLY.matcher(item.getName()).find();
	}

	static boolean skip(EquipmentItem item)
	{
		String version = item.getVersion() == null ? "" : item.getVersion();
		return SKIP_VERSION.matcher(version).find() || SKIP_NAME.matcher(item.getName()).find() || isMinigameOnly(item);
	}
}
