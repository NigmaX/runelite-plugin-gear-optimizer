package com.gearoptimizer.calc;

import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.OwnedItem;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * A set of worn items, plus the darts loaded into a blowpipe and the attack option used.
 */
public class Loadout
{
	private final EnumMap<GearSlot, OwnedItem> items = new EnumMap<>(GearSlot.class);
	@Getter
	@Setter
	private OwnedItem blowpipeDart;
	@Getter
	@Setter
	private Stance stance;
	@Getter
	@Setter
	private Spell spell;

	public Loadout()
	{
	}

	public Loadout(Loadout other)
	{
		items.putAll(other.items);
		blowpipeDart = other.blowpipeDart;
		stance = other.stance;
		spell = other.spell;
	}

	public OwnedItem get(GearSlot slot)
	{
		return items.get(slot);
	}

	public EquipmentItem stats(GearSlot slot)
	{
		OwnedItem owned = items.get(slot);
		return owned == null ? null : owned.getStats();
	}

	public void set(GearSlot slot, OwnedItem item)
	{
		if (item == null)
		{
			items.remove(slot);
		}
		else
		{
			items.put(slot, item);
		}
	}

	public EquipmentItem weapon()
	{
		return stats(GearSlot.WEAPON);
	}

	public Map<GearSlot, OwnedItem> asMap()
	{
		return Collections.unmodifiableMap(items);
	}

	public Collection<OwnedItem> items()
	{
		return items.values();
	}

	public boolean isBlowpipe()
	{
		EquipmentItem weapon = weapon();
		return weapon != null && weapon.getLowerName().contains("blowpipe");
	}

	/**
	 * @return the bank item ids of the worn items and blowpipe darts, for finding them in the bank
	 */
	public Set<Integer> itemIds()
	{
		Set<Integer> ids = new HashSet<>();
		items.values().forEach(item -> ids.add(item.getItemId()));
		if (blowpipeDart != null)
		{
			ids.add(blowpipeDart.getItemId());
		}
		return ids;
	}

	/**
	 * @return the dataset ids of the worn items and blowpipe darts, for price lookups
	 */
	public Set<Integer> statIds()
	{
		Set<Integer> ids = new HashSet<>();
		items.values().forEach(item -> ids.add(item.getStats().getId()));
		if (blowpipeDart != null)
		{
			ids.add(blowpipeDart.getStats().getId());
		}
		return ids;
	}

	/**
	 * @return the sum of a stat over all worn items
	 */
	public int total(java.util.function.ToIntFunction<EquipmentItem> stat)
	{
		int sum = 0;
		for (OwnedItem item : items.values())
		{
			sum += stat.applyAsInt(item.getStats());
		}
		return sum;
	}
}
