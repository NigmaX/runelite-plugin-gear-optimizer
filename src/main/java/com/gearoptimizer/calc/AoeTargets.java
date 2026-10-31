package com.gearoptimizer.calc;

import com.gearoptimizer.data.Monster;
import java.util.regex.Pattern;

/**
 * Guesses how many monsters an area attack hits. Only the Slayer tasks players are known to burst or barrage in
 * multi-combat count as stacked; everything else is one target unless the player says otherwise. Large monsters
 * such as bloodvelds rarely share a 3x3, even in multi-combat.
 */
public final class AoeTargets
{
	/** Typical number of monsters caught in a 3x3 on a stacking task. */
	public static final int STACKED = 4;

	private static final Pattern STACKING = Pattern.compile(
		"^(dust devil|nechryael|greater nechryael|abyssal demon|smoke devil|maniacal monkey)",
		Pattern.CASE_INSENSITIVE);

	private AoeTargets()
	{
	}

	/**
	 * A 3x3 attack is centred on the target's south-west tile and hits monsters whose south-west tile is inside it,
	 * so big monsters can't fill it: at most 9 of size 1, 4 of size 2 (one per corner) and only 1 of size 3 or more.
	 *
	 * @see <a href="https://oldschool.runescape.wiki/w/Area_of_effect">Area of effect</a>
	 */
	public static int maxInArea(Monster monster)
	{
		int size = monster == null ? 1 : monster.getSize();
		return size <= 1 ? 9 : size == 2 ? 4 : 1;
	}

	public static int guess(Monster monster)
	{
		return monster != null && STACKING.matcher(monster.getName()).find() ? STACKED : 1;
	}
}
