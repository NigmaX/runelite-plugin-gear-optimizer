package com.gearoptimizer;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.calc.InventoryPlan;
import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.OwnedItem;
import com.gearoptimizer.ui.OptimizerWindow;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.util.ImageUtil;

/**
 * Shows a setup as a Bank Tags tab holding only what the trip needs: the gear laid out like the equipment screen
 * on the left and the supplies like the inventory on the right. Also puts a Gear Optimizer button in the bank's
 * title bar that does the same for the setup open in the window, so the window can stay out of the way.
 */
@Singleton
class BankLayoutService
{
	static final String TAG = "gear optimizer";
	/** Our own sprite ids; negative so they never clash with the game's. */
	private static final int BUTTON_SPRITE = -79_100;
	private static final int BUTTON_HOVER_SPRITE = -79_101;
	private static final int ROW = 8;
	private static final int INVENTORY_COLUMN = 4;
	private static final int INVENTORY_WIDTH = 4;
	/** The in-game equipment screen, three columns wide. */
	private static final GearSlot[][] EQUIPMENT = {
		{null, GearSlot.HEAD, null},
		{GearSlot.CAPE, GearSlot.NECK, GearSlot.AMMO},
		{GearSlot.WEAPON, GearSlot.BODY, GearSlot.SHIELD},
		{null, GearSlot.LEGS, null},
		{GearSlot.HANDS, GearSlot.FEET, GearSlot.RING},
	};

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ItemManager itemManager;
	@Inject
	private PluginManager pluginManager;
	@Inject
	private BankTagsService bankTagsService;
	@Inject
	private TagManager tagManager;
	@Inject
	private LayoutManager layoutManager;

	/** Item ids in the tab, real and canonical; read by the bank on the client thread. */
	private volatile Set<Integer> tagged = Collections.emptySet();
	/** Set when the tab should open as soon as the bank does. */
	private volatile boolean openOnBank;
	/** Run on the client thread when the bank button is clicked. */
	private volatile Runnable buttonAction = () ->
	{
	};
	/** Size the button sprites were drawn at, or null before the first bank. Client thread. */
	private Dimension buttonSize;

	void start(Runnable onButton)
	{
		buttonAction = onButton;
		tagManager.registerTag(TAG, itemId -> tagged.contains(itemId));
	}

	void stop()
	{
		openOnBank = false;
		buttonAction = () ->
		{
		};
		tagManager.unregisterTag(TAG);
		layoutManager.removeLayout(TAG);
		clientThread.invokeLater(() ->
		{
			if (TAG.equals(bankTagsService.getActiveTag()))
			{
				bankTagsService.closeBankTag();
			}
			Widget button = findButton();
			if (button != null)
			{
				button.setHidden(true);
			}
			client.getSpriteOverrides().remove(BUTTON_SPRITE);
			client.getSpriteOverrides().remove(BUTTON_HOVER_SPRITE);
			buttonSize = null;
		});
	}

	/**
	 * Builds the tab for a setup and opens it, now if the bank is open or else the next time it opens.
	 *
	 * @param onDone gets a short status line on the EDT
	 */
	void show(StyleResult result, AccountSnapshot account, Consumer<String> onDone)
	{
		Set<Integer> owned = account.ownedItemIds();
		int[] equipment = new int[EQUIPMENT.length * ROW];
		Arrays.fill(equipment, -1);
		Set<Integer> placed = new HashSet<>();
		for (int row = 0; row < EQUIPMENT.length; row++)
		{
			for (int column = 0; column < EQUIPMENT[row].length; column++)
			{
				GearSlot slot = EQUIPMENT[row][column];
				OwnedItem item = slot == null ? null : result.getLoadout().get(slot);
				if (item != null && owned.contains(item.getItemId()))
				{
					equipment[row * ROW + column] = item.getItemId();
					placed.add(item.getItemId());
				}
			}
		}

		Set<Integer> supplies = new LinkedHashSet<>();
		OwnedItem dart = result.getLoadout().getBlowpipeDart();
		if (dart != null && owned.contains(dart.getItemId()))
		{
			supplies.add(dart.getItemId());
		}
		for (InventoryPlan.Slot slot : InventoryPlan.build(result, account))
		{
			if (slot.isOwned() && slot.getItemId() >= 0 && !placed.contains(slot.getItemId()))
			{
				supplies.add(slot.getItemId());
			}
		}

		int rows = Math.max(EQUIPMENT.length, (supplies.size() + INVENTORY_WIDTH - 1) / INVENTORY_WIDTH);
		int[] cells = Arrays.copyOf(equipment, rows * ROW);
		Arrays.fill(cells, equipment.length, cells.length, -1);
		int index = 0;
		for (int itemId : supplies)
		{
			cells[(index / INVENTORY_WIDTH) * ROW + INVENTORY_COLUMN + index % INVENTORY_WIDTH] = itemId;
			index++;
		}
		placed.addAll(supplies);

		clientThread.invokeLater(() ->
		{
			if (!bankTagsActive())
			{
				SwingUtilities.invokeLater(() -> onDone.accept("Turn on the Bank Tags plugin to use this"));
				return;
			}
			Set<Integer> ids = new HashSet<>();
			for (int itemId : placed)
			{
				ids.add(itemId);
				ids.add(itemManager.canonicalize(itemId));
			}
			tagged = ids;
			layoutManager.saveLayout(new Layout(TAG, cells));
			boolean bankOpen = isBankOpen();
			if (bankOpen)
			{
				bankTagsService.openBankTag(TAG, 0);
			}
			openOnBank = !bankOpen;
			SwingUtilities.invokeLater(() -> onDone.accept(bankOpen ? "Shown in your bank" : "Opens with your bank"));
		});
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && openOnBank)
		{
			openOnBank = false;
			// After the bank and Bank Tags finish setting up the bank interface
			clientThread.invokeLater(() -> bankTagsService.openBankTag(TAG, 0));
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		// The bank rebuilds its interface often; put the button back whenever it's missing
		if (event.getScriptId() == ScriptID.BANKMAIN_BUILD && findButton() == null)
		{
			addButton();
		}
	}

