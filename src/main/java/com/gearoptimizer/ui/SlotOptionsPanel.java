package com.gearoptimizer.ui;

import com.gearoptimizer.calc.Experiment;
import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.OwnedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Every option for one equipment slot (owned plus the strongest not owned) in a sortable, filterable table, one page
 * at a time. Each slot's options are scored once; paging, sorting, filtering and prices only redraw the rows.
 */
class SlotOptionsPanel extends JPanel
{
	private static final int PAGE_SIZE = 10;
	private static final String[] COLUMN_NAMES = {"Item", "Status", "DPS", "Change", "GE price", "Note"};
	/** Column widths in pixels; 0 means the column shares the leftover space (item name and note). */
	private static final int[] COLUMN_WIDTHS = {0, 132, 50, 60, 86, 0};
	private static final double[] FLEX_WEIGHTS = {0.6, 0, 0, 0, 0, 0.4};
	/** Narrowest the shared columns get before the table scrolls sideways, so item names stay readable. */
	private static final int[] FLEX_MIN_WIDTHS = {120, 0, 0, 0, 0, 70};
	static final int ROW_HEIGHT = 34;
	private static final int CELL_GAP = 8;
	/** Row border + icon + gap + every column with its gap. */
	private static final int TABLE_MIN_WIDTH;

	static
	{
		int width = 3 + 36 + 8;
		for (int i = 0; i < COLUMN_WIDTHS.length; i++)
		{
			width += (COLUMN_WIDTHS[i] == 0 ? FLEX_MIN_WIDTHS[i] : COLUMN_WIDTHS[i]) + CELL_GAP;
		}
		TABLE_MIN_WIDTH = width;
	}

	private final TabModel model;
	private final StyleResult result;
	private final StyleTab.Actions actions;
	private final ItemIcons icons;
	/** Filter and sort order, shared by every tab. */
	private final TabState state;
	/** Each slot's options as scored against the shown setup, best first. */
	private final Map<GearSlot, List<Experiment.SlotOption>> optionsBySlot = new EnumMap<>(GearSlot.class);
	private GearSlot selectedSlot;
	private int page;
	/** The options list's scroll pane and what it showed, to keep the scroll position across redraws. */
	private JScrollPane optionsScroll;
	private GearSlot scrolledSlot;
	private int scrolledPage;
	private int recommendedOptionId = -1;

	SlotOptionsPanel(TabModel model, StyleTab.Actions actions, ItemIcons icons, TabState state)
	{
		super(new BorderLayout(0, 6));
		this.model = model;
		this.result = model.getResult();
		this.actions = actions;
		this.icons = icons;
		this.state = state;
		setOpaque(false);
	}

	/**
	 * Lists the options for a slot, from the first page when it's a different slot.
	 *
	 * @param slot the slot, or null for the weapon
	 */
	void show(GearSlot slot)
	{
		if (slot != selectedSlot)
		{
			page = 0;
		}
		selectedSlot = slot == null ? GearSlot.WEAPON : slot;
		render();
	}

	/**
	 * @return the slot whose options are listed
	 */
	GearSlot getSelectedSlot()
	{
		return selectedSlot;
	}

	/**
	 * @return the slot's options, scored the first time they're needed
	 */
	private List<Experiment.SlotOption> options(GearSlot slot)
	{
		StyleResult base = model.isEdited() ? model.getRecommended() : result;
		return optionsBySlot.computeIfAbsent(slot, s -> Experiment.slotOptions(result, s, actions.ownedItems(), actions.allItems(),
			actions.rules(), base.getLoadout().get(GearSlot.AMMO)));
	}

