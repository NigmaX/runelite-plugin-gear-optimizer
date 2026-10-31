package com.gearoptimizer.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Holds the monster and equipment data. Loads the copy downloaded by {@link DataUpdater}
 * when present, otherwise the copy bundled with the plugin.
 */
@Slf4j
@Singleton
public class GameDataRepository
{
	public static final File DATA_DIR = new File(RuneLite.RUNELITE_DIR, "gear-optimizer");
	static final String MONSTERS_FILE = "monsters.json";
	static final String EQUIPMENT_FILE = "equipment.json";

	private static final Set<String> CHARGED_VERSIONS = Set.of("Charged", "Active", "Fully charged", "Normal", "Regular", "");
	private static final Set<String> UNCHARGED_VERSIONS = Set.of("Uncharged", "Empty", "Inactive", "Partially charged");

	private static final Type MONSTER_LIST = new TypeToken<List<Monster>>()
	{
	}.getType();
	private static final Type EQUIPMENT_LIST = new TypeToken<List<EquipmentItem>>()
	{
	}.getType();

	/** Everything one load produced; swapped in whole so readers never see half old, half new data. */
	@AllArgsConstructor
	private static final class Data
	{
		static final Data EMPTY = new Data(Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyMap(), Collections.emptyMap(), "Not loaded");

		final List<Monster> monsters;
		final Map<Integer, Monster> monstersById;
		final List<EquipmentItem> equipment;
		final Map<Integer, EquipmentItem> equipmentById;
		final Map<String, List<EquipmentItem>> equipmentByName;
		final String source;
	}

	private final Gson gson;
	private volatile Data data = Data.EMPTY;

	@Inject
	public GameDataRepository(Gson gson)
	{
		this.gson = gson;
	}

	/**
	 * Loads data from disk or the bundled resources. Does blocking IO: never call on the client thread.
	 */
	public synchronized void load()
	{
		List<Monster> loadedMonsters = null;
		List<EquipmentItem> loadedEquipment = null;
		String source = "Bundled";

		File monstersFile = new File(DATA_DIR, MONSTERS_FILE);
		File equipmentFile = new File(DATA_DIR, EQUIPMENT_FILE);
		if (monstersFile.isFile() && equipmentFile.isFile())
		{
			try
			{
				loadedMonsters = readFile(monstersFile, MONSTER_LIST);
				loadedEquipment = readFile(equipmentFile, EQUIPMENT_LIST);
				source = "Downloaded " + new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
					.format(new java.util.Date(monstersFile.lastModified()));
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Downloaded gear optimizer data is unreadable, falling back to bundled data", e);
				loadedMonsters = null;
				loadedEquipment = null;
			}
		}

		if (loadedMonsters == null || loadedEquipment == null)
		{
			source = "Bundled";
			loadedMonsters = readResource(MONSTERS_FILE, MONSTER_LIST);
			loadedEquipment = readResource(EQUIPMENT_FILE, EQUIPMENT_LIST);
		}

		apply(loadedMonsters, loadedEquipment, source);
	}

	void apply(List<Monster> loadedMonsters, List<EquipmentItem> loadedEquipment, String source)
	{
		List<Monster> sortedMonsters = new ArrayList<>(loadedMonsters);
		sortedMonsters.removeIf(m -> m.getName() == null || m.getSkills() == null || m.getDefensive() == null);
		sortedMonsters.sort(Comparator.comparing(Monster::getName, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(m -> m.getVersion() == null ? "" : m.getVersion()));

		Map<Integer, Monster> byId = new HashMap<>();
		for (Monster monster : sortedMonsters)
		{
			byId.putIfAbsent(monster.getId(), monster);
		}

		List<EquipmentItem> items = new ArrayList<>(loadedEquipment);
		items.removeIf(i -> i.getGearSlot() == null || i.getBonuses() == null || i.getOffensive() == null || i.getDefensive() == null);
		Map<Integer, EquipmentItem> itemsById = new HashMap<>();
		Map<String, List<EquipmentItem>> itemsByName = new HashMap<>();
		for (EquipmentItem item : items)
		{
			itemsById.put(item.getId(), item);
			itemsByName.computeIfAbsent(item.getLowerName(), k -> new ArrayList<>()).add(item);
		}

		data = new Data(Collections.unmodifiableList(sortedMonsters), byId, Collections.unmodifiableList(items), itemsById, itemsByName, source);
		log.debug("Loaded {} monsters and {} items ({})", sortedMonsters.size(), items.size(), source);
	}

	public boolean isLoaded()
	{
		return !data.monsters.isEmpty();
	}

	public List<Monster> getMonsters()
	{
		return data.monsters;
	}

	public List<EquipmentItem> getEquipment()
	{
		return data.equipment;
	}

	public String getSourceDescription()
	{
		return data.source;
	}

	public Monster getMonster(int npcId)
	{
		return data.monstersById.get(npcId);
	}

	public EquipmentItem getItem(int datasetId)
	{
		return data.equipmentById.get(datasetId);
	}

	/**
	 * Resolves an item the player owns to its combat stats. Ornament kits and other cosmetic
	 * variants fall back to their base item; uncharged items resolve to their charged form.
	 *
	 * @return null when the item has no combat stats
	 */
	public OwnedItem resolve(int itemId)
	{
		Data current = data;
		EquipmentItem item = current.equipmentById.get(itemId);
		if (item == null)
		{
			item = current.equipmentById.get(ItemVariationMapping.map(itemId));
		}
		if (item == null)
		{
			return null;
		}

		String version = item.getVersion() == null ? "" : item.getVersion();
		if (UNCHARGED_VERSIONS.contains(version))
		{
			EquipmentItem charged = findChargedVersion(current, item);
			if (charged != null)
			{
				return new OwnedItem(charged, itemId, true);
			}
		}
		return new OwnedItem(item, itemId, false);
	}

	private static EquipmentItem findChargedVersion(Data current, EquipmentItem item)
	{
		List<EquipmentItem> sameName = current.equipmentByName.get(item.getLowerName());
		if (sameName == null)
		{
			return null;
		}
		for (EquipmentItem candidate : sameName)
		{
			String version = candidate.getVersion() == null ? "" : candidate.getVersion();
			if (candidate != item && CHARGED_VERSIONS.contains(version))
			{
				return candidate;
			}
		}
		return null;
	}

	private <T> T readFile(File file, Type type) throws IOException
	{
		try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
		{
			T result = gson.fromJson(reader, type);
			if (result == null)
			{
				throw new IOException("empty file " + file);
			}
			return result;
		}
	}

	private <T> T readResource(String name, Type type)
	{
		try (InputStream in = GameDataRepository.class.getResourceAsStream(name);
			Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			return gson.fromJson(reader, type);
		}
		catch (IOException | NullPointerException e)
		{
			throw new IllegalStateException("Bundled gear optimizer data is missing: " + name, e);
		}
	}

	/**
	 * Parses raw JSON to check it is usable before it replaces the data on disk.
	 */
	boolean validate(String monstersJson, String equipmentJson)
	{
		try
		{
			List<Monster> m = gson.fromJson(monstersJson, MONSTER_LIST);
			List<EquipmentItem> e = gson.fromJson(equipmentJson, EQUIPMENT_LIST);
			return m != null && e != null && m.size() > 1000 && e.size() > 1000;
		}
		catch (RuntimeException ex)
		{
			return false;
		}
	}
}