	/**
	 * Adds the button just right of the bank's "?" button, the same size.
	 */
	private void addButton()
	{
		Widget help = client.getWidget(InterfaceID.Bankmain.BANK_TUT);
		if (help == null || help.getParent() == null || help.getWidth() <= 0)
		{
			return;
		}
		Dimension size = new Dimension(help.getWidth(), help.getHeight());
		if (!size.equals(buttonSize))
		{
			client.getSpriteOverrides().put(BUTTON_SPRITE, ImageUtil.getImageSpritePixels(buttonImage(size, false), client));
			client.getSpriteOverrides().put(BUTTON_HOVER_SPRITE, ImageUtil.getImageSpritePixels(buttonImage(size, true), client));
			buttonSize = size;
		}
		Widget button = help.getParent().createChild(-1, WidgetType.GRAPHIC);
		button.setSpriteId(BUTTON_SPRITE);
		button.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		button.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		button.setOriginalX(help.getRelativeX() + help.getWidth() + 3);
		button.setOriginalY(help.getRelativeY());
		button.setOriginalWidth(size.width);
		button.setOriginalHeight(size.height);
		button.setName("<col=ff9040>Gear Optimizer</col>");
		button.setAction(0, "Show setup");
		button.setHasListener(true);
		button.setOnOpListener((JavaScriptCallback) e -> buttonAction.run());
		button.setOnMouseOverListener((JavaScriptCallback) e -> button.setSpriteId(BUTTON_HOVER_SPRITE));
		button.setOnMouseLeaveListener((JavaScriptCallback) e -> button.setSpriteId(BUTTON_SPRITE));
		button.revalidate();
	}

	private Widget findButton()
	{
		Widget help = client.getWidget(InterfaceID.Bankmain.BANK_TUT);
		Widget[] children = help == null || help.getParent() == null ? null : help.getParent().getDynamicChildren();
		if (children == null)
		{
			return null;
		}
		for (Widget child : children)
		{
			if (child != null && (child.getSpriteId() == BUTTON_SPRITE || child.getSpriteId() == BUTTON_HOVER_SPRITE))
			{
				return child;
			}
		}
		return null;
	}

	/**
	 * A stone button like the bank's own, with the plugin's shield on it.
	 */
	private static BufferedImage buttonImage(Dimension size, boolean hover)
	{
		int w = size.width;
		int h = size.height;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(hover ? new Color(88, 76, 60) : new Color(66, 57, 45));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(102, 89, 70));
		g.drawLine(1, 1, w - 2, 1);
		g.drawLine(1, 1, 1, h - 2);
		g.setColor(new Color(38, 33, 26));
		g.drawLine(1, h - 2, w - 2, h - 2);
		g.drawLine(w - 2, 1, w - 2, h - 2);
		g.setColor(new Color(16, 14, 11));
		g.drawRect(0, 0, w - 1, h - 1);
		int icon = Math.max(8, Math.min(w, h) - 8);
		g.drawImage(OptimizerWindow.appIcon(icon), (w - icon) / 2, (h - icon) / 2, null);
		g.dispose();
		return image;
	}

	private boolean bankTagsActive()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin instanceof BankTagsPlugin)
			{
				return pluginManager.isPluginActive(plugin);
			}
		}
		return false;
	}

	private boolean isBankOpen()
	{
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		return items != null && !items.isHidden();
	}
}
