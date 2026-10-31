package com.gearoptimizer.calc;

import com.gearoptimizer.data.Monster;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;

/**
 * The dwarf multicannon, firing alongside the player's own attacks.
 * <p>
 * It turns one of eight directions per tick and shoots at most once per direction, so each monster around it is
 * shot about once per 8-tick turn, up to 8 monsters. The max hit is fixed (30 steel, 35 granite). Accuracy is the
 * player's: ranged accuracy with a ranged weapon, melee accuracy with the current attack style otherwise, and the
 * better of the two with a powered staff; it rolls against heavy ranged defence. Target bonuses (slayer helm,
 * salve, banes) don't apply.
 *
 * @see <a href="https://oldschool.runescape.wiki/w/Dwarf_multicannon">Dwarf multicannon</a>
 */
public final class Cannon
{
	public static final int STEEL_MAX_HIT = 30;
	public static final int GRANITE_MAX_HIT = 35;
	private static final int TICKS_PER_TURN = 8;
	private static final int MAX_TARGETS = 8;
	private static final Pattern IGNORES_CANNON = Pattern.compile("^(demonic gorilla|sand crab|ammonite crab)", Pattern.CASE_INSENSITIVE);

	/** What the cannon adds to a setup. */
	@Value
	public static class Fire
	{
		double dps;
		double accuracy;
		int maxHit;
		int targets;
		double ballsPerHour;
		int ballItemId;
	}

	private Cannon()
	{
	}

	/**
	 * @return the player's result with the cannon's damage added to its DPS, or the result itself without a cannon
	 */
	static DpsResult addTo(DpsResult player, Loadout loadout, CombatContext ctx)
	{
		Monster monster = ctx.getMonster();
		if (ctx.getCannonMaxHit() <= 0 || player.getDps() <= 0 || IGNORES_CANNON.matcher(monster.getName()).find())
		{
			return player;
		}
		int targets = Math.max(1, Math.min(MAX_TARGETS, ctx.getAoeTargets()));
		double accuracy = accuracy(loadout, ctx);
		double secondsPerTurn = TICKS_PER_TURN * DpsResult.SECONDS_PER_TICK;
		double dps = targets * accuracy * ctx.getCannonMaxHit() / 2.0 / secondsPerTurn;
		Fire fire = new Fire(dps, accuracy, ctx.getCannonMaxHit(), targets, targets * 3600 / secondsPerTurn,
			ctx.getCannonMaxHit() >= GRANITE_MAX_HIT ? ItemID.GRANITE_CANNONBALL : ItemID.MCANNONBALL);
		List<String> effects = new ArrayList<>(player.getEffects());
		effects.add(String.format("Cannon +%.2f DPS (%d monster%s, %.0f%% accuracy)", dps, targets, targets == 1 ? "" : "s", accuracy * 100));
		return new DpsResult(player.getDps() + dps, player.getMaxHit(), player.getAccuracy(), player.getAverageHit(),
			player.getSpeedTicks(), player.getAttackRoll(), player.getDefenceRoll(), List.copyOf(effects), fire);
	}

	static double accuracy(Loadout loadout, CombatContext ctx)
	{
		Monster monster = ctx.getMonster();
		long defence = (long) (monster.getSkills().getDef() + 9) * Math.max(0, monster.getDefensive().getHeavy() + 64);
		Stance stance = loadout.getStance();
		CombatStyle style = stance == null ? CombatStyle.MELEE : stance.getStyle();
		long attack;
		if (style == CombatStyle.RANGED)
		{
			attack = rangedRoll(loadout, ctx, stance.getAttackBonus());
		}
		else if (style == CombatStyle.MELEE)
		{
			attack = meleeRoll(loadout, ctx, stance.getType(), stance.getAttackBonus());
		}
		else if ("Powered Staff".equals(GearEffects.weaponCategory(loadout)))
		{
			attack = Math.max(rangedRoll(loadout, ctx, 0), meleeRoll(loadout, ctx, AttackType.CRUSH, 0));
		}
		else
		{
			// Other staves and wands count as melee weapons, which for them means crush
			attack = meleeRoll(loadout, ctx, AttackType.CRUSH, 0);
		}
		return DpsCalculator.hitChance(attack, defence);
	}

	private static long rangedRoll(Loadout loadout, CombatContext ctx, int stanceBonus)
	{
		PrayerBoost prayer = ctx.getPrayer();
		double prayerAccuracy = prayer.getStyle() == CombatStyle.RANGED ? prayer.getAccuracy() : 1;
		int effective = (int) (ctx.boostedLevel(Skill.RANGED) * prayerAccuracy) + stanceBonus + 8;
		if (GearEffects.hasVoid(loadout, "void ranger helm"))
		{
			effective = effective * 11 / 10;
		}
		long roll = (long) effective * (DpsCalculator.attackBonus(loadout, AttackType.RANGED) + 64);
		return roll * (100 + GearEffects.crystalAccuracyBonus(loadout)) / 100;
	}

	private static long meleeRoll(Loadout loadout, CombatContext ctx, AttackType type, int stanceBonus)
	{
		PrayerBoost prayer = ctx.getPrayer();
		double prayerAccuracy = prayer.getStyle() == CombatStyle.MELEE ? prayer.getAccuracy() : 1;
		int effective = (int) (ctx.boostedLevel(Skill.ATTACK) * prayerAccuracy) + stanceBonus + 8;
		if (GearEffects.hasVoid(loadout, "void melee helm"))
		{
			effective = effective * 11 / 10;
		}
		return (long) effective * (DpsCalculator.attackBonus(loadout, type) + 64);
	}
}
