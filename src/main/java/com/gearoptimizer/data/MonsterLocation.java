package com.gearoptimizer.data;

import java.util.List;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * One place a monster can be found, from the wiki's location table: its name, the spawn points there and, for a
 * dungeon or other place off the surface map, the entrances on the surface.
 */
@Value
public class MonsterLocation
{
	String name;
	/** Empty when the wiki gives no coordinates. */
	List<WorldPoint> points;
	/** Off the surface map, so the surface map can't show it. */
	boolean underground;
	/** In the Wilderness, where blighted supplies and Wilderness weapons work. */
	boolean wilderness;
	/** Surface entrances of an underground place; empty when unknown. */
	List<WorldPoint> entrances;

	public boolean hasPoints()
	{
		return !points.isEmpty();
	}

	/** How far from a place's spawns a monster can be and still count as being there. */
	private static final int NEARBY = 64;

	/**
	 * @return the place a monster seen at a point is at, or null if it's near none of them
	 */
	public static MonsterLocation at(List<MonsterLocation> places, WorldPoint seen)
	{
		MonsterLocation best = null;
		int bestDistance = NEARBY + 1;
		for (MonsterLocation place : places)
		{
			for (WorldPoint point : place.points)
			{
				int distance = point.getPlane() == seen.getPlane() ? point.distanceTo2D(seen) : Integer.MAX_VALUE;
				if (distance < bestDistance)
				{
					best = place;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	/**
	 * @return the spawn point nearest the middle of the area, to mark it with one pin
	 */
	public WorldPoint center()
	{
		double x = points.stream().mapToInt(WorldPoint::getX).average().orElse(0);
		double y = points.stream().mapToInt(WorldPoint::getY).average().orElse(0);
		WorldPoint best = null;
		double bestDistance = Double.MAX_VALUE;
		for (WorldPoint point : points)
		{
			double distance = Math.hypot(point.getX() - x, point.getY() - y);
			if (distance < bestDistance)
			{
				best = point;
				bestDistance = distance;
			}
		}
		return best;
	}
}
