package com.gearoptimizer.ui;

import com.gearoptimizer.data.EquipmentItem;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * Shared colours, label factories and small drawn icons. The RuneScape fonts lack most symbol
 * glyphs, so status marks are drawn rather than typed.
 */
final class Ui
{
	static final Color GOOD = new Color(110, 200, 110);
	static final Color BAD = new Color(230, 90, 80);
	static final Color WARN = new Color(240, 170, 60);
	static final Color INFO = new Color(110, 160, 230);
	static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();
	static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
	/** The "Max DPS" view: best gear in the game and what it costs. */
	static final Color MAX_GOLD = new Color(255, 200, 60);
	/** Protective gear the monster requires. */
	static final Color PROTECTION = new Color(90, 200, 200);
	/** DPS changes smaller than this count as no change. */
	static final double DPS_EPSILON = 0.005;

	/**
	 * A panel that always matches its scroll pane's width, so labels wrap instead of being clipped.
	 */
	static class WidthTrackingPanel extends JPanel implements Scrollable
	{
		WidthTrackingPanel(LayoutManager layout)
		{
			super(layout);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	enum Glyph
	{
		CHECK, CROSS, WARNING, INFO, DOT, ARROW_UP, ARROW_DOWN, LOCK
	}

	private Ui()
	{
	}

	static JLabel small(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	static JLabel regular(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeFont());
		label.setForeground(color);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	static JLabel bold(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(color);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * A bold label at a custom size, for headlines and the numbers that matter most.
	 */
	static JLabel bold(String text, Color color, float size)
	{
		JLabel label = bold(text, color);
		label.setFont(label.getFont().deriveFont(size));
		return label;
	}

	static JLabel marked(Glyph glyph, Color color, String text, Color textColor)
	{
		JLabel label = regular(text, textColor);
		label.setIcon(icon(glyph, color));
		label.setIconTextGap(6);
		return label;
	}

	/**
	 * A small marked label whose text wraps to the given width, for list items.
	 */
	static JLabel markedWrapped(Glyph glyph, Color color, String text, Color textColor, int width)
	{
		JLabel label = marked(glyph, color, "", textColor);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setText("<html><div style='width:" + width + "px'>" + escape(text) + "</div></html>");
		return label;
	}

	static JPanel card(String title)
	{
		return asCard(new JPanel(), title);
	}

	/**
	 * Lays a panel out as a card: a padded column on the card background, under an optional title.
	 */
	static <T extends JPanel> T asCard(T card, String title)
	{
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(CARD);
		card.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (title != null)
		{
			card.add(bold(title, ColorScheme.LIGHT_GRAY_COLOR));
			card.add(gap(4));
		}
		return card;
	}

	static JPanel column()
	{
		return asColumn(new JPanel());
	}

	/**
	 * Lays a panel out as a see-through column.
	 */
	static <T extends JPanel> T asColumn(T panel)
	{
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	static JPanel gap(int size)
	{
		JPanel gap = new JPanel();
		gap.setOpaque(false);
		gap.setPreferredSize(new Dimension(size, size));
		gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, size));
		gap.setAlignmentX(Component.LEFT_ALIGNMENT);
		return gap;
	}

	/**
	 * A label that wraps to the given width.
	 */
	static JLabel wrapped(String text, Color color, int width)
	{
		return small("<html><div style='width:" + width + "px'>" + escape(text) + "</div></html>", color);
	}

	static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	static String formatSeconds(double seconds)
	{
		if (Double.isInfinite(seconds) || Double.isNaN(seconds))
		{
			return "-";
		}
		if (seconds < 60)
		{
			return String.format("%.0fs", seconds);
		}
		return String.format("%dm %02ds", (int) seconds / 60, (int) seconds % 60);
	}

	private static String hex(Color color)
	{
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	/**
	 * @param html text already escaped for HTML
	 * @return the text in a colour, for HTML labels and tooltips
	 */
	static String colored(String html, Color color)
	{
		return "<font color='" + hex(color) + "'>" + html + "</font>";
	}

	/**
	 * @return e.g. "1 item" or "3 items"
	 */
	static String plural(int count, String noun)
	{
		return count + " " + noun + (count == 1 ? "" : "s");
	}

	/**
	 * @return an amount of coins such as "1.2M gp"; anything under 1 gp shows as "0 gp"
	 */
	static String gp(double amount)
	{
		return amount < 1 ? "0 gp" : QuantityFormatter.quantityToStackSize((long) amount) + " gp";
	}

	/**
	 * @return the exact amount of coins such as "1,234,567 gp", for prices to pay
	 */
	static String exactGp(long amount)
	{
		return QuantityFormatter.formatNumber(amount) + " gp";
	}

	/**
	 * @param price a GE price, 0 if untradeable, or null while it is being looked up
	 * @param zeroText what to show for an untradeable item
	 */
	static String priceText(Long price, String zeroText)
	{
		return price == null ? "..." : price > 0 ? QuantityFormatter.quantityToStackSize(price) + " gp" : zeroText;
	}

	/**
	 * @return the colour for a DPS change: better, worse, or the same when within {@link #DPS_EPSILON}
	 */
	static Color deltaColor(double delta, Color better, Color worse, Color same)
	{
		return delta > DPS_EPSILON ? better : delta < -DPS_EPSILON ? worse : same;
	}

	static Color deltaColor(double delta)
	{
		return deltaColor(delta, GOOD, BAD, MUTED);
	}

	/**
	 * Describes an item's combat bonuses: attack, strength and magic damage on one line for lists (compact), or every
	 * bonus over three HTML lines for tooltips.
	 */
	static String itemStats(EquipmentItem stats, boolean compact)
	{
		EquipmentItem.Attack off = stats.getOffensive();
		EquipmentItem.Bonuses bonus = stats.getBonuses();
		if (compact)
		{
			return String.format("atk %d/%d/%d mag %d rng %d  ·  str %d rstr %d mdmg %.1f%%", off.getStab(), off.getSlash(),
				off.getCrush(), off.getMagic(), off.getRanged(), bonus.getStr(), bonus.getRangedStr(), bonus.getMagicStr() / 10.0);
		}
		EquipmentItem.Attack def = stats.getDefensive();
		return String.format("Attack: stab %d, slash %d, crush %d, magic %d, ranged %d",
			off.getStab(), off.getSlash(), off.getCrush(), off.getMagic(), off.getRanged())
			+ String.format("<br>Defence: stab %d, slash %d, crush %d, magic %d, ranged %d",
			def.getStab(), def.getSlash(), def.getCrush(), def.getMagic(), def.getRanged())
			+ String.format("<br>Strength %d, ranged str %d, magic dmg %.1f%%, prayer %d", bonus.getStr(), bonus.getRangedStr(),
			bonus.getMagicStr() / 10.0, bonus.getPrayer());
	}

	/**
	 * @param tooltip the tooltip, or null for none
	 * @param action runs on click, or null when the caller adds its own listener
	 */
	static JButton button(String text, String tooltip, Runnable action)
	{
		JButton button = new JButton(text);
		button.setFocusable(false);
		button.setToolTipText(tooltip);
		if (action != null)
		{
			button.addActionListener(e -> action.run());
		}
		return button;
	}

	/**
	 * Makes a component act like a button: a hand cursor, a hover colour, and an action when pressed.
	 *
	 * @param background the colour when not hovered, or null for a see-through component
	 */
	static void clickable(JComponent component, Color background, Color hover, Runnable onClick)
	{
		component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				onClick.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				fill(component, hover);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				fill(component, background);
			}
		});
	}

