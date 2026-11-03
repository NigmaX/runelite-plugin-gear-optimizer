package com.gearoptimizer.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import org.junit.BeforeClass;
import org.junit.Test;

public class MonsterLocationTest
{
	private static GameDataRepository repository;

	@BeforeClass
	public static void loadData()
	{
		repository = new GameDataRepository(new Gson());
		repository.load();
	}

	private static List<String> names(int npcId)
	{
		return repository.getLocations(npcId).stream().map(MonsterLocation::getName).collect(Collectors.toList());
	}

	@Test
	public void bundledLocationsHaveSpawnPoints()
	{
		List<MonsterLocation> banshees = repository.getLocations(NpcID.KOUREND_BANSHEE);
		assertEquals(1, banshees.size());
		MonsterLocation catacombs = banshees.get(0);
		assertEquals("Catacombs of Kourend", catacombs.getName());
		assertTrue(catacombs.hasPoints());
		assertTrue(catacombs.getPoints().contains(catacombs.center()));
	}

	@Test
	public void undergroundPlacesHaveSurfaceEntrances()
	{
		MonsterLocation catacombs = repository.getLocations(NpcID.KOUREND_BANSHEE).get(0);
		assertTrue(catacombs.isUnderground());
		// The statue of King Rada I in Kourend Castle
		assertTrue(catacombs.getEntrances().toString(), catacombs.getEntrances().contains(new WorldPoint(1639, 3673, 0)));

		MonsterLocation field = repository.getLocations(NpcID.COW).stream()
			.filter(location -> location.getName().equals("Lumbridge cow field"))
			.findFirst()
			.orElseThrow(AssertionError::new);
		assertFalse(field.isUnderground());
		assertTrue(field.getEntrances().isEmpty());
	}

	@Test
	public void eachVersionGetsItsOwnPlaces()
	{
		assertEquals(List.of("Wilderness Slayer Cave"), names(NpcID.WILD_CAVE_ABYSSAL));
		List<String> standard = names(NpcID.SLAYER_ABYSSAL);
		assertTrue(standard.toString(), standard.stream().anyMatch(name -> name.startsWith("Slayer Tower")));
		assertFalse(standard.toString(), standard.contains("Catacombs of Kourend"));
		assertFalse(standard.toString(), standard.contains("Wilderness Slayer Cave"));
	}

	@Test
	public void monsterSeenNearSpawnsIsAtThatPlace()
	{
		List<MonsterLocation> wyrms = repository.getLocations(NpcID.WYRM_LIGHT);
		assertEquals("Wyrmscraig Cavern", MonsterLocation.at(wyrms, new WorldPoint(2620, 8630, 0)).getName());
		assertTrue(MonsterLocation.at(wyrms, new WorldPoint(1260, 10192, 0)).getName().startsWith("Karuulm"));
		assertNull(MonsterLocation.at(wyrms, new WorldPoint(3222, 3218, 0)));
	}

	@Test
	public void wildernessPlacesAreFlagged()
	{
		assertTrue(repository.getLocations(NpcID.WILD_CAVE_ABYSSAL).get(0).isWilderness());
		assertTrue(repository.getLocations(NpcID.SLAYER_ABYSSAL).stream().noneMatch(MonsterLocation::isWilderness));
	}

	@Test
	public void monstersWithoutDataHaveNoPlaces()
	{
		assertTrue(repository.getLocations(-1).isEmpty());
	}
}
