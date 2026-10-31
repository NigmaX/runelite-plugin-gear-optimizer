package com.gearoptimizer.calc;

import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.OwnedItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import net.runelite.api.gameval.ItemID;

/**
 * What a setup costs to run (ammo, runes, charges) and cheaper ammo/spell options for it.
 */
public final class Consumables
{
	/** A budget option must keep at least this share of the best option's DPS. */
	public static final double BUDGET_DPS_SHARE = 0.9;

	private static final Map<String, Integer> RUNE_IDS = Map.of(
		"Air", ItemID.AIRRUNE, "Water", ItemID.WATERRUNE, "Earth", ItemID.EARTHRUNE, "Fire", ItemID.FIRERUNE,
		"Mind", ItemID.MINDRUNE, "Chaos", ItemID.CHAOSRUNE, "Death", ItemID.DEATHRUNE, "Blood", ItemID.BLOODRUNE,
		"Wrath", ItemID.WRATHRUNE, "Soul", ItemID.SOULRUNE);

	/**
	 * @return the runes the loadout's spell uses per cast that its gear doesn't provide (name -> count), in spell order
	 */
	public static Map<String, Integer> runesToCarry(Loadout loadout)
	{
		Map<String, Integer> runes = new java.util.LinkedHashMap<>();
		Spell spell = loadout.getSpell();
		if (spell != null)
		{
			spell.getRuneCosts().forEach((rune, count) ->
			{
				if (!MonsterRules.providedByGear(rune, loadout))
				{
					runes.put(rune, count);
				}
			});
		}
		return runes;
	}

	/**
	 * @return the runes the loadout's spell uses that its gear provides for free, such as water from a Kodai wand
	 */
	public static List<String> runesFromGear(Loadout loadout)
	{
		List<String> runes = new ArrayList<>();
		Spell spell = loadout.getSpell();
		if (spell != null)
		{
			for (String rune : spell.getRunes())
			{
				if (MonsterRules.providedByGear(rune, loadout))
				{
					runes.add(rune);
				}
			}
		}
		return runes;
	}

	/**
	 * @return the item id of a rune by name ("Water"), or -1
	 */
	public static int runeItemId(String rune)
	{
		return RUNE_IDS.getOrDefault(rune, -1);
	}

	/** Items whose prices the cost model may need besides the player's own. */
	public static final Set<Integer> PRICED_SUPPLIES = Set.of(ItemID.AIRRUNE, ItemID.WATERRUNE, ItemID.EARTHRUNE, ItemID.FIRERUNE,
		ItemID.MINDRUNE, ItemID.CHAOSRUNE, ItemID.DEATHRUNE, ItemID.BLOODRUNE, ItemID.WRATHRUNE, ItemID.SOULRUNE,
		ItemID.SNAKEBOSS_SCALE, ItemID.VIAL_BLOOD, ItemID.MCANNONBALL, ItemID.GRANITE_CANNONBALL);

	@Value
	public static class Cost
	{
		public static final Cost FREE = new Cost(0, 0, List.of());

		double perAttack;
		double perHour;
		List<String> breakdown;

		public double perThousandDamage(DpsResult dps)
		{
			return dps.getAverageHit() <= 0 ? 0 : perAttack / dps.getAverageHit() * 1000;
		}
	}

	@Value
	public static class Option
	{
		String label;
		Loadout loadout;
		DpsResult dps;
		Cost cost;
		boolean current;
	}

	private Consumables()
	{
	}

