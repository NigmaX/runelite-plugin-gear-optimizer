package com.gearoptimizer.calc;

import lombok.Value;

/**
 * One option on the combat options tab, with the invisible level bonuses it grants.
 */
@Value
public class Stance
{
	String name;
	AttackType type;
	CombatStyle style;
	int attackBonus;
	int strengthBonus;
	/** Change to the weapon's attack speed in ticks (rapid is -1). */
	int speedDelta;

	static Stance melee(String name, AttackType type, String stance)
	{
		switch (stance)
		{
			case "accurate":
				return new Stance(name, type, CombatStyle.MELEE, 3, 0, 0);
			case "aggressive":
				return new Stance(name, type, CombatStyle.MELEE, 0, 3, 0);
			case "controlled":
				return new Stance(name, type, CombatStyle.MELEE, 1, 1, 0);
			default:
				// Defensive: only defence, which the optimizer doesn't score per stance
				return new Stance(name, type, CombatStyle.MELEE, 0, 0, 0);
		}
	}

	static final Stance RANGED_ACCURATE = new Stance("Accurate", AttackType.RANGED, CombatStyle.RANGED, 3, 3, 0);
	static final Stance RANGED_RAPID = new Stance("Rapid", AttackType.RANGED, CombatStyle.RANGED, 0, 0, -1);
	static final Stance MAGIC_ACCURATE = new Stance("Accurate", AttackType.MAGIC, CombatStyle.MAGIC, 2, 0, 0);
	static final Stance AUTOCAST = new Stance("Autocast", AttackType.MAGIC, CombatStyle.MAGIC, 0, 0, 0);
}
