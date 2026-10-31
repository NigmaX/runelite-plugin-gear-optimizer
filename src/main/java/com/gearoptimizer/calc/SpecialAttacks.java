package com.gearoptimizer.calc;

import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.OwnedItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Value;

/**
 * Special attack weapons, with simplified accuracy/damage modifiers from the wiki.
 */
public final class SpecialAttacks
{
	private enum Kind
	{
		NORMAL,
		CLAWS,
		GUARANTEED
	}

	private static final class Spec
	{
		final String prefix;
		final CombatStyle style;
		final int energy;
		final double accuracy;
		final double damage;
		final int hits;
		final Kind kind;
		final String utility;

		Spec(String prefix, CombatStyle style, int energy, double accuracy, double damage, int hits, Kind kind, String utility)
		{
			this.prefix = prefix;
			this.style = style;
			this.energy = energy;
			this.accuracy = accuracy;
			this.damage = damage;
			this.hits = hits;
			this.kind = kind;
			this.utility = utility;
		}
	}

	@Value
	public static class SpecOption
	{
		OwnedItem weapon;
		int energy;
		double damagePerSpec;
		double damagePer100Energy;
		String utility;
	}

	private static final CombatStyle M = CombatStyle.MELEE;
	private static final CombatStyle R = CombatStyle.RANGED;

	private static final List<Spec> SPECS = List.of(
		new Spec("dragon claws", M, 50, 1, 1, 4, Kind.CLAWS, null),
		new Spec("burning claws", M, 30, 1, 1, 3, Kind.CLAWS, null),
		new Spec("armadyl godsword", M, 50, 2, 1.375, 1, Kind.NORMAL, null),
		new Spec("bandos godsword", M, 50, 2, 1.21, 1, Kind.NORMAL, "Drains defence by damage dealt"),
		new Spec("ancient godsword", M, 50, 2, 1.1, 1, Kind.NORMAL, "Bleeds 25 after 8 ticks"),
		new Spec("saradomin godsword", M, 50, 2, 1.1, 1, Kind.NORMAL, "Heals and restores prayer"),
		new Spec("zamorak godsword", M, 50, 2, 1.1, 1, Kind.NORMAL, "Freezes"),
		new Spec("dragon warhammer", M, 50, 1, 1.5, 1, Kind.NORMAL, "Lowers defence 30% on hit"),
		new Spec("elder maul", M, 50, 1.25, 1, 1, Kind.NORMAL, "Lowers defence 35% on hit"),
		new Spec("voidwaker", M, 50, 1, 1, 1, Kind.GUARANTEED, "Always hits, 50-150% of max"),
		new Spec("dragon dagger", M, 25, 1.15, 1.15, 2, Kind.NORMAL, null),
		new Spec("abyssal dagger", M, 50, 1.25, 0.85, 2, Kind.NORMAL, null),
		new Spec("dragon mace", M, 25, 1.25, 1.5, 1, Kind.NORMAL, null),
		new Spec("dragon longsword", M, 25, 1, 1.25, 1, Kind.NORMAL, null),
		new Spec("osmumten's fang", M, 25, 1.5, 1, 1, Kind.NORMAL, null),
		new Spec("crystal halberd", M, 30, 1, 1.1, 2, Kind.NORMAL, "Second hit on large monsters"),
		new Spec("dragon halberd", M, 30, 1, 1.1, 2, Kind.NORMAL, "Second hit on large monsters"),
		new Spec("granite maul", M, 50, 1, 1, 1, Kind.NORMAL, "Instant extra hit"),
		new Spec("barrelchest anchor", M, 50, 2, 1.1, 1, Kind.NORMAL, "Drains a combat stat"),
		new Spec("arclight", M, 50, 1, 1, 1, Kind.NORMAL, "Lowers attack/strength/defence (more on demons)"),
		new Spec("emberlight", M, 50, 1, 1, 1, Kind.NORMAL, "Lowers attack/strength/defence (more on demons)"),
		new Spec("toxic blowpipe", R, 50, 2, 1.5, 1, Kind.NORMAL, "Heals half the damage"),
		new Spec("dark bow", R, 55, 1, 1.3, 2, Kind.NORMAL, null),
		new Spec("armadyl crossbow", R, 40, 2, 1, 1, Kind.NORMAL, null),
		new Spec("zaryte crossbow", R, 75, 2, 1, 1, Kind.NORMAL, "Guaranteed enchanted bolt effect"),
		new Spec("heavy ballista", R, 65, 1.25, 1.25, 1, Kind.NORMAL, null),
		new Spec("magic shortbow", R, 55, 1, 1, 2, Kind.NORMAL, null),
		new Spec("dragon knife", R, 25, 1, 1, 2, Kind.NORMAL, null)
	);

