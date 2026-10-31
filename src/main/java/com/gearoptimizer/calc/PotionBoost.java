package com.gearoptimizer.calc;

import com.gearoptimizer.account.AccountSnapshot;
import java.util.regex.Pattern;
import lombok.Getter;
import net.runelite.api.Skill;

/**
 * Stat-boosting potions, ordered weakest to strongest within each style.
 */
@Getter
public enum PotionBoost
{
	NONE("No potion", null, 0, 0, null),
	COMBAT("Combat potion", CombatStyle.MELEE, 3, 10, "^combat potion"),
	ATTACK_STRENGTH("Attack + Strength potions", CombatStyle.MELEE, 3, 10, "^(attack|strength) potion"),
	SUPER_COMBAT("Super combat potion", CombatStyle.MELEE, 5, 15, "^(divine )?super (combat|attack|strength)"),
	RANGING("Ranging potion", CombatStyle.RANGED, 4, 10, "^(divine )?(ranging|bastion) potion"),
	MAGIC("Magic potion", CombatStyle.MAGIC, 4, 0, "^(divine )?(magic|battlemage) potion"),
	IMBUED_HEART("Imbued heart", CombatStyle.MAGIC, 1, 10, "^imbued heart"),
	SATURATED_HEART("Saturated heart", CombatStyle.MAGIC, 4, 10, "^saturated heart");

	private final String displayName;
	private final CombatStyle style;
	private final int flat;
	private final int percent;
	private final Pattern itemPattern;

	PotionBoost(String displayName, CombatStyle style, int flat, int percent, String regex)
	{
		this.displayName = displayName;
		this.style = style;
		this.flat = flat;
		this.percent = percent;
		this.itemPattern = regex == null ? null : Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
	}

	public int boost(int level)
	{
		return level + flat + level * percent / 100;
	}

	public int boostedLevel(Skill skill, int level)
	{
		if (this == NONE)
		{
			return level;
		}
		switch (style)
		{
			case MELEE:
				return skill == Skill.ATTACK || skill == Skill.STRENGTH ? boost(level) : level;
			case RANGED:
				return skill == Skill.RANGED ? boost(level) : level;
			default:
				return skill == Skill.MAGIC ? boost(level) : level;
		}
	}

	/**
	 * @return the strongest boost for the style that the account owns
	 */
	public static PotionBoost bestOwned(CombatStyle style, AccountSnapshot account)
	{
		PotionBoost best = NONE;
		for (PotionBoost boost : values())
		{
			if (boost.style == style && owns(account, boost.itemPattern))
			{
				best = boost;
			}
		}
		return best;
	}

	/**
	 * @return the strongest boost for the style regardless of what is owned
	 */
	public static PotionBoost strongest(CombatStyle style)
	{
		PotionBoost best = NONE;
		for (PotionBoost boost : values())
		{
			if (boost.style == style)
			{
				best = boost;
			}
		}
		return best;
	}

	private static boolean owns(AccountSnapshot account, Pattern pattern)
	{
		return !MonsterRules.matchingIds(account, pattern).isEmpty();
	}
}
