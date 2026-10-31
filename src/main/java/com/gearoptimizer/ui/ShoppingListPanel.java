package com.gearoptimizer.ui;

import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.OwnedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * In max DPS view, lists the items to buy with their GE prices and the total.
 */
class ShoppingListPanel extends JPanel
{
	private static final Color PRICE = new Color(255, 225, 140);

	ShoppingListPanel(TabModel model, ItemIcons icons)
	{
		super(new BorderLayout(0, 8));
		setBackground(Ui.CARD);
		setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		StyleResult result = model.getResult();
		StyleResult ownedBest = model.getOwnedBest();
		TabModel.Shopping shopping = model.getShopping();

		JPanel header = Ui.column();
		header.add(Ui.bold("Shopping list for max DPS", Ui.MAX_GOLD, 16f));
		double gain = result.effectiveDps() - ownedBest.effectiveDps();
		header.add(Ui.regular(shopping.getSlots().isEmpty() ? "You already own every piece of the max DPS setup."
			: String.format("%s to buy for %s, +%.2f DPS (%.0f%%) over your best setup", Ui.plural(shopping.getSlots().size(), "item"),
				Ui.gp(shopping.getTotal()), gain, ownedBest.effectiveDps() > 0 ? gain / ownedBest.effectiveDps() * 100 : 0), Color.WHITE));
		if (shopping.getUntradeable() > 0)
		{
			header.add(Ui.small(Ui.plural(shopping.getUntradeable(), "untradeable item") + " (not included in the total; earned in game)",
				Ui.WARN));
		}
		header.add(Ui.small((result.getContext().isOnSlayerTask() ? "Assumes you're on a slayer task (slayer helm bonus counted)"
			: "Assumes you're off task (no slayer helm bonus)") + ". Change it under Assumptions.", Ui.INFO));
		header.add(Ui.small("Best items in the game within your levels, tradeable or not. Turn off Max DPS to go back to your own gear.",
			Ui.MUTED));
		add(header, BorderLayout.NORTH);

		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setOpaque(false);
		for (GearSlot slot : shopping.getSlots())
		{
			OwnedItem item = result.getLoadout().get(slot);
			JPanel row = new JPanel(new BorderLayout(10, 0));
			row.setOpaque(false);
			row.setAlignmentX(LEFT_ALIGNMENT);
			row.setPreferredSize(new Dimension(100, SlotOptionsPanel.ROW_HEIGHT));
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, SlotOptionsPanel.ROW_HEIGHT));
			JLabel icon = ItemIcons.blank(SlotOptionsPanel.ROW_HEIGHT);
			icons.addTo(icon, item.getStats().getId());
			row.add(icon, BorderLayout.WEST);
			JPanel text = new JPanel(new BorderLayout(8, 0));
			text.setOpaque(false);
			JPanel name = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			name.setOpaque(false);
			name.add(Ui.small(slot.getDisplayName().toUpperCase(), Ui.MUTED));
			name.add(Ui.regular(item.getStats().getName(), Color.WHITE));
			text.add(name, BorderLayout.CENTER);
			long price = model.priceOf(item);
			JLabel priceLabel = Ui.regular(price > 0 ? Ui.exactGp(price) : "Untradeable", price > 0 ? PRICE : Ui.WARN);
			priceLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			text.add(priceLabel, BorderLayout.EAST);
			row.add(text, BorderLayout.CENTER);
			rows.add(row);
		}
		OwnedItem dart = result.getLoadout().getBlowpipeDart();
		if (dart != null && model.mustBuy(dart))
		{
			rows.add(Ui.small("Plus " + dart.getStats().getName() + " for the blowpipe (" + Ui.gp(model.priceOf(dart)) + " each)", Ui.MUTED));
		}
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(rows, BorderLayout.NORTH);
		add(holder, BorderLayout.CENTER);
	}
}
