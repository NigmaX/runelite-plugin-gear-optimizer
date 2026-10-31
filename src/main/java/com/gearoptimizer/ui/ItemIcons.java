package com.gearoptimizer.ui;

import com.gearoptimizer.data.OwnedItem;
import java.awt.Dimension;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Item pictures for the window, in labels the size of an inventory slot so they line up everywhere.
 */
final class ItemIcons
{
	/** Width of an inventory slot, which item pictures are drawn for. */
	private static final int SLOT_WIDTH = 36;
	private static final int SLOT_HEIGHT = 32;

	private final ItemManager itemManager;

	ItemIcons(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/**
	 * @return an empty label one inventory slot wide, which centres the picture put in it
	 */
	static JLabel blank(int height)
	{
		JLabel label = new JLabel();
		label.setPreferredSize(new Dimension(SLOT_WIDTH, height));
		label.setHorizontalAlignment(SwingConstants.CENTER);
		return label;
	}

	/**
	 * @return a slot-sized label with the item's picture
	 */
	JLabel icon(int itemId)
	{
		JLabel label = blank(SLOT_HEIGHT);
		addTo(label, itemId);
		return label;
	}

	/**
	 * A slot-sized picture on the background colour, with a tooltip; empty for a null item.
	 */
	JLabel tile(OwnedItem item, String tooltip)
	{
		JLabel label = blank(SLOT_HEIGHT);
		label.setOpaque(true);
		label.setBackground(Ui.BACKGROUND);
		label.setToolTipText(tooltip);
		if (item != null)
		{
			addTo(label, item.getItemId());
		}
		return label;
	}

	void addTo(JLabel label, int itemId)
	{
		itemManager.getImage(itemId).addTo(label);
	}

	/**
	 * Shows a stack of the item, with the quantity drawn on it like in game when stackable.
	 */
	void addTo(JLabel label, int itemId, int quantity, boolean stackable)
	{
		itemManager.getImage(itemId, quantity, stackable).addTo(label);
	}

	/**
	 * @param onLoaded runs once the picture has loaded, to repaint whatever draws the icon
	 */
	ImageIcon imageIcon(int itemId, Runnable onLoaded)
	{
		AsyncBufferedImage image = itemManager.getImage(itemId);
		ImageIcon icon = new ImageIcon(image);
		image.onLoaded(() ->
		{
			icon.setImage(image);
			onLoaded.run();
		});
		return icon;
	}
}
