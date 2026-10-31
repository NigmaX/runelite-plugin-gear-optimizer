package com.gearoptimizer.ui;

import com.gearoptimizer.calc.Consumables;
import com.gearoptimizer.calc.StyleResult;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * Running cost of the setup and a comparison of the owned ammo/dart/spell options by DPS and price.
 */
class SuppliesPanel extends JPanel
{
	/** Most options listed; the rest are weaker. */
	private static final int MAX_OPTIONS = 10;

	SuppliesPanel(StyleResult result, StyleTab.Actions actions)
	{
		Ui.asColumn(this);
		Consumables.Cost cost = result.getCost();
		add(Ui.regular(cost.getPerHour() > 0
			? "Supplies cost about " + Ui.gp(cost.getPerHour()) + " per hour of attacking."
			: "This setup uses no ammo, runes or charges.", Color.WHITE));
		if (!cost.getBreakdown().isEmpty())
		{
			add(Ui.small(String.join(",  ", cost.getBreakdown()), Ui.MUTED));
		}

		List<Consumables.Option> options = result.getConsumableOptions();
		if (options.size() < 2)
		{
			add(Ui.gap(6));
			add(Ui.small("No other ammo, darts or spells you own fit this weapon.", Ui.MUTED));
			return;
		}
		Consumables.Option budget = Consumables.budgetPick(options);
		if (budget != null && !budget.isCurrent())
		{
			add(Ui.gap(4));
			add(Ui.marked(Ui.Glyph.INFO, Ui.INFO, String.format("Budget pick: %s saves %s/hr for %+.2f DPS", budget.getLabel(),
				Ui.gp(cost.getPerHour() - budget.getCost().getPerHour()), budget.getDps().getDps() - result.getDps().getDps()), Color.WHITE));
		}
		add(Ui.gap(6));
		JPanel table = new JPanel(new GridLayout(0, 6, 8, 2));
		table.setOpaque(false);
		table.setAlignmentX(LEFT_ALIGNMENT);
		for (String header : new String[]{"Option", "DPS", "Max hit", "Cost / hour", "Cost / 1k dmg", ""})
		{
			table.add(Ui.small(header, Ui.MUTED));
		}
		int shown = 0;
		for (Consumables.Option option : options)
		{
			if (shown++ >= MAX_OPTIONS)
			{
				break;
			}
			Color color = option.isCurrent() ? ColorScheme.BRAND_ORANGE : option == budget ? Ui.GOOD : Color.WHITE;
			JLabel optionLabel = Ui.small(option.getLabel() + (option.isCurrent() ? " (in use)" : option == budget ? " (budget)" : ""), color);
			int sprite = SpellIcons.sprite(option.getLoadout().getSpell());
			if (sprite >= 0)
			{
				actions.spriteManager().addSpriteTo(optionLabel, sprite, 0);
			}
			table.add(optionLabel);
			table.add(Ui.small(String.format("%.2f", option.getDps().getDps()), color));
			table.add(Ui.small(String.valueOf(option.getDps().getMaxHit()), Color.WHITE));
			table.add(Ui.small(Ui.gp(option.getCost().getPerHour()), Color.WHITE));
			table.add(Ui.small(Ui.gp(option.getCost().perThousandDamage(option.getDps())), Ui.MUTED));
			if (option.isCurrent())
			{
				table.add(Ui.small("", Ui.MUTED));
			}
			else
			{
				table.add(Ui.button("Try", null, () -> actions.tryOption(result.getStyle(), option)));
			}
		}
		add(table);
		add(Ui.gap(4));
		add(Ui.small("GE guide prices. Ammo loss counts your Ava's device or quiver if worn.", Ui.MUTED));
	}
}
