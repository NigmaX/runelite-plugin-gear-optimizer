package com.gearoptimizer;

import com.gearoptimizer.calc.MonsterRules;
import com.gearoptimizer.calc.StyleResult;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the selected loadout's items, and the supplies to bring, in the bank.
 */
class BankHighlightOverlay extends WidgetItemOverlay
{
	private final ItemManager itemManager;
	private final GearOptimizerConfig config;
	private volatile Set<Integer> highlighted = Collections.emptySet();

	@Inject
	BankHighlightOverlay(ItemManager itemManager, GearOptimizerConfig config)
	{
		this.itemManager = itemManager;
		this.config = config;
		showOnBank();
	}

	/**
	 * Outlines a setup's gear and supplies in the bank, or nothing for null.
	 */
	void highlight(StyleResult result)
	{
		if (result == null)
		{
			highlighted = Collections.emptySet();
			return;
		}
		Set<Integer> ids = result.getLoadout().itemIds();
		for (MonsterRules.CheckedItem item : result.getInventory())
		{
			ids.addAll(item.getItemIds());
		}
		highlighted = Set.copyOf(ids);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.highlightInBank() || !highlighted.contains(itemId))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), config.highlightColor());
		graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
	}
}
