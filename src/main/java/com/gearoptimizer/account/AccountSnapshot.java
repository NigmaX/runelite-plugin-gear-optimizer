package com.gearoptimizer.account;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import net.runelite.api.Skill;

/**
 * Everything the optimizer knows about one account. Persisted per RuneScape profile as JSON.
 */
@Data
public class AccountSnapshot
{
	/** itemId -> quantity */
	private Map<Integer, Integer> bank = new HashMap<>();
	private Map<Integer, Integer> inventory = new HashMap<>();
	/** equipment slot index -> itemId */
	private Map<Integer, Integer> equipment = new HashMap<>();
	/** rune itemId -> quantity */
	private Map<Integer, Integer> runePouch = new HashMap<>();
	/** itemId -> name, kept so inventory requirements can be matched without the client */
	private Map<Integer, String> itemNames = new HashMap<>();
	private Map<Skill, Integer> levels = new EnumMap<>(Skill.class);

	private boolean rigourUnlocked;
	private boolean auguryUnlocked;
	private boolean gargoyleSmasher;
	private boolean kandarinHardDiary;
	/** Desert Treasure I done; null until the first login check. */
	private Boolean ancientMagicks;

	private long bankUpdatedAt;

	/**
	 * @return a copy whose maps can be changed without affecting this snapshot
	 */
	public AccountSnapshot copy()
	{
		AccountSnapshot copy = new AccountSnapshot();
		copy.bank = new HashMap<>(nonNull(bank));
		copy.inventory = new HashMap<>(nonNull(inventory));
		copy.equipment = new HashMap<>(nonNull(equipment));
		copy.runePouch = new HashMap<>(nonNull(runePouch));
		copy.itemNames = new HashMap<>(nonNull(itemNames));
		copy.levels = new EnumMap<>(Skill.class);
		copy.levels.putAll(getLevels());
		copy.rigourUnlocked = rigourUnlocked;
		copy.auguryUnlocked = auguryUnlocked;
		copy.gargoyleSmasher = gargoyleSmasher;
		copy.kandarinHardDiary = kandarinHardDiary;
		copy.ancientMagicks = ancientMagicks;
		copy.bankUpdatedAt = bankUpdatedAt;
		return copy;
	}

	/**
	 * @return whether both have the same levels, unlocks and rune types in the pouch. Rune counts are ignored: they
	 * drop with every cast and don't change which gear is best.
	 */
	public boolean sameLevelsAndUnlocksAs(AccountSnapshot other)
	{
		return getLevels().equals(other.getLevels()) && rigourUnlocked == other.rigourUnlocked && auguryUnlocked == other.auguryUnlocked
			&& gargoyleSmasher == other.gargoyleSmasher && kandarinHardDiary == other.kandarinHardDiary
			&& java.util.Objects.equals(ancientMagicks, other.ancientMagicks)
			&& nonNull(runePouch).keySet().equals(nonNull(other.runePouch).keySet());
	}

	public boolean hasBank()
	{
		return bankUpdatedAt > 0;
	}

	public int level(Skill skill)
	{
		Integer level = levels == null ? null : levels.get(skill);
		if (level != null)
		{
			return level;
		}
		return skill == Skill.HITPOINTS ? 10 : 1;
	}

	public Map<Skill, Integer> getLevels()
	{
		return levels == null ? Collections.emptyMap() : levels;
	}

	/**
	 * @return every item id the account has access to: bank, inventory, equipment and rune pouch
	 */
	public Set<Integer> ownedItemIds()
	{
		Set<Integer> ids = new HashSet<>();
		ids.addAll(nonNull(bank).keySet());
		ids.addAll(nonNull(inventory).keySet());
		ids.addAll(nonNull(equipment).values());
		ids.addAll(nonNull(runePouch).keySet());
		return ids;
	}

	public int quantityOf(int itemId)
	{
		int total = nonNull(bank).getOrDefault(itemId, 0) + nonNull(inventory).getOrDefault(itemId, 0)
			+ nonNull(runePouch).getOrDefault(itemId, 0);
		if (nonNull(equipment).containsValue(itemId))
		{
			total += 1;
		}
		return total;
	}

	public String nameOf(int itemId)
	{
		return itemNames == null ? null : itemNames.get(itemId);
	}

	private static <K, V> Map<K, V> nonNull(Map<K, V> map)
	{
		return map == null ? Collections.emptyMap() : map;
	}
}
