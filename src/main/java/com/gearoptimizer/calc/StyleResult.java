package com.gearoptimizer.calc;

import java.util.List;
import lombok.Value;

/**
 * The best loadout found for one combat style.
 */
@Value
public class StyleResult
{
	CombatStyle style;
	Loadout loadout;
	DpsResult dps;
	CombatContext context;
	double score;
	/** Average defence bonus against the monster's attack styles. */
	int defence;
	List<MonsterRules.CheckedItem> inventory;
	List<String> warnings;
	List<SpecialAttacks.SpecOption> specOptions;
	/** Seconds per kill spent on a finishing item such as a rock hammer. */
	double finishSeconds;
	/** Ammo, runes and charges used. */
	Consumables.Cost cost;
	/** Owned ammo/darts/spells that could replace the current one, best DPS first. */
	List<Consumables.Option> consumableOptions;
	/** GE prices used for costs, so edited setups can be costed the same way. */
	java.util.Map<Integer, Integer> prices;

	/**
	 * @return monsters hit per attack: more than 1 for burst/barrage or chinchompas when monsters are stacked
	 */
	public int targetsHit()
	{
		Spell spell = loadout.getSpell();
		boolean area = (spell != null && spell.isAoe())
			|| (loadout.weapon() != null && "Chinchompas".equals(loadout.weapon().getCategory()));
		return area ? context.areaTargets() : 1;
	}

	public double timeToKill()
	{
		return dps.timeToKill(context.getMonster().getHitpoints()) + finishSeconds;
	}

	/**
	 * @return DPS including the time spent finishing each kill
	 */
	public double effectiveDps()
	{
		return effectiveDps(dps.getDps(), context.getMonster().getHitpoints(), finishSeconds);
	}

	static double effectiveDps(double dps, int hitpoints, double finishSeconds)
	{
		if (dps <= 0 || finishSeconds <= 0)
		{
			return dps;
		}
		return hitpoints / (hitpoints / dps + finishSeconds);
	}
}