	/**
	 * Redraws the selected slot's options with the current filter, sort order, page and prices.
	 */
	private void render()
	{
		GearSlot shownSlot = selectedSlot;
		StyleResult base = model.isEdited() ? model.getRecommended() : result;
		OwnedItem recommendedItem = base.getLoadout().get(shownSlot);
		int recommendedId = recommendedItem == null ? -1 : recommendedItem.getStats().getId();
		List<Experiment.SlotOption> options = new ArrayList<>(options(shownSlot));
		int total = options.size();
		if ("OWNED".equals(state.getSlotFilter()))
		{
			options.removeIf(o -> !o.isOwned() && !o.isInUse() && o.getItem().getStats().getId() != recommendedId);
		}
		else if ("BETTER".equals(state.getSlotFilter()))
		{
			options.removeIf(o -> o.getChange() <= Ui.DPS_EPSILON && !o.isInUse());
		}
		recommendedOptionId = recommendedId;
		if (state.getSortColumn() >= 0)
		{
			options.sort(comparator(state.getSortColumn(), state.isSortAscending()));
		}
		else
		{
			// Default: keep the item in use and the recommended item at the top so they never get lost
			List<Experiment.SlotOption> pinned = new ArrayList<>();
			for (Experiment.SlotOption option : new ArrayList<>(options))
			{
				if (option.isInUse() || option.getItem().getStats().getId() == recommendedId)
				{
					options.remove(option);
					pinned.add(option);
				}
			}
			pinned.sort((a, b) -> Boolean.compare(b.isInUse(), a.isInUse()));
			options.addAll(0, pinned);
		}
		int pages = Math.max(1, (options.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.min(page, pages - 1);

		Point keepScroll = optionsScroll != null && scrolledSlot == shownSlot && scrolledPage == page
			? optionsScroll.getViewport().getViewPosition() : null;
		removeAll();
		OwnedItem current = result.getLoadout().get(shownSlot);
		JPanel header = new JPanel(new BorderLayout(0, 6));
		header.setOpaque(false);

		JPanel titleRow = new JPanel(new BorderLayout(8, 0));
		titleRow.setOpaque(false);
		JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		title.setOpaque(false);
		title.add(Ui.small(shownSlot.getDisplayName().toUpperCase(), Ui.MUTED));
		title.add(Ui.bold(current == null ? "Empty" : current.getStats().getName(), Color.WHITE));
		titleRow.add(title, BorderLayout.WEST);
		JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		actionsRow.setOpaque(false);
		int currentId = current == null ? -1 : current.getStats().getId();
		if (model.isEdited() && currentId != recommendedId)
		{
			JButton revert = Ui.button("Use best pick: " + (recommendedItem == null ? "empty" : recommendedItem.getStats().getName()), null,
				() -> actions.tryItem(result.getStyle(), shownSlot, recommendedItem));
			revert.setForeground(ColorScheme.BRAND_ORANGE);
			actionsRow.add(revert);
		}
		actionsRow.add(Ui.button("Search all items", "Pick any item in the game for this slot",
			() -> actions.chooseItem(result.getStyle(), shownSlot)));
		titleRow.add(actionsRow, BorderLayout.EAST);
		header.add(titleRow, BorderLayout.NORTH);

		Ui.Chips filters = new Ui.Chips(new String[][]{{"ALL", "All"}, {"OWNED", "Owned"}, {"BETTER", "Better only"}},
			state.getSlotFilter(), filter ->
			{
				state.setSlotFilter(filter);
				page = 0;
				render();
			});
		filters.add(Ui.small("  " + options.size() + " of " + total + " items", Ui.MUTED));
		header.add(filters, BorderLayout.CENTER);
		add(header, BorderLayout.NORTH);

		// Rows keep a fixed height and stack from the top instead of stretching over the panel
		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setOpaque(false);
		Set<Integer> unpriced = new HashSet<>();
		int from = page * PAGE_SIZE;
		for (int i = from; i < from + PAGE_SIZE; i++)
		{
			if (i < options.size())
			{
				Experiment.SlotOption option = options.get(i);
				rows.add(optionRow(option));
				if (actions.price(option.getItem().getItemId()) == null)
				{
					unpriced.add(option.getItem().getItemId());
				}
			}
			else if (i == 0)
			{
				rows.add(Ui.small("Nothing matches this filter.", Ui.MUTED));
			}
		}
		// The rows scroll under a pinned header row, and sideways when the panel is too narrow for the item names
		TableView body = new TableView();
		body.add(rows, BorderLayout.NORTH);
		TableView head = new TableView();
		head.add(optionRow(null), BorderLayout.NORTH);
		JScrollPane scroll = Ui.scroll(body, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED, true);
		scroll.setColumnHeaderView(head);
		scroll.getColumnHeader().setOpaque(false);
		scroll.getHorizontalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);
		if (keepScroll != null)
		{
			// Price lookups redraw the list; stay where the player was
			SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(keepScroll));
		}
		optionsScroll = scroll;
		scrolledSlot = shownSlot;
		scrolledPage = page;

		JPanel pager = new JPanel(new BorderLayout());
		pager.setOpaque(false);
		pager.add(Ui.small("Click a row to try it. DPS change is against the setup shown.", Ui.MUTED), BorderLayout.WEST);
		JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		nav.setOpaque(false);
		JButton prev = Ui.button("<", null, () ->
		{
			page--;
			render();
		});
		prev.setEnabled(page > 0);
		JButton next = Ui.button(">", null, () ->
		{
			page++;
			render();
		});
		next.setEnabled(page < pages - 1);
		nav.add(prev);
		nav.add(Ui.small((page + 1) + " / " + pages, Color.WHITE));
		nav.add(next);
		pager.add(nav, BorderLayout.EAST);
		add(pager, BorderLayout.SOUTH);
		revalidate();
		repaint();

		if (!unpriced.isEmpty())
		{
			actions.requestPrices(unpriced, this, () ->
			{
				if (selectedSlot == shownSlot)
				{
					render();
				}
			});
		}
	}

