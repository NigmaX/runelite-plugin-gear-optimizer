package com.gearoptimizer.calc;

import com.gearoptimizer.account.AccountSnapshot;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;

@Getter
@RequiredArgsConstructor
public enum PrayerBoost
{
	NONE("No prayer", null, 0, 1, 1, 0),
	BURST_CLARITY("Clarity + Burst of Strength", CombatStyle.MELEE, 7, 1.05, 1.05, 0),
	IMPROVED_SUPERHUMAN("Improved Reflexes + Superhuman Strength", CombatStyle.MELEE, 16, 1.10, 1.10, 0),
	INCREDIBLE_ULTIMATE("Incredible Reflexes + Ultimate Strength", CombatStyle.MELEE, 34, 1.15, 1.15, 0),
	CHIVALRY("Chivalry", CombatStyle.MELEE, 60, 1.15, 1.18, 0),
	PIETY("Piety", CombatStyle.MELEE, 70, 1.20, 1.23, 0),
	SHARP_EYE("Sharp Eye", CombatStyle.RANGED, 8, 1.05, 1.05, 0),
	HAWK_EYE("Hawk Eye", CombatStyle.RANGED, 26, 1.10, 1.10, 0),
	EAGLE_EYE("Eagle Eye", CombatStyle.RANGED, 44, 1.15, 1.15, 0),
	RIGOUR("Rigour", CombatStyle.RANGED, 74, 1.20, 1.23, 0),
	MYSTIC_WILL("Mystic Will", CombatStyle.MAGIC, 9, 1.05, 1, 0),
	MYSTIC_LORE("Mystic Lore", CombatStyle.MAGIC, 27, 1.10, 1, 1),
	MYSTIC_MIGHT("Mystic Might", CombatStyle.MAGIC, 45, 1.15, 1, 2),
	AUGURY("Augury", CombatStyle.MAGIC, 77, 1.25, 1, 4);

	private final String displayName;
	private final CombatStyle style;
	private final int prayerLevel;
	private final double accuracy;
	private final double strength;
	/** Magic damage bonus in whole percent. */
	private final int magicDamage;

	/**
	 * @return the strongest prayer the account can use for the style
	 */
	public static PrayerBoost best(CombatStyle style, AccountSnapshot account)
	{
		PrayerBoost best = NONE;
		int prayer = account.level(Skill.PRAYER);
		for (PrayerBoost boost : values())
		{
			if (boost.style != style || prayer < boost.prayerLevel)
			{
				continue;
			}
			if (boost == PIETY && account.level(Skill.DEFENCE) < 70)
			{
				continue;
			}
			if (boost == CHIVALRY && account.level(Skill.DEFENCE) < 65)
			{
				continue;
			}
			if ((boost == RIGOUR && !account.isRigourUnlocked()) || (boost == AUGURY && !account.isAuguryUnlocked()))
			{
				continue;
			}
			best = boost;
		}
		return best;
	}
}
