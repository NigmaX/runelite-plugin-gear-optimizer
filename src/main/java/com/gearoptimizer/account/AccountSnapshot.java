package com.gearoptimizer.account;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import lombok.Data;
import net.runelite.api.Item;
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
	/**
	 * itemId -> quantity of gear put away since the bank was last read: at a deposit box, in house storage, traded or
	 * dropped. It still counts as owned until the bank is read again.
	 */
	private Map<Integer, Integer> putAway = new HashMap<>();
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
		copy.putAway = new HashMap<>(nonNull(putAway));
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
	 * @return every item id the account has access to: bank, inventory, equipment, rune pouch and gear put away
	 */
	public Set<Integer> ownedItemIds()
	{
		Set<Integer> ids = new HashSet<>();
		ids.addAll(nonNull(bank).keySet());
		ids.addAll(nonNull(inventory).keySet());
		ids.addAll(nonNull(equipment).values());
		ids.addAll(nonNull(runePouch).keySet());
		ids.addAll(nonNull(putAway).keySet());
		return ids;
	}

	public int quantityOf(int itemId)
	{
		int total = nonNull(bank).getOrDefault(itemId, 0) + nonNull(inventory).getOrDefault(itemId, 0)
			+ nonNull(runePouch).getOrDefault(itemId, 0) + nonNull(putAway).getOrDefault(itemId, 0);
		if (nonNull(equipment).containsValue(itemId))
		{
			total += 1;
		}
		return total;
	}

	public int putAwayCount()
	{
		return nonNull(putAway).values().stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * Notes the gear that left the inventory and equipment between two moments while the bank was closed, and
	 * forgets gear that came back. Only units that left a slot empty, or part of an inventory stack, count: gear that
	 * degrades or spends a charge changes in place, a swap moves it between slots, and a worn stack whose last
	 * arrow, dart or chinchompa was used is gone.
	 *
	 * @return whether the gear put away changed
	 */
	public boolean notePutAway(Item[] inventoryBefore, Item[] inventoryAfter, Item[] wornBefore, Item[] wornAfter,
		IntPredicate isGear, IntPredicate isStackable)
	{
		Map<Integer, Integer> left = new HashMap<>();
		for (int i = 0; i < inventoryBefore.length; i++)
		{
			Item before = inventoryBefore[i];
			Item after = i < inventoryAfter.length ? inventoryAfter[i] : null;
			if (isEmpty(before))
			{
				continue;
			}
			if (isEmpty(after))
			{
				left.merge(before.getId(), before.getQuantity(), Integer::sum);
			}
			else if (after.getId() == before.getId() && after.getQuantity() < before.getQuantity())
			{
				left.merge(before.getId(), before.getQuantity() - after.getQuantity(), Integer::sum);
			}
		}
		for (int i = 0; i < wornBefore.length; i++)
		{
			Item before = wornBefore[i];
			Item after = i < wornAfter.length ? wornAfter[i] : null;
			if (isEmpty(before) || !isEmpty(after))
			{
				continue;
			}
			// A worn stack emptied from its last unit was used up, not put away
			if (before.getQuantity() > 1 || !isStackable.test(before.getId()))
			{
				left.merge(before.getId(), before.getQuantity(), Integer::sum);
			}
		}

		Map<Integer, Integer> heldBefore = held(inventoryBefore, wornBefore);
		Map<Integer, Integer> heldAfter = held(inventoryAfter, wornAfter);
		Map<Integer, Integer> stored = new HashMap<>(nonNull(putAway));
		for (Map.Entry<Integer, Integer> entry : left.entrySet())
		{
			int id = entry.getKey();
			// A unit that moved to another slot, like equipping it, hasn't left
			int gone = Math.min(entry.getValue(), heldBefore.getOrDefault(id, 0) - heldAfter.getOrDefault(id, 0));
			if (gone > 0 && isGear.test(id))
			{
				stored.merge(id, gone, Integer::sum);
			}
		}
		for (Map.Entry<Integer, Integer> entry : heldAfter.entrySet())
		{
			int back = entry.getValue() - heldBefore.getOrDefault(entry.getKey(), 0);
			if (back > 0)
			{
				stored.computeIfPresent(entry.getKey(), (id, quantity) -> quantity > back ? quantity - back : null);
			}
		}
		if (stored.equals(nonNull(putAway)))
		{
			return false;
		}
		putAway = stored;
		return true;
	}

	private static Map<Integer, Integer> held(Item[] inventory, Item[] worn)
	{
		Map<Integer, Integer> held = new HashMap<>();
		for (Item[] items : new Item[][]{inventory, worn})
		{
			for (Item item : items)
			{
				if (!isEmpty(item))
				{
					held.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
		}
		return held;
	}

	private static boolean isEmpty(Item item)
	{
		return item == null || item.getId() <= 0;
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