	public static Cost cost(Loadout loadout, DpsResult dps, Map<Integer, Integer> prices)
	{
		if (dps.getSpeedTicks() <= 0 || prices == null)
		{
			return Cost.FREE;
		}
		List<String> parts = new ArrayList<>();
		double perAttack = 0;
		EquipmentItem weapon = loadout.weapon();
		String weaponName = GearEffects.lowerName(weapon);
		Stance stance = loadout.getStance();
		CombatStyle style = stance == null ? CombatStyle.MELEE : stance.getStyle();

		if (style == CombatStyle.RANGED)
		{
			double loss = ammoLoss(loadout);
			if (loadout.isBlowpipe())
			{
				OwnedItem dart = loadout.getBlowpipeDart();
				if (dart != null)
				{
					perAttack += add(parts, dart.getStats().getName(), loss, price(prices, dart));
				}
				perAttack += add(parts, "Zulrah's scales", 2.0 / 3, prices.getOrDefault(ItemID.SNAKEBOSS_SCALE, 0));
			}
			else if (GearEffects.usesAmmoSlot(weapon) && loadout.get(GearSlot.AMMO) != null)
			{
				int shots = weaponName.startsWith("dark bow") ? 2 : 1;
				perAttack += add(parts, loadout.stats(GearSlot.AMMO).getName(), loss * shots, price(prices, loadout.get(GearSlot.AMMO)));
			}
			else if (weapon != null && ("Thrown".equals(weapon.getCategory()) || "Chinchompas".equals(weapon.getCategory())))
			{
				double thrownLoss = "Chinchompas".equals(weapon.getCategory()) ? 1 : loss;
				perAttack += add(parts, weapon.getName(), thrownLoss, price(prices, loadout.get(GearSlot.WEAPON)));
			}
		}
		else if (style == CombatStyle.MAGIC)
		{
			Spell spell = loadout.getSpell();
			if (spell != null)
			{
				for (Map.Entry<String, Integer> rune : spell.getRuneCosts().entrySet())
				{
					if (!MonsterRules.providedByGear(rune.getKey(), loadout))
					{
						perAttack += add(parts, rune.getKey() + " rune", rune.getValue(), prices.getOrDefault(RUNE_IDS.get(rune.getKey()), 0));
					}
				}
			}
			else
			{
				perAttack += chargeCost(weaponName, prices, parts);
			}
		}
		else if (weaponName.contains("scythe of vitur"))
		{
			perAttack += add(parts, "Blood rune", 3, prices.getOrDefault(ItemID.BLOODRUNE, 0));
			perAttack += add(parts, "Vial of blood", 0.01, prices.getOrDefault(ItemID.VIAL_BLOOD, 0));
		}

		double attacksPerHour = 3600 / (dps.getSpeedTicks() * DpsResult.SECONDS_PER_TICK);
		Cannon.Fire cannon = dps.getCannon();
		if (cannon != null)
		{
			// Counted per player attack so the cost per hour adds up
			perAttack += add(parts, "Cannonballs", cannon.getBallsPerHour() / attacksPerHour, prices.getOrDefault(cannon.getBallItemId(), 0));
		}
		return new Cost(perAttack, perAttack * attacksPerHour, parts);
	}

	private static double chargeCost(String weaponName, Map<Integer, Integer> prices, List<String> parts)
	{
		if (weaponName.startsWith("trident of the seas"))
		{
			return add(parts, "Death rune", 1, prices.getOrDefault(ItemID.DEATHRUNE, 0))
				+ add(parts, "Chaos rune", 1, prices.getOrDefault(ItemID.CHAOSRUNE, 0))
				+ add(parts, "Fire rune", 5, prices.getOrDefault(ItemID.FIRERUNE, 0))
				+ add(parts, "Coins", 10, 1);
		}
		if (weaponName.startsWith("trident of the swamp"))
		{
			return add(parts, "Death rune", 1, prices.getOrDefault(ItemID.DEATHRUNE, 0))
				+ add(parts, "Chaos rune", 1, prices.getOrDefault(ItemID.CHAOSRUNE, 0))
				+ add(parts, "Fire rune", 5, prices.getOrDefault(ItemID.FIRERUNE, 0))
				+ add(parts, "Zulrah's scales", 1, prices.getOrDefault(ItemID.SNAKEBOSS_SCALE, 0));
		}
		if (weaponName.contains("sanguinesti staff"))
		{
			return add(parts, "Blood rune", 3, prices.getOrDefault(ItemID.BLOODRUNE, 0));
		}
		if (weaponName.startsWith("tumeken's shadow"))
		{
			return add(parts, "Soul rune", 5, prices.getOrDefault(ItemID.SOULRUNE, 0))
				+ add(parts, "Chaos rune", 2, prices.getOrDefault(ItemID.CHAOSRUNE, 0));
		}
		return 0;
	}