	private SpecialAttacks()
	{
	}

	/**
	 * Ranks owned spec weapons by damage per 100% special energy, swapped into the style's best loadout.
	 */
	public static List<SpecOption> rank(StyleResult main, List<OwnedItem> ownedWeapons, MonsterRules rules,
		java.util.function.Predicate<Loadout> safe)
	{
		List<SpecOption> options = new ArrayList<>();
		if (main == null || main.getStyle() == CombatStyle.MAGIC)
		{
			return options;
		}
		for (OwnedItem weapon : ownedWeapons)
		{
			Spec spec = specFor(weapon, main.getStyle());
			if (spec == null)
			{
				continue;
			}
			Loadout swapped = new Loadout(main.getLoadout());
			swapped.set(GearSlot.WEAPON, weapon);
			if (weapon.getStats().isTwoHanded())
			{
				swapped.set(GearSlot.SHIELD, null);
			}
			if (!rules.allowsWeapon(swapped) || !safe.test(swapped))
			{
				continue;
			}
			DpsResult best = null;
			for (Stance stance : WeaponStyles.stancesFor(weapon.getStats(), main.getStyle()))
			{
				swapped.setStance(stance);
				DpsResult result = DpsCalculator.calculate(swapped, main.getContext());
				if (best == null || result.getAverageHit() > best.getAverageHit())
				{
					best = result;
				}
			}
			if (best == null || best.getMaxHit() <= 0)
			{
				continue;
			}
			double perSpec = expectedDamage(spec, best, main.getContext().getMonster().getSize());
			options.add(new SpecOption(weapon, spec.energy, perSpec, perSpec * 100.0 / spec.energy, spec.utility));
		}
		options.sort(Comparator.comparingDouble(SpecOption::getDamagePer100Energy).reversed());
		return options.size() > 3 ? new ArrayList<>(options.subList(0, 3)) : options;
	}

	private static Spec specFor(OwnedItem weapon, CombatStyle style)
	{
		String name = weapon.getStats().getName().toLowerCase();
		for (Spec spec : SPECS)
		{
			if (spec.style == style && name.startsWith(spec.prefix))
			{
				return spec;
			}
		}
		return null;
	}

	private static double expectedDamage(Spec spec, DpsResult base, int monsterSize)
	{
		long attackRoll = (long) (base.getAttackRoll() * spec.accuracy);
		double accuracy = DpsCalculator.hitChance(attackRoll, base.getDefenceRoll());
		int max = (int) (base.getMaxHit() * spec.damage);
		switch (spec.kind)
		{
			case GUARANTEED:
				return max;
			case CLAWS:
				// At least one of the rolls lands; a landed sequence averages about 1.5x max hit in total
				double anyHit = 1 - Math.pow(1 - accuracy, spec.hits);
				return anyHit * max * (spec.hits == 4 ? 1.5 : 1.2) * 0.75;
			default:
				int hits = spec.prefix.endsWith("halberd") && monsterSize < 2 ? 1 : spec.hits;
				return hits * accuracy * max / 2.0;
		}
	}
}
