package com.gearoptimizer.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.function.IntPredicate;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class AccountSnapshotTest
{
	private static final IntPredicate GEAR = id -> id != ItemID.SHARK;
	private static final IntPredicate STACKABLE = id -> id == ItemID.RUNE_ARROW;
	private static final int HEAD = EquipmentInventorySlot.HEAD.getSlotIdx();
	private static final int WEAPON = EquipmentInventorySlot.WEAPON.getSlotIdx();
	private static final int AMMO = EquipmentInventorySlot.AMMO.getSlotIdx();

	@Test
	public void gearPutAwayWithTheBankClosedStillCounts()
	{
		AccountSnapshot account = new AccountSnapshot();
		Item[] before = with(worn(), WEAPON, ItemID.ABYSSAL_WHIP, 1);

		assertTrue(account.notePutAway(inventory(), inventory(), before, worn(), GEAR, STACKABLE));
		assertEquals(1, account.quantityOf(ItemID.ABYSSAL_WHIP));
		assertTrue(account.ownedItemIds().contains(ItemID.ABYSSAL_WHIP));
		assertEquals(1, account.copy().putAwayCount());
	}

	@Test
	public void equippingOrSwappingGearIsNotPuttingItAway()
	{
		AccountSnapshot account = new AccountSnapshot();
		Item[] inventory = with(inventory(), 0, ItemID.ABYSSAL_WHIP, 1);
		assertFalse(account.notePutAway(inventory, inventory(), worn(), with(worn(), WEAPON, ItemID.ABYSSAL_WHIP, 1), GEAR, STACKABLE));

		Item[] wornBefore = with(worn(), WEAPON, ItemID.TOTS_CHARGED, 1);
		Item[] inventoryAfter = with(inventory(), 0, ItemID.TOTS_CHARGED, 1);
		Item[] wornAfter = with(worn(), WEAPON, ItemID.ABYSSAL_WHIP, 1);
		assertFalse(account.notePutAway(inventory, inventoryAfter, wornBefore, wornAfter, GEAR, STACKABLE));
		assertEquals(0, account.putAwayCount());
	}

	@Test
	public void gearThatDegradesOrRunsOutOfChargesIsNotPutAway()
	{
		AccountSnapshot account = new AccountSnapshot();
		Item[] before = with(with(worn(), HEAD, ItemID.BARROWS_AHRIM_HEAD_100, 1), WEAPON, ItemID.TOTS_CHARGED, 1);
		Item[] after = with(with(worn(), HEAD, ItemID.BARROWS_AHRIM_HEAD_75, 1), WEAPON, ItemID.TOTS_UNCHARGED, 1);

		assertFalse(account.notePutAway(inventory(), inventory(), before, after, GEAR, STACKABLE));
	}

	@Test
	public void usedUpAmmoAndEatenFoodAreNotPutAway()
	{
		AccountSnapshot account = new AccountSnapshot();
		Item[] inventory = with(inventory(), 0, ItemID.SHARK, 1);
		Item[] worn = with(worn(), AMMO, ItemID.RUNE_ARROW, 1);

		assertFalse(account.notePutAway(inventory, inventory(), worn, worn(), GEAR, STACKABLE));
	}

	@Test
	public void aStackOfAmmoPutAwayCountsInFull()
	{
		AccountSnapshot account = new AccountSnapshot();
		Item[] worn = with(worn(), AMMO, ItemID.RUNE_ARROW, 500);
		Item[] inventoryBefore = with(inventory(), 0, ItemID.RUNE_ARROW, 300);
		Item[] inventoryAfter = with(inventory(), 0, ItemID.RUNE_ARROW, 100);

		assertTrue(account.notePutAway(inventoryBefore, inventoryAfter, worn, worn(), GEAR, STACKABLE));
		assertEquals(700, account.quantityOf(ItemID.RUNE_ARROW));
	}

	@Test
	public void gearThatComesBackIsNoLongerPutAway()
	{
		AccountSnapshot account = new AccountSnapshot();
		Item[] holding = with(inventory(), 0, ItemID.ABYSSAL_WHIP, 1);
		account.notePutAway(holding, inventory(), worn(), worn(), GEAR, STACKABLE);

		assertTrue(account.notePutAway(inventory(), holding, worn(), worn(), GEAR, STACKABLE));
		assertEquals(0, account.putAwayCount());
		assertFalse(account.ownedItemIds().contains(ItemID.ABYSSAL_WHIP));
	}

	private static Item[] inventory()
	{
		return empty(28);
	}

	private static Item[] worn()
	{
		return empty(14);
	}

	private static Item[] empty(int slots)
	{
		Item[] items = new Item[slots];
		Arrays.fill(items, new Item(-1, 0));
		return items;
	}

	private static Item[] with(Item[] items, int slot, int id, int quantity)
	{
		Item[] copy = items.clone();
		copy[slot] = new Item(id, quantity);
		return copy;
	}
}
