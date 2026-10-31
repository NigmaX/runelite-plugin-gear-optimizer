package com.gearoptimizer.ui;

import com.gearoptimizer.GearOptimizerConfig;
import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.calc.Consumables;
import com.gearoptimizer.calc.InventoryPlan;
import com.gearoptimizer.calc.MonsterRules;
import com.gearoptimizer.calc.PotionBoost;
import com.gearoptimizer.calc.StyleResult;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * The inventory column: what to bring, drawn like the in-game inventory, the potion and budget settings that decide
 * it, a button to lay the setup out in the bank, and a checklist of the items the monster calls for.
 */
class InventoryPanel extends JPanel
{
	private final StyleResult result;
	private final StyleTab.Actions actions;
	private final ItemIcons icons;

	InventoryPanel(TabModel model, StyleTab.Actions actions, ItemIcons icons)
	{
		this.result = model.getResult();
		this.actions = actions;
		this.icons = icons;
		Ui.asCard(this, "Bring with you");
		JPanel inventory = inventoryGrid(InventoryPlan.build(result, actions.account()));
		inventory.setMaximumSize(inventory.getPreferredSize());
		add(inventory);
		add(Ui.gap(6));

		// What goes in the inventory: potions (which also raise the max hit) and how much ammo/runes may cost
		int width = inventory.getPreferredSize().width;
		GearOptimizerConfig settings = actions.settings();
		add(Ui.small("Potions", Ui.MUTED));
		JPanel potions = Ui.segmented(new String[][]{
			{"NONE", "Off", "No potion boost"},
			{"OWNED", "Bank", "Drink the best combat potion in your bank; it raises your levels and max hit"},
			{"STRONGEST", "Best", "Drink the strongest combat potion, even if you don't own it"},
		}, settings.potionChoice(), value -> actions.changeSetting("potionChoice", value));
		potions.setMaximumSize(new Dimension(width, potions.getPreferredSize().height));
		add(potions);
		PotionBoost potion = result.getContext().getPotion();
		if (potion != PotionBoost.NONE)
		{
			add(Ui.small(potion.getDisplayName(), Ui.INFO));
		}
		else if ("OWNED".equals(settings.potionChoice()))
		{
			add(Ui.small("None in your bank", Ui.MUTED));
		}
		add(Ui.gap(4));
		if (!model.isMaxView())
		{
			JToggleButton budget = Ui.toggle("Budget supplies", settings.budgetMode(),
				"Use the cheapest ammo and runes that keep at least 90% of the best DPS",
				on -> actions.changeSetting("budgetMode", on));
			budget.setAlignmentX(LEFT_ALIGNMENT);
			budget.setMaximumSize(new Dimension(width, budget.getPreferredSize().height));
			add(budget);
			add(Ui.gap(4));

			JButton bank = Ui.button("Show in bank", "<html>Opens a bank tab with only this setup: the gear laid out like your equipment<br>"
				+ "on the left, the supplies like your inventory on the right. Uses the Bank Tags plugin.</html>", null);
			bank.setAlignmentX(LEFT_ALIGNMENT);
			bank.setMaximumSize(new Dimension(width, bank.getPreferredSize().height));
			bank.addActionListener(e ->
			{
				bank.setEnabled(false);
				actions.showInBank(result, () -> bank.setEnabled(true));
			});
			add(bank);
			add(Ui.gap(6));
		}
		checklist();
	}

	/**
	 * Lists the items the monster calls for that are missing or required; owned optional items share one line.
	 */
	private void checklist()
	{
		List<String> fine = new ArrayList<>();
		for (MonsterRules.CheckedItem item : result.getInventory())
		{
			if (item.isOwned() && !item.isMandatory())
			{
				fine.add(item.getLabel().replaceAll(" \\(.*\\)", ""));
				continue;
			}
			Ui.Glyph glyph = item.isOwned() ? Ui.Glyph.CHECK : item.isMandatory() ? Ui.Glyph.CROSS : Ui.Glyph.WARNING;
			Color color = item.isOwned() ? Ui.GOOD : item.isMandatory() ? Ui.BAD : Ui.WARN;
			JLabel label = Ui.markedWrapped(glyph, color, item.getLabel(), Color.WHITE, EquipmentPanel.TEXT_WIDTH - 18);
			label.setToolTipText(item.getDetail());
			add(label);
		}
		if (!fine.isEmpty())
		{
			add(Ui.markedWrapped(Ui.Glyph.CHECK, Ui.GOOD, String.join(", ", fine), Ui.MUTED, EquipmentPanel.TEXT_WIDTH - 18));
		}
	}

	/**
	 * Draws the planned inventory like the in-game 4x7 inventory.
	 */
	private JPanel inventoryGrid(List<InventoryPlan.Slot> plan)
	{
		JPanel grid = new JPanel(new GridLayout(7, 4, 1, 1));
		grid.setBackground(new Color(62, 53, 41));
		grid.setAlignmentX(LEFT_ALIGNMENT);
		grid.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(40, 34, 26), 2), BorderFactory.createEmptyBorder(3, 3, 3, 3)));
		for (InventoryPlan.Slot slot : plan)
		{
			JLabel cell = ItemIcons.blank(32);
			if (slot.isOwned())
			{
				icons.addTo(cell, slot.getItemId(), Math.max(1, slot.getQuantity()), slot.getQuantity() > 1);
				boolean pouch = slot.getName() != null && slot.getName().toLowerCase().contains("rune pouch");
				cell.setToolTipText(pouch ? runePouchTooltip(slot.getName())
					: slot.getName() + (slot.getQuantity() > 1 ? " x" + slot.getQuantity() : ""));
			}
			else
			{
				cell.setOpaque(true);
				cell.setBackground(new Color(110, 35, 35));
				cell.setForeground(Color.WHITE);
				cell.setFont(FontManager.getRunescapeSmallFont());
				cell.setText("?");
				cell.setToolTipText("Missing: " + slot.getName() + (slot.isMandatory() ? " (required)" : ""));
			}
			grid.add(cell);
		}
		return grid;
	}

	/**
	 * Tooltip for the rune pouch: what to fill it with for the spell, and what it holds now.
	 */
	private String runePouchTooltip(String name)
	{
		StringBuilder text = new StringBuilder("<html><b>").append(Ui.escape(name)).append("</b>");
		Map<String, Integer> runes = Consumables.runesToCarry(result.getLoadout());
		if (!runes.isEmpty())
		{
			List<String> needed = new ArrayList<>();
			runes.forEach((rune, count) -> needed.add(Ui.plural(count, rune + " rune")));
			text.append("<br>Fill with ").append(String.join(", ", needed)).append(" per cast");
		}
		AccountSnapshot account = actions.account();
		Map<Integer, Integer> pouch = account == null ? null : account.getRunePouch();
		if (pouch != null && !pouch.isEmpty())
		{
			List<String> held = new ArrayList<>();
			pouch.forEach((id, quantity) ->
			{
				String rune = account.nameOf(id);
				if (rune != null)
				{
					held.add(rune + " x" + QuantityFormatter.quantityToStackSize(quantity));
				}
			});
			if (!held.isEmpty())
			{
				text.append("<br>").append(Ui.colored("Holds now: " + Ui.escape(String.join(", ", held)), Ui.MUTED));
			}
		}
		return text.append("</html>").toString();
	}
}
