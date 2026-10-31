package com.gearoptimizer.ui;

import com.gearoptimizer.calc.CombatStyle;
import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.Monster;
import com.gearoptimizer.data.OwnedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.ToDoubleFunction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;

/**
 * Visual comparison of every setup: a verdict, setup cards, metric bar charts and the swaps to make.
 */
class CompareTab extends JPanel
{
	private static final Color MELEE = new Color(215, 95, 80);
	private static final Color RANGED = new Color(100, 185, 95);
	private static final Color MAGIC = new Color(95, 145, 225);
	private static final Color WORN = new Color(150, 150, 150);

	/** A setup in the comparison: one per style, plus the gear worn now. */
	private static final class Entry
	{
		final String name;
		final StyleResult result;
		final Color color;
		final boolean worn;

		Entry(String name, StyleResult result, Color color, boolean worn)
		{
			this.name = name;
			this.result = result;
			this.color = color;
			this.worn = worn;
		}

		double killsPerHour()
		{
			double ttk = result.timeToKill();
			return ttk <= 0 || Double.isInfinite(ttk) ? 0 : 3600 / ttk;
		}
	}

	private final ItemIcons icons;
	private final Consumer<CombatStyle> openStyle;

	CompareTab(Monster monster, List<StyleResult> setups, StyleResult best, StyleResult worn, ItemIcons icons,
		Consumer<CombatStyle> openStyle)
	{
		this.icons = icons;
		this.openStyle = openStyle;
		setLayout(new BorderLayout());
		setBackground(Ui.BACKGROUND);

		List<Entry> entries = new ArrayList<>();
		for (StyleResult setup : setups)
		{
			entries.add(new Entry(setup.getStyle().getDisplayName(), setup, colorOf(setup.getStyle()), false));
		}
		entries.sort((a, b) -> Double.compare(b.result.effectiveDps(), a.result.effectiveDps()));
		if (worn != null)
		{
			entries.add(new Entry("Worn now", worn, WORN, true));
		}

		JPanel column = new Ui.WidthTrackingPanel(new GridBagLayout());
		column.setBackground(Ui.BACKGROUND);
		column.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTH;
		c.insets = new Insets(0, 0, 10, 0);

		if (entries.isEmpty())
		{
			column.add(Ui.regular("Nothing to compare yet.", Ui.MUTED), c);
		}
		else
		{
			Entry top = entries.get(0);
			column.add(verdict(monster, top, entries, worn), c);
			column.add(cards(entries, top), c);
			column.add(charts(entries), c);
			if (worn != null && best != null)
			{
				column.add(changes(worn, entries.get(0).result), c);
			}
		}
		c.weighty = 1;
		JPanel filler = new JPanel();
		filler.setOpaque(false);
		column.add(filler, c);

		add(Ui.scroll(column, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED, false), BorderLayout.CENTER);
	}

	static Color colorOf(CombatStyle style)
	{
		switch (style)
		{
			case MELEE:
				return MELEE;
			case RANGED:
				return RANGED;
			default:
				return MAGIC;
		}
	}

	// ---- Verdict ----

