package com.gearoptimizer.ui;

import com.gearoptimizer.calc.MonsterRules;
import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.data.GearSlot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Full-width banners, one per line, for the custom setup, warnings and required protection. One per line so none of
 * them can wrap out of view.
 */
class NoticeStrip extends JPanel
{
	NoticeStrip(TabModel model, StyleTab.Actions actions)
	{
		super(new GridLayout(0, 1, 0, 4));
		setOpaque(false);
		StyleResult result = model.getResult();
		if (model.isEdited())
		{
			JButton reset = Ui.button("Back to recommended", "Undo every change on this tab", () -> actions.resetExperiment(result.getStyle()));
			add(banner(new Color(30, 50, 80), Ui.marked(Ui.Glyph.INFO, Ui.INFO, String.format("Custom setup: %+.2f DPS vs recommended%s",
				result.effectiveDps() - model.getRecommended().effectiveDps(), model.getExperimentNote() == null ? ""
					: ". " + model.getExperimentNote()), Color.WHITE), reset));
		}
		for (String warning : result.getWarnings())
		{
			boolean unsafe = warning.startsWith("UNSAFE");
			add(banner(unsafe ? new Color(90, 25, 25) : new Color(70, 55, 20), Ui.marked(unsafe ? Ui.Glyph.CROSS : Ui.Glyph.WARNING,
				unsafe ? Ui.BAD : Ui.WARN, unsafe ? warning.replace("UNSAFE: ", "Unsafe: ") : warning, Color.WHITE), null));
		}
		for (GearSlot slot : GearSlot.values())
		{
			String requirement = requirementText(actions.rules(), result, slot);
			if (requirement != null)
			{
				add(banner(new Color(25, 60, 65), Ui.marked(Ui.Glyph.LOCK, Ui.PROTECTION,
					"Required: " + requirement + ", " + slot.getDisplayName().toLowerCase() + " slot", Color.WHITE), null));
			}
		}
	}

	/**
	 * @return true if there's nothing to say
	 */
	boolean isEmpty()
	{
		return getComponentCount() == 0;
	}

	/**
	 * @return a description of the protection the monster requires in this slot, or null
	 */
	static String requirementText(MonsterRules rules, StyleResult result, GearSlot slot)
	{
		MonsterRules.SlotRequirement requirement = rules.requirementFor(slot);
		if (requirement == null)
		{
			return null;
		}
		boolean helmCounts = requirement.getPattern().pattern().contains("slayer helmet");
		return requirement.getLabel() + " against " + result.getContext().getMonster().getName().toLowerCase()
			+ "s" + (helmCounts ? " (a slayer helmet counts)" : "");
	}

	/**
	 * A full-width notice line. Text too long for the window is cut with an ellipsis and shown whole in the tooltip;
	 * the action button always stays visible on the right.
	 */
	private static JPanel banner(Color background, JLabel text, JButton action)
	{
		JPanel banner = new JPanel(new BorderLayout(8, 0));
		banner.setBackground(background);
		banner.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 6));
		text.setToolTipText(text.getText());
		text.setMinimumSize(new Dimension(0, text.getPreferredSize().height));
		banner.add(text, BorderLayout.CENTER);
		if (action != null)
		{
			banner.add(action, BorderLayout.EAST);
		}
		return banner;
	}
}
