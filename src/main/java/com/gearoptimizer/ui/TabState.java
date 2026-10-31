package com.gearoptimizer.ui;

import lombok.Getter;
import lombok.Setter;

/**
 * How the style tabs show their right panel. Every tab shares it, so switching tabs or rebuilding one keeps the same
 * view; the window owns it, so it starts over with the plugin.
 */
@Getter
@Setter
final class TabState
{
	/** The right panel's view: slot options, supplies or special attack. */
	private String rightView = StyleTab.VIEW_SLOT;
	/** Which options the slot list shows: ALL, OWNED or BETTER. */
	private String slotFilter = "ALL";
	/** The slot list's sorted column (see {@link SlotOptionsPanel}), or -1 for the default order. */
	private int sortColumn = -1;
	private boolean sortAscending;
}