	private JPanel verdict(Monster monster, Entry top, List<Entry> entries, StyleResult worn)
	{
		JPanel card = Ui.card(null);
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, top.color),
			BorderFactory.createEmptyBorder(10, 12, 10, 12)));
		StyleResult best = top.result;
		card.add(Ui.small("Verdict against " + monster.getDisplayName(), Ui.MUTED));
		card.add(Ui.gap(2));
		card.add(Ui.bold(String.format("Use %s: %.2f DPS, about %s per kill (%.0f kills/hr)", top.name.toLowerCase(),
			best.effectiveDps(), Ui.formatSeconds(best.timeToKill()), top.killsPerHour()), Color.WHITE, 18f));
		card.add(Ui.gap(4));

		List<String> lines = new ArrayList<>();
		if (worn != null)
		{
			double gain = best.effectiveDps() - worn.effectiveDps();
			double pct = worn.effectiveDps() > 0 ? gain / worn.effectiveDps() * 100 : 0;
			lines.add(Math.abs(gain) < Ui.DPS_EPSILON ? "That's what you're wearing already."
				: String.format("%s%.2f DPS (%+.0f%%) compared to what you're wearing now.", gain > 0 ? "+" : "", gain, pct));
		}
		for (Entry other : entries)
		{
			if (other == top || other.worn)
			{
				continue;
			}
			double slower = (1 - other.result.effectiveDps() / best.effectiveDps()) * 100;
			String line = String.format("%s kills %.0f%% slower", other.name, slower);
			int defence = other.result.getDefence() - best.getDefence();
			if (defence > 0 && best.getDefence() > 0)
			{
				line += String.format(" but has %.0f%% more defence against its attacks", defence * 100.0 / best.getDefence());
			}
			double cost = other.result.getCost().getPerHour() - best.getCost().getPerHour();
			if (cost < -1000)
			{
				line += ", and saves " + Ui.gp(-cost) + "/hr in supplies";
			}
			lines.add(line + ".");
		}
		if (best.getCost().getPerHour() > 0)
		{
			lines.add("Supplies for the best setup cost about " + Ui.gp(best.getCost().getPerHour()) + " per hour.");
		}
		for (String line : lines)
		{
			card.add(Ui.marked(Ui.Glyph.DOT, top.color, line, ColorScheme.LIGHT_GRAY_COLOR));
		}
		for (String warning : best.getWarnings())
		{
			if (warning.startsWith("UNSAFE"))
			{
				card.add(Ui.marked(Ui.Glyph.CROSS, Ui.BAD, warning, Ui.BAD));
			}
		}
		return card;
	}

	// ---- Setup cards ----

	private JPanel cards(List<Entry> entries, Entry top)
	{
		JPanel row = new JPanel(new GridLayout(1, 0, 10, 0));
		row.setOpaque(false);
		for (Entry entry : entries)
		{
			row.add(card(entry, entry == top));
		}
		return row;
	}

	private JPanel card(Entry entry, boolean best)
	{
		StyleResult result = entry.result;
		JPanel card = Ui.card(null);
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(4, 0, 0, 0, entry.color),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)));

		JPanel title = new JPanel(new BorderLayout());
		title.setOpaque(false);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		title.add(Ui.bold(entry.name + (entry.worn ? " (" + result.getStyle().getDisplayName().toLowerCase() + ")" : ""), entry.color),
			BorderLayout.WEST);
		if (best)
		{
			title.add(Ui.pill("BEST", ColorScheme.BRAND_ORANGE, Color.BLACK), BorderLayout.EAST);
		}
		else if (hasUnsafe(result))
		{
			title.add(Ui.pill("UNSAFE", Ui.BAD, Color.BLACK), BorderLayout.EAST);
		}
		card.add(title);

		JPanel weapon = new JPanel(new BorderLayout(6, 0));
		weapon.setOpaque(false);
		weapon.setAlignmentX(Component.LEFT_ALIGNMENT);
		OwnedItem weaponItem = result.getLoadout().get(GearSlot.WEAPON);
		weapon.add(weaponItem == null ? ItemIcons.blank(32) : icons.icon(weaponItem.getItemId()), BorderLayout.WEST);
		weapon.add(Ui.small(weaponItem == null ? "Unarmed" : weaponItem.getStats().getName(), Color.WHITE), BorderLayout.CENTER);
		card.add(weapon);
		card.add(Ui.gap(4));

		card.add(Ui.bold(String.format("%.2f", result.effectiveDps()), Color.WHITE, 28f));
		int targets = result.targetsHit();
		card.add(Ui.small(targets > 1 ? String.format("DPS total, %d monsters (%.2f each)", targets, result.effectiveDps() / targets) : "DPS",
			Ui.MUTED));
		card.add(Ui.gap(6));
		card.add(line("Time to kill", Ui.formatSeconds(result.timeToKill())));
		card.add(line("Kills per hour", String.format("%.0f", entry.killsPerHour())));
		card.add(line("Max hit / accuracy", result.getDps().getMaxHit() + " / " + String.format("%.0f%%", result.getDps().getAccuracy() * 100)));
		card.add(line("Defence", String.valueOf(result.getDefence())));
		card.add(line("Supplies", result.getCost().getPerHour() > 0 ? Ui.gp(result.getCost().getPerHour()) + "/hr" : "free"));
		card.add(Ui.gap(6));
		card.add(gearStrip(result));

		if (!entry.worn)
		{
			card.add(Ui.gap(6));
			JButton open = Ui.button("View setup", null, () -> openStyle.accept(result.getStyle()));
			open.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(open);
		}
		return card;
	}

	private static boolean hasUnsafe(StyleResult result)
	{
		for (String warning : result.getWarnings())
		{
			if (warning.startsWith("UNSAFE"))
			{
				return true;
			}
		}
		return false;
	}

	private static JPanel line(String name, String value)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(Ui.small(name, Ui.MUTED), BorderLayout.WEST);
		JLabel valueLabel = Ui.small(value, Color.WHITE);
		valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(valueLabel, BorderLayout.EAST);
		return row;
	}

	private JPanel gearStrip(StyleResult result)
	{
		JPanel strip = new JPanel(new GridLayout(0, 6, 2, 2));
		strip.setOpaque(false);
		strip.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (GearSlot slot : GearSlot.values())
		{
			OwnedItem item = result.getLoadout().get(slot);
			strip.add(icons.tile(item, slot.getDisplayName() + ": " + (item == null ? "empty" : item.getStats().getName())));
		}
		return strip;
	}

	// ---- Charts ----

	private JPanel charts(List<Entry> entries)
	{
		JPanel card = Ui.card("Side by side");
		JPanel grid = new JPanel(new GridLayout(0, 2, 16, 10));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.add(chart("Damage per second", entries, e -> e.result.effectiveDps(), v -> String.format("%.2f", v), true));
		grid.add(chart("Kills per hour", entries, Entry::killsPerHour, v -> String.format("%.0f", v), true));
		grid.add(chart("Max hit", entries, e -> e.result.getDps().getMaxHit(), v -> String.format("%.0f", v), true));
		grid.add(chart("Accuracy", entries, e -> e.result.getDps().getAccuracy() * 100, v -> String.format("%.1f%%", v), true));
		grid.add(chart("Defence against its attacks", entries, e -> e.result.getDefence(), v -> String.format("%.0f", v), true));
		grid.add(chart("Supply cost per hour (lower is better)", entries, e -> e.result.getCost().getPerHour(), Ui::gp, false));
		card.add(grid);
		return card;
	}

	private JPanel chart(String title, List<Entry> entries, ToDoubleFunction<Entry> metric, DoubleFunction<String> format,
		boolean higherIsBetter)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		panel.add(Ui.small(title, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.NORTH);

		double max = 0;
		double bestValue = higherIsBetter ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
		for (Entry entry : entries)
		{
			double value = metric.applyAsDouble(entry);
			max = Math.max(max, value);
			if (!entry.worn)
			{
				bestValue = higherIsBetter ? Math.max(bestValue, value) : Math.min(bestValue, value);
			}
		}

		JPanel rows = new JPanel(new GridLayout(0, 1, 0, 3));
		rows.setOpaque(false);
		for (Entry entry : entries)
		{
			double value = metric.applyAsDouble(entry);
			JPanel row = new JPanel(new BorderLayout(8, 0));
			row.setOpaque(false);
			JLabel name = Ui.small(entry.name, entry.worn ? Ui.MUTED : Color.WHITE);
			name.setPreferredSize(new Dimension(80, 16));
			row.add(name, BorderLayout.WEST);
			row.add(new Bar(max <= 0 ? 0 : value / max, entry.color, entry.worn), BorderLayout.CENTER);
			boolean winner = !entry.worn && value == bestValue && entries.size() > 1;
			JLabel valueLabel = Ui.small(format.apply(value) + (winner ? "  *" : ""), winner ? ColorScheme.BRAND_ORANGE : Color.WHITE);
			valueLabel.setPreferredSize(new Dimension(90, 16));
			row.add(valueLabel, BorderLayout.EAST);
			rows.add(row);
		}
		panel.add(rows, BorderLayout.CENTER);
		return panel;
	}

	/** A horizontal bar filled to a fraction of its width. */
	private static final class Bar extends JComponent
	{
		private final double fraction;
		private final Color color;
		private final boolean muted;

		Bar(double fraction, Color color, boolean muted)
		{
			this.fraction = Math.max(0, Math.min(1, fraction));
			this.color = color;
			this.muted = muted;
			setPreferredSize(new Dimension(100, 14));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int height = 10;
			int y = (getHeight() - height) / 2;
			g.setColor(Ui.BACKGROUND);
			g.fillRoundRect(0, y, getWidth(), height, 6, 6);
			g.setColor(muted ? color.darker() : color);
			g.fillRoundRect(0, y, (int) Math.round(getWidth() * fraction), height, 6, 6);
			g.dispose();
		}
	}

	// ---- What to change ----

	private JPanel changes(StyleResult worn, StyleResult best)
	{
		JPanel card = Ui.card("What to change from your worn gear to the best setup");
		int changed = 0;
		JPanel grid = new JPanel(new GridLayout(0, 2, 16, 4));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (GearSlot slot : GearSlot.values())
		{
			OwnedItem from = worn.getLoadout().get(slot);
			OwnedItem to = best.getLoadout().get(slot);
			int fromId = from == null ? -1 : from.getStats().getId();
			int toId = to == null ? -1 : to.getStats().getId();
			if (fromId == toId)
			{
				continue;
			}
			changed++;
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			row.setOpaque(false);
			JLabel slotLabel = Ui.small(slot.getDisplayName(), Ui.MUTED);
			slotLabel.setPreferredSize(new Dimension(52, 16));
			row.add(slotLabel);
			row.add(icons.tile(from, from == null ? "Empty" : from.getStats().getName()));
			row.add(Ui.small("->", Ui.MUTED));
			row.add(icons.tile(to, to == null ? "Empty" : to.getStats().getName()));
			row.add(Ui.small(to == null ? "Take it off" : to.getStats().getName(), Color.WHITE));
			grid.add(row);
		}
		if (changed == 0)
		{
			card.add(Ui.regular("Nothing: you're already wearing the best setup.", Ui.GOOD));
		}
		else
		{
			card.add(grid);
		}
		return card;
	}
}