	/**
	 * @return share of ammunition lost per shot, depending on the ammo-saving cape
	 */
	static double ammoLoss(Loadout loadout)
	{
		String cape = GearEffects.lowerName(loadout.stats(GearSlot.CAPE));
		if (cape.contains("dizana's quiver") || cape.contains("assembler"))
		{
			return 0.2;
		}
		if (cape.startsWith("ava's accumulator") || cape.startsWith("accumulator max cape"))
		{
			return 0.28;
		}
		if (cape.startsWith("ava's attractor"))
		{
			return 0.4;
		}
		return 1;
	}

	private static double add(List<String> parts, String name, double perAttack, int price)
	{
		double cost = perAttack * price;
		if (cost > 0)
		{
			parts.add(String.format("%s %.2f/attack", name, perAttack));
		}
		return cost;
	}

	private static int price(Map<Integer, Integer> prices, OwnedItem item)
	{
		Integer price = prices.get(item.getItemId());
		if (price == null || price == 0)
		{
			price = prices.getOrDefault(item.getStats().getId(), 0);
		}
		return price;
	}

	/**
	 * Lists the owned ammo, darts or spells that could replace the setup's current one, each with its DPS and cost.
	 */
	public static List<Option> options(StyleResult result, Collection<OwnedItem> owned, Map<Integer, Integer> prices)
	{
		List<Option> options = new ArrayList<>();
		Loadout base = result.getLoadout();
		CombatContext ctx = result.getContext();
		EquipmentItem weapon = base.weapon();

		if (result.getStyle() == CombatStyle.RANGED && base.isBlowpipe())
		{
			Set<Integer> seen = new HashSet<>();
			for (OwnedItem item : owned)
			{
				if (AmmoRules.blowpipeAccepts(weapon, item.getStats()) && seen.add(item.getStats().getId()))
				{
					Loadout loadout = new Loadout(base);
					loadout.setBlowpipeDart(item);
					options.add(option(item.getStats().getName(), loadout, result, prices, base.getBlowpipeDart() != null
						&& base.getBlowpipeDart().getStats().getId() == item.getStats().getId()));
				}
			}
		}
		else if (result.getStyle() == CombatStyle.RANGED && GearEffects.usesAmmoSlot(weapon))
		{
			Set<Integer> seen = new HashSet<>();
			for (OwnedItem item : owned)
			{
				if (item.getStats().getGearSlot() == GearSlot.AMMO && AmmoRules.compatible(weapon, item.getStats()) && seen.add(item.getStats().getId()))
				{
					Loadout loadout = new Loadout(base);
					loadout.set(GearSlot.AMMO, item);
					options.add(option(item.getStats().getName(), loadout, result, prices,
						base.get(GearSlot.AMMO) != null && base.stats(GearSlot.AMMO).getId() == item.getStats().getId()));
				}
			}
		}
		else if (result.getStyle() == CombatStyle.MAGIC && WeaponStyles.canAutocast(weapon))
		{
			for (Spell spell : Spell.values())
			{
				if (spell.castable(base, ctx))
				{
					Loadout loadout = new Loadout(base);
					loadout.setSpell(spell);
					loadout.setStance(Stance.AUTOCAST);
					options.add(option(spell.getDisplayName(), loadout, result, prices, spell == base.getSpell()));
				}
			}
		}
		options.removeIf(o -> o.getDps().getDps() <= 0);
		options.sort(Comparator.comparingDouble((Option o) -> o.getDps().getDps()).reversed());
		return options;
	}

	private static Option option(String label, Loadout loadout, StyleResult result, Map<Integer, Integer> prices, boolean current)
	{
		DpsResult dps = result.getStyle() == CombatStyle.MAGIC
			? DpsCalculator.calculate(loadout, result.getContext())
			: Optimizer.evaluate(loadout, result.getStyle(), result.getContext());
		return new Option(label, loadout, dps, cost(loadout, dps, prices), current);
	}

	/**
	 * @return the cheapest option per hour that keeps at least {@link #BUDGET_DPS_SHARE} of the best DPS, or null
	 */
	public static Option budgetPick(List<Option> options)
	{
		if (options.isEmpty())
		{
			return null;
		}
		double best = options.get(0).getDps().getDps();
		Option pick = null;
		for (Option option : options)
		{
			if (option.getDps().getDps() >= best * BUDGET_DPS_SHARE
				&& (pick == null || option.getCost().getPerHour() < pick.getCost().getPerHour()))
			{
				pick = option;
			}
		}
		return pick;
	}
}
