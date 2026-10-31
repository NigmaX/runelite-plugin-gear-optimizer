package com.gearoptimizer.ui;

import com.gearoptimizer.calc.CombatStyle;
import com.gearoptimizer.calc.SpecialAttacks;
import com.gearoptimizer.calc.StyleResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import javax.swing.JPanel;

/**
 * The owned special attack weapons to swap to for this style, best damage per 100% energy first.
 */
class SpecPanel extends JPanel
{
	SpecPanel(StyleResult result, ItemIcons icons)
	{
		Ui.asColumn(this);
		if (result.getSpecOptions().isEmpty())
		{
			add(Ui.small(result.getStyle() == CombatStyle.MAGIC ? "Special attacks aren't ranked for magic setups."
				: "You don't own a special attack weapon for this style.", Ui.MUTED));
			return;
		}
		add(Ui.small("Swap to these for your special attack, best damage per 100% energy first:", Ui.MUTED));
		add(Ui.gap(4));
		for (SpecialAttacks.SpecOption option : result.getSpecOptions())
		{
			JPanel row = new JPanel(new BorderLayout(8, 0));
			row.setOpaque(false);
			row.setAlignmentX(LEFT_ALIGNMENT);
			row.add(icons.icon(option.getWeapon().getItemId()), BorderLayout.WEST);
			JPanel text = new JPanel(new GridLayout(0, 1));
			text.setOpaque(false);
			text.add(Ui.regular(option.getWeapon().getStats().getName(), Color.WHITE));
			text.add(Ui.small(String.format("~%.0f damage per spec, %d%% energy", option.getDamagePerSpec(), option.getEnergy())
				+ (option.getUtility() == null ? "" : ". " + option.getUtility()), Ui.GOOD));
			row.add(text, BorderLayout.CENTER);
			add(row);
			add(Ui.gap(4));
		}
	}
}
