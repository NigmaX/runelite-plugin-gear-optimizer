package com.gearoptimizer.ui;

import com.gearoptimizer.GearOptimizerConfig;
import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.calc.CombatStyle;
import com.gearoptimizer.calc.Consumables;
import com.gearoptimizer.calc.MonsterRules;
import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.calc.UpgradeFinder;
import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.OwnedItem;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import net.runelite.client.game.SpriteManager;

/**
 * One combat style's tab, laid out to fit without scrolling: stats across the top, then
 * equipment, inventory, and a switchable panel (slot options, supplies, special attack).
 */
class StyleTab extends JPanel
{
	interface Actions
	{
		void toggleExcluded(int itemId);

		void toggleLocked(int itemId);

		Set<Integer> locked();

		/** Opens the item chooser for a slot. */
		void chooseItem(CombatStyle style, GearSlot slot);

		/** Puts an item in a slot of the style's setup and re-calculates it. */
		void tryItem(CombatStyle style, GearSlot slot, OwnedItem item);

		List<OwnedItem> ownedItems();

		List<EquipmentItem> allItems();

		MonsterRules rules();

		/** @return the GE price, 0 if untradeable, or null if not looked up yet */
		Long price(int itemId);

		/**
		 * Looks up prices on the client thread, then runs the callback on the EDT once all of them are in, unless the
		 * owner has left the window by then.
		 */
		void requestPrices(Collection<Integer> itemIds, Component owner, Runnable onLoaded);

		/** Switches the setup to another ammo/dart/spell option. */
		void tryOption(CombatStyle style, Consumables.Option option);

		/** The calculation settings (prayer, potions, slayer task...). */
		GearOptimizerConfig settings();

		/** Changes a calculation setting; every tab recalculates. */
		void changeSetting(String key, Object value);

		/** Loads other darts into the blowpipe of the style's setup. */
		void tryDart(CombatStyle style, OwnedItem dart);

		/** Opens a bank tab holding only the shown setup's gear and supplies, then runs onDone. */
		void showInBank(StyleResult shown, Runnable onDone);

		/** Goes back to the recommended setup for every slot. */
		void resetExperiment(CombatStyle style);

		/** Remembers which slot the player is looking at, so rebuilds keep it. */
		void slotSelected(CombatStyle style, GearSlot slot);

		SpriteManager spriteManager();

		boolean isMaxMode();

		/** Turns the "Max DPS" view (best gear in the game) on or off. */
		void setMaxMode(boolean on);

		/** The account the setup was built from, for the inventory plan. */
		AccountSnapshot account();
	}

	static final String VIEW_SLOT = "Slot options";
	private static final String VIEW_SUPPLIES = "Supplies & budget";
	private static final String VIEW_SPEC = "Special attack";

	private final TabModel model;
	private final Actions actions;
	/** The right panel's view, shared by every tab. */
	private final TabState state;
	private final EquipmentPanel equipment;
	private final CardLayout rightCards = new CardLayout();
	private final JPanel rightContent = new JPanel(rightCards);
	/** Null in max DPS view, which shows a shopping list instead of the right panel. */
	private SlotOptionsPanel slotOptions;
	private Ui.Chips viewSwitcher;

