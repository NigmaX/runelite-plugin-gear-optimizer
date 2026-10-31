package com.gearoptimizer.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.EquipmentInventorySlot;

@Getter
@RequiredArgsConstructor
public enum GearSlot
{
	HEAD("head", "Head", EquipmentInventorySlot.HEAD),
	CAPE("cape", "Cape", EquipmentInventorySlot.CAPE),
	NECK("neck", "Neck", EquipmentInventorySlot.AMULET),
	AMMO("ammo", "Ammo", EquipmentInventorySlot.AMMO),
	WEAPON("weapon", "Weapon", EquipmentInventorySlot.WEAPON),
	BODY("body", "Body", EquipmentInventorySlot.BODY),
	SHIELD("shield", "Shield", EquipmentInventorySlot.SHIELD),
	LEGS("legs", "Legs", EquipmentInventorySlot.LEGS),
	HANDS("hands", "Hands", EquipmentInventorySlot.GLOVES),
	FEET("feet", "Feet", EquipmentInventorySlot.BOOTS),
	RING("ring", "Ring", EquipmentInventorySlot.RING);

	private final String key;
	private final String displayName;
	private final EquipmentInventorySlot inventorySlot;

	public static GearSlot fromKey(String key)
	{
		for (GearSlot slot : values())
		{
			if (slot.key.equals(key))
			{
				return slot;
			}
		}
		return null;
	}

	public static GearSlot fromInventoryIndex(int index)
	{
		for (GearSlot slot : values())
		{
			if (slot.inventorySlot.getSlotIdx() == index)
			{
				return slot;
			}
		}
		return null;
	}
}
