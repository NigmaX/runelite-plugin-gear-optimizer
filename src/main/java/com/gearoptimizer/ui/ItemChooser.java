package com.gearoptimizer.ui;

import com.gearoptimizer.calc.Experiment;
import com.gearoptimizer.calc.UpgradeFinder;
import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.OwnedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;

/**
 * Lets the player put any item in a slot: owned items first, then everything else in the game.
 */
class ItemChooser extends JDialog
{
	private static final int MAX_ROWS = 250;

	private static final class Entry
	{
		final OwnedItem item;
		final boolean owned;
		final boolean usable;

		Entry(OwnedItem item, boolean owned, boolean usable)
		{
			this.item = item;
			this.owned = owned;
			this.usable = usable;
		}
	}

	private final ItemIcons itemIcons;
	private final List<Entry> all = new ArrayList<>();
	private final DefaultListModel<Entry> model = new DefaultListModel<>();
	private final JList<Entry> list = new JList<>(model);
	private final IconTextField search = new IconTextField();
	private final Map<Integer, ImageIcon> icons = new HashMap<>();
	private final Consumer<OwnedItem> onPick;

	/**
	 * @param weapon the current weapon, to grey out ammo it cannot fire
	 * @param onPick receives the chosen item, or null for an empty slot
	 */
	ItemChooser(Window owner, GearSlot slot, List<OwnedItem> owned, List<EquipmentItem> everything, EquipmentItem weapon,
		ItemIcons itemIcons, Consumer<OwnedItem> onPick)
	{
		super(owner, "Try another " + slot.getDisplayName().toLowerCase(), ModalityType.APPLICATION_MODAL);
		this.itemIcons = itemIcons;
		this.onPick = onPick;

		all.add(new Entry(null, true, true));
		Set<Integer> ownedIds = new HashSet<>();
		for (OwnedItem item : owned)
		{
			if (item.getStats().getGearSlot() == slot && !UpgradeFinder.isMinigameOnly(item.getStats())
				&& ownedIds.add(item.getStats().getId()))
			{
				all.add(new Entry(item, true, usable(slot, weapon, item.getStats())));
			}
		}
		Set<String> seenNames = new HashSet<>();
		for (EquipmentItem item : everything)
		{
			if (item.getGearSlot() == slot && !ownedIds.contains(item.getId()) && !Experiment.isJunkVariant(item)
				&& seenNames.add(item.getDisplayName()))
			{
				all.add(new Entry(new OwnedItem(item, item.getId(), false), false, usable(slot, weapon, item)));
			}
		}

		JPanel root = new JPanel(new BorderLayout(0, 6));
		root.setBackground(Ui.BACKGROUND);
		root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(300, 30));
		search.setBackground(Ui.CARD);
		Ui.onTextChange(search.getDocument(), this::filter);
		search.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER && !model.isEmpty())
				{
					pick(list.getSelectedIndex() >= 0 ? list.getSelectedValue() : model.get(0));
				}
				else if (e.getKeyCode() == KeyEvent.VK_DOWN && !model.isEmpty())
				{
					list.setSelectedIndex(Math.min(model.size() - 1, list.getSelectedIndex() + 1));
				}
				else if (e.getKeyCode() == KeyEvent.VK_UP && !model.isEmpty())
				{
					list.setSelectedIndex(Math.max(0, list.getSelectedIndex() - 1));
				}
			}
		});
		root.add(search, BorderLayout.NORTH);

		list.setBackground(Ui.BACKGROUND);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new Renderer());
		list.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int index = list.locationToIndex(e.getPoint());
				if (index >= 0)
				{
					pick(model.get(index));
				}
			}
		});
		root.add(Ui.scroll(list, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED, false), BorderLayout.CENTER);
		root.add(Ui.small("Owned items are listed first. Greyed-out ammo can't be fired by your weapon.", Ui.MUTED), BorderLayout.SOUTH);

		setContentPane(root);
		setSize(420, 560);
		setLocationRelativeTo(owner);
		filter();
	}

	private static boolean usable(GearSlot slot, EquipmentItem weapon, EquipmentItem item)
	{
		return slot != GearSlot.AMMO || weapon == null || Experiment.canFire(weapon, item);
	}

	private void filter()
	{
		String query = search.getText() == null ? "" : search.getText().trim().toLowerCase();
		model.clear();
		for (Entry entry : all)
		{
			if (model.size() >= MAX_ROWS)
			{
				break;
			}
			if (query.isEmpty() || (entry.item != null && entry.item.getStats().getDisplayName().toLowerCase().contains(query)))
			{
				model.addElement(entry);
			}
		}
	}

	private void pick(Entry entry)
	{
		if (!entry.usable)
		{
			return;
		}
		dispose();
		onPick.accept(entry.item);
	}

	private ImageIcon icon(int itemId)
	{
		return icons.computeIfAbsent(itemId, id -> itemIcons.imageIcon(id, list::repaint));
	}

	private final class Renderer implements ListCellRenderer<Entry>
	{
		private final JPanel row = new JPanel(new BorderLayout(8, 0));
		private final JLabel iconLabel = ItemIcons.blank(32);
		private final JLabel name = new JLabel();
		private final JLabel detail = new JLabel();

		Renderer()
		{
			row.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
			name.setFont(FontManager.getRunescapeFont());
			detail.setFont(FontManager.getRunescapeSmallFont());
			JPanel text = new JPanel(new BorderLayout());
			text.setOpaque(false);
			text.add(name, BorderLayout.CENTER);
			text.add(detail, BorderLayout.SOUTH);
			row.add(iconLabel, BorderLayout.WEST);
			row.add(text, BorderLayout.CENTER);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Entry> list, Entry entry, int index, boolean selected, boolean focus)
		{
			row.setBackground(selected ? ColorScheme.DARK_GRAY_HOVER_COLOR : Ui.BACKGROUND);
			if (entry.item == null)
			{
				iconLabel.setIcon(null);
				name.setText("Empty slot");
				name.setForeground(Color.WHITE);
				detail.setText("Remove the item");
				detail.setForeground(Ui.MUTED);
				return row;
			}
			EquipmentItem stats = entry.item.getStats();
			iconLabel.setIcon(icon(entry.item.getItemId()));
			name.setText(stats.getDisplayName());
			name.setForeground(!entry.usable ? Ui.MUTED.darker() : entry.owned ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
			String text = (entry.owned ? "Owned" : "Not owned") + "  ·  " + Ui.itemStats(stats, true);
			detail.setText(entry.usable ? text : "Your weapon can't fire this");
			detail.setForeground(entry.owned ? Ui.GOOD : Ui.MUTED);
			return row;
		}
	}
}
