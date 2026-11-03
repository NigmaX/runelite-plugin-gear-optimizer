package com.gearoptimizer;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.util.ImageUtil;

/**
 * A stone button in the world map's bottom bar, beside the zoom buttons, that centres the map on the pinned monster.
 * It only moves the map on screen; nothing is sent to the game server. Client thread only.
 */
final class WorldMapButton
{
	private static final int SPRITE = -79_102;
	private static final int HOVER_SPRITE = -79_103;
	private static final int GAP = 4;
	/** The buttons on the right of the bar; the new one goes left of them. */
	private static final int[] RIGHT_BUTTONS = {
		InterfaceID.Worldmap.ZOOM_OUT, InterfaceID.Worldmap.ZOOM_IN, InterfaceID.Worldmap.OVERVIEW_TOGGLE, InterfaceID.Worldmap.KEY_TOGGLE,
	};

	private final Client client;
	private final Runnable onClick;
	/** The monster to focus on, or null to hide the button. */
	private String target;
	private BufferedImage icon;
	private Dimension spriteSize;

	WorldMapButton(Client client, Runnable onClick)
	{
		this.client = client;
		this.onClick = onClick;
	}

	/**
	 * Shows the button for a monster, or hides it with a null name.
	 */
	void show(String name, BufferedImage icon)
	{
		target = name;
		if (icon != this.icon)
		{
			this.icon = icon;
			spriteSize = null;
		}
		refresh();
	}

	/**
	 * Adds the button to the open map if it's missing, and shows or hides it.
	 */
	void refresh()
	{
		Widget zoomOut = client.getWidget(InterfaceID.Worldmap.ZOOM_OUT);
		if (zoomOut == null || zoomOut.getParent() == null)
		{
			return;
		}
		Widget button = find(zoomOut.getParent());
		if (target == null)
		{
			if (button != null)
			{
				button.setHidden(true);
			}
			return;
		}
		if (button == null)
		{
			button = create(zoomOut);
		}
		if (button != null)
		{
			button.setHidden(false);
			button.setName("<col=ff9040>" + target + "</col>");
		}
	}

	void stop()
	{
		target = null;
		refresh();
		client.getSpriteOverrides().remove(SPRITE);
		client.getSpriteOverrides().remove(HOVER_SPRITE);
		spriteSize = null;
	}

	private Widget create(Widget zoomOut)
	{
		Widget bar = zoomOut.getParent();
		Dimension size = new Dimension(zoomOut.getWidth(), zoomOut.getHeight());
		if (size.width <= 0 || size.height <= 0)
		{
			return null;
		}
		if (!size.equals(spriteSize))
		{
			client.getSpriteOverrides().put(SPRITE, ImageUtil.getImageSpritePixels(GameButtons.stone(size, false, icon), client));
			client.getSpriteOverrides().put(HOVER_SPRITE, ImageUtil.getImageSpritePixels(GameButtons.stone(size, true, icon), client));
			spriteSize = size;
		}

		int left = zoomOut.getRelativeX();
		for (int id : RIGHT_BUTTONS)
		{
			Widget other = client.getWidget(id);
			if (other != null && !other.isHidden() && other.getParentId() == bar.getId() && other.getRelativeX() > bar.getWidth() / 2)
			{
				left = Math.min(left, other.getRelativeX());
			}
		}
		int x = left - GAP - size.width;

		Widget button = bar.createChild(-1, WidgetType.GRAPHIC);
		button.setSpriteId(SPRITE);
		// Anchor it like the zoom buttons, so it stays beside them when the map is resized
		if (zoomOut.getXPositionMode() == WidgetPositionMode.ABSOLUTE_RIGHT)
		{
			button.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
			button.setOriginalX(bar.getWidth() - x - size.width);
		}
		else
		{
			button.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
			button.setOriginalX(x);
		}
		button.setYPositionMode(zoomOut.getYPositionMode());
		button.setOriginalY(zoomOut.getOriginalY());
		button.setOriginalWidth(size.width);
		button.setOriginalHeight(size.height);
		button.setAction(0, "Focus on");
		button.setHasListener(true);
		button.setOnOpListener((JavaScriptCallback) e -> onClick.run());
		button.setOnMouseOverListener((JavaScriptCallback) e -> button.setSpriteId(HOVER_SPRITE));
		button.setOnMouseLeaveListener((JavaScriptCallback) e -> button.setSpriteId(SPRITE));
		button.revalidate();
		return button;
	}

	private static Widget find(Widget bar)
	{
		Widget[] children = bar.getDynamicChildren();
		if (children == null)
		{
			return null;
		}
		for (Widget child : children)
		{
			if (child != null && (child.getSpriteId() == SPRITE || child.getSpriteId() == HOVER_SPRITE))
			{
				return child;
			}
		}
		return null;
	}
}