	/**
	 * Holds the option rows (or their header) in a scroll pane: as wide as the panel, but never narrower than
	 * {@link #TABLE_MIN_WIDTH}, beyond which the table scrolls sideways.
	 */
	private static final class TableView extends JPanel implements Scrollable
	{
		TableView()
		{
			super(new BorderLayout());
			setOpaque(false);
		}

		@Override
		public Dimension getPreferredSize()
		{
			Dimension size = super.getPreferredSize();
			return new Dimension(Math.max(TABLE_MIN_WIDTH, size.width), size.height);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
		{
			return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return getParent() instanceof JViewport && getParent().getWidth() >= TABLE_MIN_WIDTH;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return getParent() instanceof JViewport && getParent().getHeight() > super.getPreferredSize().height;
		}
	}

	/**
	 * @param option null for the column header row
	 */
	private JPanel optionRow(Experiment.SlotOption option)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
		row.setAlignmentX(LEFT_ALIGNMENT);
		int height = option == null ? 20 : ROW_HEIGHT;
		row.setPreferredSize(new Dimension(100, height));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		JLabel icon = ItemIcons.blank(height);
		row.add(icon, BorderLayout.WEST);

		JPanel cols = new JPanel(new GridBagLayout());
		cols.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(0, 0, 0, CELL_GAP);

		Component[] cells;
		if (option == null)
		{
			cells = new Component[COLUMN_NAMES.length];
			for (int i = 0; i < COLUMN_NAMES.length; i++)
			{
				cells[i] = headerCell(i);
			}
		}
		else
		{
			OwnedItem item = option.getItem();
			icons.addTo(icon, item.getItemId());
			boolean isRecommended = item.getStats().getId() == recommendedOptionId;
			double change = option.getChange();
			Long price = actions.price(item.getItemId());

			JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
			status.setOpaque(false);
			if (option.isInUse())
			{
				status.add(Ui.pill("IN USE", ColorScheme.BRAND_ORANGE, Color.BLACK));
			}
			if (isRecommended)
			{
				status.add(Ui.pill("BEST PICK", new Color(45, 85, 150), Color.WHITE));
			}
			if (option.isOwned() && !option.isInUse())
			{
				status.add(Ui.pill("OWNED", new Color(55, 75, 55), new Color(200, 235, 200)));
			}

			JLabel note = noteLabel(option, change);
			cells = new Component[]{
				Ui.small(item.getStats().getName(), option.isInUse() ? ColorScheme.BRAND_ORANGE : option.isOwned() ? Color.WHITE
					: ColorScheme.LIGHT_GRAY_COLOR),
				status,
				Ui.small(String.format("%.2f", option.getDps()), Color.WHITE),
				Ui.small(Math.abs(change) < Ui.DPS_EPSILON ? "-" : String.format("%+.2f", change), Ui.deltaColor(change)),
				Ui.small(Ui.priceText(price, "Untradeable"), price != null && price == 0 ? Ui.MUTED : Color.WHITE),
				note,
			};
			row.setToolTipText("<html><b>" + Ui.escape(item.getStats().getDisplayName()) + "</b>"
				+ (price != null && price > 0 ? "<br>GE price: " + Ui.exactGp(price) : "")
				+ (option.getNote() != null ? "<br>" + Ui.escape(option.getNote()) : "") + "</html>");
		}
		for (int i = 0; i < cells.length; i++)
		{
			c.gridx = i;
			c.weightx = FLEX_WEIGHTS[i];
			Component cell = cells[i];
			int width = COLUMN_WIDTHS[i];
			Dimension size = new Dimension(width == 0 ? FLEX_MIN_WIDTHS[i] : width, 18);
			cell.setPreferredSize(size);
			cell.setMinimumSize(size);
			cols.add(cell, c);
		}
		row.add(cols, BorderLayout.CENTER);