	private static void fill(JComponent component, Color color)
	{
		component.setOpaque(color != null);
		if (color != null)
		{
			component.setBackground(color);
		}
		component.repaint();
	}

	/**
	 * Runs an action whenever the text of a field changes.
	 */
	static void onTextChange(Document document, Runnable onChange)
	{
		document.addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onChange.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onChange.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onChange.run();
			}
		});
	}

	/**
	 * A borderless scroll pane that scrolls up and down as needed, at a readable speed.
	 *
	 * @param transparent let the background show through, for scroll panes inside cards
	 */
	static JScrollPane scroll(Component view, int horizontalPolicy, boolean transparent)
	{
		JScrollPane scroll = new JScrollPane(view, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, horizontalPolicy);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		if (transparent)
		{
			scroll.setOpaque(false);
			scroll.getViewport().setOpaque(false);
		}
		return scroll;
	}

	static Icon icon(Glyph glyph, Color color)
	{
		return new Icon()
		{
			@Override
			public void paintIcon(Component c, Graphics graphics, int x, int y)
			{
				Graphics2D g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.translate(x, y);
				g.setColor(color);
				g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				switch (glyph)
				{
					case CHECK:
						g.drawPolyline(new int[]{2, 5, 10}, new int[]{6, 9, 2}, 3);
						break;
					case CROSS:
						g.drawLine(2, 2, 9, 9);
						g.drawLine(9, 2, 2, 9);
						break;
					case WARNING:
						Path2D triangle = new Path2D.Double();
						triangle.moveTo(6, 0);
						triangle.lineTo(12, 11);
						triangle.lineTo(0, 11);
						triangle.closePath();
						g.fill(triangle);
						g.setColor(Color.BLACK);
						g.setStroke(new BasicStroke(1.6f));
						g.drawLine(6, 4, 6, 7);
						g.drawLine(6, 9, 6, 9);
						break;
					case INFO:
						g.fillOval(0, 0, 12, 12);
						g.setColor(Color.BLACK);
						g.setStroke(new BasicStroke(1.6f));
						g.drawLine(6, 5, 6, 9);
						g.drawLine(6, 3, 6, 3);
						break;
					case LOCK:
						g.setStroke(new BasicStroke(1.6f));
						g.drawArc(3, 0, 6, 8, 0, 180);
						g.drawLine(3, 4, 3, 6);
						g.drawLine(9, 4, 9, 6);
						g.fillRoundRect(1, 5, 10, 7, 2, 2);
						break;
					case ARROW_UP:
						g.fillPolygon(new int[]{2, 10, 6}, new int[]{9, 9, 3}, 3);
						break;
					case ARROW_DOWN:
						g.fillPolygon(new int[]{2, 10, 6}, new int[]{3, 3, 9}, 3);
						break;
					default:
						g.fillOval(3, 3, 6, 6);
						break;
				}
				g.dispose();
			}

			@Override
			public int getIconWidth()
			{
				return 12;
			}

			@Override
			public int getIconHeight()
			{
				return 12;
			}
		};
	}

	/**
	 * Draws the toolbar/window icon (a sword over a shield), so no image asset is needed.
	 */
	static BufferedImage appIcon(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.scale(size / 16.0, size / 16.0);
		Path2D shield = new Path2D.Double();
		shield.moveTo(3, 2);
		shield.lineTo(13, 2);
		shield.lineTo(13, 8);
		shield.quadTo(13, 13, 8, 15);
		shield.quadTo(3, 13, 3, 8);
		shield.closePath();
		g.setColor(new Color(70, 110, 160));
		g.fill(shield);
		g.setColor(new Color(200, 210, 225));
		g.setStroke(new BasicStroke(1.4f));
		g.draw(shield);
		g.setColor(ColorScheme.BRAND_ORANGE);
		g.setStroke(new BasicStroke(1.8f));
		g.drawLine(8, 4, 8, 12);
		g.drawLine(5, 7, 11, 7);
		g.dispose();
		return image;
	}

	/** Text of a setting that is on. */
	private static final Color ON = ColorScheme.BRAND_ORANGE;

	/**
	 * An on/off button for a yes-or-no setting: orange with a tick when on.
	 */
	static JToggleButton toggle(String text, boolean on, String tooltip, Consumer<Boolean> onChange)
	{
		JToggleButton button = new JToggleButton(text, on);
		button.setFocusable(false);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setToolTipText(tooltip);
		Runnable style = () ->
		{
			button.setForeground(button.isSelected() ? ON : ColorScheme.LIGHT_GRAY_COLOR);
			button.setIcon(button.isSelected() ? icon(Glyph.CHECK, ON) : icon(Glyph.DOT, MUTED));
		};
		style.run();
		button.addActionListener(e ->
		{
			style.run();
			onChange.accept(button.isSelected());
		});
		return button;
	}

	/**
	 * A row of buttons where exactly one is picked, for settings with a few choices.
	 *
	 * @param options {value, label, tooltip} per button
	 */
	static JPanel segmented(String[][] options, String selected, Consumer<String> onPick)
	{
		JPanel row = new JPanel(new GridLayout(1, options.length, 2, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		ButtonGroup group = new ButtonGroup();
		for (String[] option : options)
		{
			JToggleButton button = new JToggleButton(option[1], option[0].equals(selected));
			button.setFocusable(false);
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setMargin(new Insets(2, 6, 2, 6));
			button.setToolTipText(option[2]);
			button.setForeground(button.isSelected() ? ON : ColorScheme.LIGHT_GRAY_COLOR);
			button.addActionListener(e ->
			{
				for (Component other : row.getComponents())
				{
					other.setForeground(other == button ? ON : ColorScheme.LIGHT_GRAY_COLOR);
				}
				onPick.accept(option[0]);
			});
			group.add(button);
			row.add(button);
		}
		return row;
	}

	/**
	 * A row of plain toggle buttons where one is picked, such as a view switcher or list filters.
	 */
	static final class Chips extends JPanel
	{
		private final Map<String, JToggleButton> buttons = new HashMap<>();

		/**
		 * @param options {value, label} per button
		 */
		Chips(String[][] options, String selected, Consumer<String> onPick)
		{
			super(new FlowLayout(FlowLayout.LEFT, 4, 0));
			setOpaque(false);
			ButtonGroup group = new ButtonGroup();
			for (String[] option : options)
			{
				JToggleButton button = new JToggleButton(option[1], option[0].equals(selected));
				button.setFocusable(false);
				button.setFont(FontManager.getRunescapeSmallFont());
				button.addActionListener(e -> onPick.accept(option[0]));
				group.add(button);
				add(button);
				buttons.put(option[0], button);
			}
		}

		void select(String value)
		{
			JToggleButton button = buttons.get(value);
			if (button != null)
			{
				button.setSelected(true);
			}
		}
	}

	/**
	 * A small rounded label used for statuses such as IN USE or BEST PICK.
	 */
	static JLabel pill(String text, Color background, Color foreground)
	{
		JLabel pill = new JLabel(text)
		{
			@Override
			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(background);
				g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
				g.dispose();
				super.paintComponent(graphics);
			}
		};
		pill.setFont(FontManager.getRunescapeSmallFont());
		pill.setForeground(foreground);
		pill.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
		pill.setOpaque(false);
		return pill;
	}
}
