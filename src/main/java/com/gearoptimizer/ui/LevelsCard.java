package com.gearoptimizer.ui;

import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.calc.CombatStyle;
import com.gearoptimizer.calc.StyleResult;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.Map;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;

/**
 * The sidebar card with the player's combat levels, boosted by the potion each style assumes.
 */
final class LevelsCard
{
	@Getter
	private final JPanel panel = Ui.card(null);
	private final Function<Skill, Icon> skillIcon;
	/** Width text wraps at. */
	private final int width;

	LevelsCard(Function<Skill, Icon> skillIcon, int width)
	{
		this.skillIcon = skillIcon;
		this.width = width;
	}

	void show(OptimizerView view)
	{
		panel.removeAll();
		panel.add(Ui.bold("Your stats", ColorScheme.LIGHT_GRAY_COLOR));
		panel.add(Ui.gap(4));
		AccountSnapshot account = view.getAccount();
		if (account.getLevels().isEmpty())
		{
			panel.add(Ui.wrapped("Log in once to sync your levels. Until then everything uses level 1.", Ui.BAD, width));
			panel.revalidate();
			return;
		}
		Map<CombatStyle, StyleResult> styles = view.getResult().getStyles();
		JPanel grid = new JPanel(new GridLayout(2, 3, 6, 4));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.add(level(styles.get(CombatStyle.MELEE), Skill.ATTACK, account));
		grid.add(level(styles.get(CombatStyle.MELEE), Skill.STRENGTH, account));
		grid.add(level(null, Skill.DEFENCE, account));
		grid.add(level(styles.get(CombatStyle.RANGED), Skill.RANGED, account));
		grid.add(level(styles.get(CombatStyle.MAGIC), Skill.MAGIC, account));
		grid.add(level(null, Skill.PRAYER, account));
		StringBuilder tip = new StringBuilder("<html>Max hit uses these levels plus the potion boost, then prayer, stance and gear.");
		for (StyleResult style : styles.values())
		{
			tip.append("<br>").append(style.getStyle().getDisplayName()).append(": ").append(style.getContext().getPrayer().getDisplayName())
				.append(", ").append(style.getContext().getPotion().getDisplayName().toLowerCase());
		}
		grid.setToolTipText(tip.append("</html>").toString());
		panel.add(grid);
		Boolean ancients = account.getAncientMagicks();
		if (ancients != null && !ancients)
		{
			panel.add(Ui.gap(4));
			panel.add(Ui.small("Ancient Magicks not unlocked", Ui.WARN));
		}
		panel.revalidate();
		panel.repaint();
	}

	/**
	 * @param style the setup whose potion boosts the level, or null for the base level
	 */
	private JLabel level(StyleResult style, Skill skill, AccountSnapshot account)
	{
		int base = account.level(skill);
		int boosted = style == null ? base : style.getContext().boostedLevel(skill);
		JLabel label = Ui.small(boosted > base ? base + " (" + boosted + ")" : String.valueOf(base), boosted > base ? Ui.GOOD : Color.WHITE);
		label.setIcon(skillIcon.apply(skill));
		label.setIconTextGap(5);
		label.setToolTipText(skill.getName() + (boosted > base ? ": " + base + ", boosted to " + boosted : ": " + base));
		return label;
	}
}
