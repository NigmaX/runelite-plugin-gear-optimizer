package com.gearoptimizer.data;

import lombok.Value;

/**
 * An item the player owns, paired with the stats it is calculated with.
 */
@Value
public class OwnedItem
{
	EquipmentItem stats;
	/** The real item id in the bank, used for highlighting. */
	int itemId;
	/** True when the owned copy is uncharged/empty and was resolved to its charged stats. */
	boolean needsCharges;
}
