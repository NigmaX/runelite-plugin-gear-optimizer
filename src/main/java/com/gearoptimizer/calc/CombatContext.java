package com.gearoptimizer.calc;

import com.gearoptimizer.data.Monster;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.Skill;

/**
 * Everything about the fight that is not gear: the target, the player's levels and their boosts.
 */
@Value
@Builder(toBuilder = true)
public class CombatContext
{
	Monster monster;
	Map<Skill, Integer> levels;
	PrayerBoost prayer;
	PotionBoost potion;
	boolean onSlayerTask;
	boolean inWilderness;
	boolean kandarinHardDiary;
	/** Monsters hit by area attacks (burst/barrage, chinchompas); 1 for single-target. */
	@Builder.Default
	int aoeTargets = 1;
	/** Max hit of the cannon fired alongside the player (30 steel, 35 granite), or 0 without one. */
	@Builder.Default
	int cannonMaxHit = 0;
	/** Whether the player can cast Ancient Magicks (Desert Treasure I). */
	@Builder.Default
	boolean ancientMagicks = true;

	/**
	 * @return monsters a 3x3 attack (burst, barrage, chinchompas) hits: the stacked count, limited by the monster's size
	 */
	public int areaTargets()
	{
		return Math.max(1, Math.min(aoeTargets, AoeTargets.maxInArea(monster)));
	}

	public int baseLevel(Skill skill)
	{
		Integer level = levels.get(skill);
		if (level == null)
		{
			return skill == Skill.HITPOINTS ? 10 : 1;
		}
		return level;
	}

	public int boostedLevel(Skill skill)
	{
		return potion.boostedLevel(skill, baseLevel(skill));
	}
}