	StyleTab(TabModel model, Actions actions, ItemIcons icons, TabState state)
	{
		this.model = model;
		this.actions = actions;
		this.state = state;
		setLayout(new BorderLayout(0, 8));
		setBackground(Ui.BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel(new BorderLayout(0, 6));
		top.setOpaque(false);
		top.add(new StatsHeader(model, new SettingsRow(model.getResult(), actions)), BorderLayout.NORTH);
		NoticeStrip notices = new NoticeStrip(model, actions);
		if (!notices.isEmpty())
		{
			top.add(notices, BorderLayout.CENTER);
		}
		add(top, BorderLayout.NORTH);

		// Equipment and inventory keep their natural, in-game proportions; the options panel takes the rest
		equipment = new EquipmentPanel(model, actions, icons, slot -> showSlot(slot, true));
		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
		left.setOpaque(false);
		left.add(fixed(equipment));
		left.add(Box.createHorizontalStrut(8));
		left.add(fixed(new InventoryPanel(model, actions, icons)));
		JPanel center = new JPanel(new BorderLayout(8, 0));
		center.setOpaque(false);
		center.add(verticalScroll(left), BorderLayout.WEST);
		center.add(isMaxView() ? new ShoppingListPanel(model, icons) : rightPanel(icons), BorderLayout.CENTER);
		add(center, BorderLayout.CENTER);

		if (!isMaxView())
		{
			showSlot(model.getInitialSlot() != null ? model.getInitialSlot() : biggestUpgradeSlot(), false);
		}
	}

	boolean isMaxView()
	{
		return model.isMaxView();
	}

	/**
	 * @return true if the tab shows a setup the player edited
	 */
	boolean isEdited()
	{
		return model.isEdited();
	}

	/** @return the setup this tab shows (the recommendation, the player's edit or the max DPS setup) */
	StyleResult getShown()
	{
		return model.getResult();
	}

	CombatStyle getStyle()
	{
		return model.getResult().getStyle();
	}

	/**
	 * Pins a card to its preferred size and to the top, so layouts never stretch it.
	 */
	private static JPanel fixed(JPanel card)
	{
		card.setAlignmentY(TOP_ALIGNMENT);
		card.setMaximumSize(card.getPreferredSize());
		return card;
	}

	/**
	 * Lets a column scroll up and down when the window is shorter than it. The scroll bar's width is reserved up
	 * front so it never squeezes the content when it appears.
	 */
	private static JScrollPane verticalScroll(JPanel content)
	{
		JScrollPane scroll = Ui.scroll(content, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, true);
		Dimension size = content.getPreferredSize();
		scroll.setPreferredSize(new Dimension(size.width + scroll.getVerticalScrollBar().getPreferredSize().width + 2, size.height));
		return scroll;
	}

	// ---- Right: switchable panel ----

	private JPanel rightPanel(ItemIcons icons)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setBackground(Ui.CARD);
		panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

		viewSwitcher = new Ui.Chips(new String[][]{{VIEW_SLOT, VIEW_SLOT}, {VIEW_SUPPLIES, VIEW_SUPPLIES}, {VIEW_SPEC, VIEW_SPEC}},
			state.getRightView(), this::showView);
		panel.add(viewSwitcher, BorderLayout.NORTH);

		slotOptions = new SlotOptionsPanel(model, actions, icons, state);
		rightContent.setOpaque(false);
		rightContent.add(slotOptions, VIEW_SLOT);
		rightContent.add(new SuppliesPanel(model.getResult(), actions), VIEW_SUPPLIES);
		rightContent.add(new SpecPanel(model.getResult(), icons), VIEW_SPEC);
		panel.add(rightContent, BorderLayout.CENTER);
		showView(state.getRightView());
		return panel;
	}

	private void showView(String view)
	{
		state.setRightView(view);
		rightCards.show(rightContent, view);
		viewSwitcher.select(view);
	}

	private GearSlot biggestUpgradeSlot()
	{
		GearSlot best = null;
		double gain = 0;
		for (GearSlot slot : GearSlot.values())
		{
			List<UpgradeFinder.Upgrade> list = model.getUpgrades().forSlot(getStyle(), slot);
			if (!list.isEmpty() && list.get(0).getDpsGain() > gain)
			{
				gain = list.get(0).getDpsGain();
				best = slot;
			}
		}
		return best;
	}

	/**
	 * Lists every option for the slot and marks it in the equipment grid.
	 *
	 * @param slot the slot, or null for the weapon
	 * @param focus switch the right panel to the slot view
	 */
	private void showSlot(GearSlot slot, boolean focus)
	{
		slotOptions.show(slot);
		GearSlot shown = slotOptions.getSelectedSlot();
		equipment.highlight(shown);
		if (focus)
		{
			showView(VIEW_SLOT);
			actions.slotSelected(getStyle(), shown);
		}
	}
}
