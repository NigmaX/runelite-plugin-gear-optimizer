package com.gearoptimizer.ui;

import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.calc.UpgradeFinder;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.OwnedItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

/**
 * What one style tab shows: the setup, what it is compared against, and how the player got to it.
 */
@Getter
final class TabModel
{
	/** In max DPS view, the pieces still to buy and what they cost. */
	@Value
	static class Shopping
	{
		List<GearSlot> slots;
		long total;
		/** Pieces with no GE price, left out of the total. */
		int untradeable;
	}

	/** The setup on the tab: the recommendation, the player's edit, or the max DPS setup. */
	private final StyleResult result;
	/** The tab shows the recommendation for the best style (never an edit or the max DPS view). */
	private final boolean best;
	/** What the player is wearing now, or null. */
	private final StyleResult current;
	private final UpgradeFinder.Upgrades upgrades;
	/** The optimizer's own setup when the tab shows an edited one, else null. */
	private final StyleResult recommended;
	private final String experimentNote;
	/** The slot to list options for first, or null for the one with the biggest upgrade. */
	private final GearSlot initialSlot;
	/** In max DPS view: the best owned setup, to compare against. Null in the normal view. */
	private final StyleResult ownedBest;
	/** Max DPS mode is on but its result isn't ready yet. */
	private final boolean maxLoading;
	/** In max DPS view: what is left to buy. Null in the normal view. */
	private final Shopping shopping;
	@Getter(AccessLevel.NONE)
	private final Set<Integer> ownedIds;
	@Getter(AccessLevel.NONE)
	private final PriceCache prices;

	/**
	 * @param ownedIds dataset ids of the items the player owns
	 */
	@Builder
	private TabModel(StyleResult result, boolean best, StyleResult current, UpgradeFinder.Upgrades upgrades, StyleResult recommended,
		String experimentNote, GearSlot initialSlot, StyleResult ownedBest, boolean maxLoading, Set<Integer> ownedIds, PriceCache prices)
	{
		this.result = result;
		this.best = best;
		this.current = current;
		this.upgrades = upgrades;
		this.recommended = recommended;
		this.experimentNote = experimentNote;
		this.initialSlot = initialSlot;
		this.ownedBest = ownedBest;
		this.maxLoading = maxLoading;
		this.ownedIds = ownedIds;
		this.prices = prices;
		this.shopping = ownedBest == null ? null : shoppingList();
	}

	boolean isMaxView()
	{
		return ownedBest != null;
	}

	/**
	 * @return true if the tab shows an edited setup
	 */
	boolean isEdited()
	{
		return recommended != null;
	}

	/**
	 * @return true if the item in the max DPS view still has to be bought
	 */
	boolean mustBuy(OwnedItem item)
	{
		return isMaxView() && item != null && !ownedIds.contains(item.getStats().getId());
	}

	/**
	 * @return the GE price, or 0 if untradeable or not looked up yet
	 */
	long priceOf(OwnedItem item)
	{
		Long price = prices.get(item.getStats().getId());
		return price == null ? 0 : price;
	}

	private Shopping shoppingList()
	{
		List<GearSlot> slots = new ArrayList<>();
		long total = 0;
		int untradeable = 0;
		for (GearSlot slot : GearSlot.values())
		{
			OwnedItem item = result.getLoadout().get(slot);
			if (mustBuy(item))
			{
				slots.add(slot);
				long price = priceOf(item);
				total += price;
				untradeable += price > 0 ? 0 : 1;
			}
		}
		return new Shopping(Collections.unmodifiableList(slots), total, untradeable);
	}
}
