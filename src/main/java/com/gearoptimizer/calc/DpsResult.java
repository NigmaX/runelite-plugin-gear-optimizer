package com.gearoptimizer.calc;

import java.util.List;
import lombok.Value;

@Value
public class DpsResult
{
	/** Length of a game tick in seconds. */
	public static final double SECONDS_PER_TICK = 0.6;

	public static final DpsResult NONE = new DpsResult(0, 0, 0, 0, 1, 0, 0, List.of(), null);

	double dps;
	int maxHit;
	double accuracy;
	/** Average damage per attack, including misses. */
	double averageHit;
	int speedTicks;
	long attackRoll;
	long defenceRoll;
	/** Bonuses that were applied, for display. */
	List<String> effects;
	/** The cannon's share of {@link #dps}, or null without a cannon. */
	Cannon.Fire cannon;

	/**
	 * @return rough time to kill in seconds (cannon included), ignoring overkill and regeneration
	 */
	public double timeToKill(int hitpoints)
	{
		if (dps <= 0)
		{
			return Double.POSITIVE_INFINITY;
		}
		return hitpoints / dps;
	}
}