		if (option != null && option.isInUse())
		{
			row.setBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, ColorScheme.BRAND_ORANGE));
		}
		if (option != null && !option.isInUse())
		{
			OwnedItem item = option.getItem();
			Ui.clickable(row, null, ColorScheme.DARK_GRAY_HOVER_COLOR, () -> actions.tryItem(result.getStyle(), selectedSlot, item));
		}
		return row;
	}

	/**
	 * A clickable column header: sorts by that column, click again to reverse, a third time for the default order.
	 */
	private JLabel headerCell(int column)
	{
		boolean active = state.getSortColumn() == column;
		JLabel label = Ui.small(COLUMN_NAMES[column], active ? Color.WHITE : Ui.MUTED);
		if (active)
		{
			label.setIcon(Ui.icon(state.isSortAscending() ? Ui.Glyph.ARROW_UP : Ui.Glyph.ARROW_DOWN, ColorScheme.BRAND_ORANGE));
			label.setHorizontalTextPosition(SwingConstants.LEFT);
			label.setIconTextGap(4);
		}
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		boolean textColumn = column == 0 || column == 5;
		boolean nextIsDefault = active && state.isSortAscending() != textColumn;
		label.setToolTipText(!active ? "Sort by " + COLUMN_NAMES[column].toLowerCase()
			: nextIsDefault ? "Click for the default order" : "Click to reverse");
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (state.getSortColumn() != column)
				{
					state.setSortColumn(column);
					// Numbers start highest-first, text starts A-Z
					state.setSortAscending(textColumn);
				}
				else if (state.isSortAscending() != textColumn)
				{
					// Second click already reversed it: back to the default order
					state.setSortColumn(-1);
				}
				else
				{
					state.setSortAscending(!state.isSortAscending());
				}
				page = 0;
				render();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.setForeground(state.getSortColumn() == column ? Color.WHITE : Ui.MUTED);
			}
		});
		return label;
	}

	private Comparator<Experiment.SlotOption> comparator(int column, boolean ascending)
	{
		Comparator<Experiment.SlotOption> comparator;
		switch (column)
		{
			case 0:
				comparator = Comparator.comparing(o -> o.getItem().getStats().getName().toLowerCase());
				break;
			case 1:
				// In use, then best pick, then owned, then the rest
				comparator = Comparator.comparingInt(o -> -statusRank(o));
				break;
			case 2:
				comparator = Comparator.comparingDouble(Experiment.SlotOption::getDps);
				break;
			case 3:
				comparator = Comparator.comparingDouble(Experiment.SlotOption::getChange);
				break;
			case 4:
				comparator = Comparator.comparingLong(o ->
				{
					Long price = actions.price(o.getItem().getItemId());
					return price == null || price <= 0 ? Long.MIN_VALUE : price;
				});
				break;
			default:
				comparator = Comparator.comparing(o -> o.getNote() == null ? "\uffff" : o.getNote().toLowerCase());
				break;
		}
		Comparator<Experiment.SlotOption> byDps = Comparator.comparingDouble(Experiment.SlotOption::getDps).reversed();
		if (column == 1)
		{
			return (ascending ? comparator.reversed() : comparator).thenComparing(byDps);
		}
		Comparator<Experiment.SlotOption> ordered = ascending ? comparator : comparator.reversed();
		if (column == 4)
		{
			// Untradeable and unknown prices always go last
			Comparator<Experiment.SlotOption> unpriced = Comparator.comparing(o ->
			{
				Long price = actions.price(o.getItem().getItemId());
				return price == null || price <= 0;
			});
			return unpriced.thenComparing(ordered);
		}
		return ordered.thenComparing(byDps);
	}

	private int statusRank(Experiment.SlotOption option)
	{
		if (option.isInUse())
		{
			return 3;
		}
		if (option.getItem().getStats().getId() == recommendedOptionId)
		{
			return 2;
		}
		return option.isOwned() ? 1 : 0;
	}

	/**
	 * Turns an option's note into a short, colour-coded label.
	 */
	private static JLabel noteLabel(Experiment.SlotOption option, double change)
	{
		String note = option.getNote();
		JLabel label;
		if (option.isUnsafe())
		{
			label = Ui.marked(Ui.Glyph.CROSS, Ui.BAD, note.replace("Unsafe: removes", "Removes"), Ui.BAD);
		}
		else if (note == null)
		{
			label = Ui.small(!option.isInUse() && Math.abs(change) < Ui.DPS_EPSILON ? "Same DPS" : "", Ui.MUTED);
		}
		else if (note.contains("monsters"))
		{
			label = Ui.marked(Ui.Glyph.INFO, Ui.INFO, note, Ui.INFO);
		}
		else
		{
			String shortNote = note.replace("Switched ammo to ", "Uses ").replace("Removed the shield (two-handed weapon)", "Two-handed")
				.replace("Removed the two-handed ", "Removes ");
			label = Ui.marked(Ui.Glyph.WARNING, Ui.WARN, shortNote, Ui.WARN);
		}
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}
}
