package com.gearoptimizer.ui;

import com.gearoptimizer.GearOptimizerConfig;
import com.gearoptimizer.GearOptimizerPlugin;
import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.calc.Optimizer;
import com.gearoptimizer.data.GameDataRepository;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import net.runelite.client.util.QuantityFormatter;

/**
 * Data management: where monster and item data comes from, the items the player excluded or forced, and owned items
 * skipped for their level requirements.
 */
class DataTab extends JPanel
{
	private final GearOptimizerPlugin plugin;
	private final GameDataRepository repository;
	private final AccountSnapshot account;

	/**
	 * @param result the last calculation, or null before the first
	 * @param account the account whose bank names the excluded and forced items
	 */
	DataTab(GearOptimizerPlugin plugin, GearOptimizerConfig config, GameDataRepository repository, Optimizer.Result result,
		AccountSnapshot account)
	{
		super(new GridLayout(1, 3, 10, 0));
		this.plugin = plugin;
		this.repository = repository;
		this.account = account;
		setBackground(Ui.BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel data = Ui.card("Monster and item data");
		data.add(Ui.regular(repository.getSourceDescription(), Color.WHITE));
		data.add(Ui.small(QuantityFormatter.formatNumber(repository.getMonsters().size()) + " monsters, "
			+ QuantityFormatter.formatNumber(repository.getEquipment().size()) + " items", Ui.MUTED));
		data.add(Ui.gap(8));
		JButton refresh = Ui.button("Download latest data", config.allowDataDownload() ? "Two requests to GitHub"
			: "Enable 'Allow data refresh' in the plugin settings first", plugin::refreshData);
		refresh.setEnabled(config.allowDataDownload());
		refresh.setAlignmentX(Component.LEFT_ALIGNMENT);
		data.add(refresh);
		data.add(Ui.gap(4));
		JButton reset = Ui.button("Use bundled data", null, plugin::resetData);
		reset.setAlignmentX(Component.LEFT_ALIGNMENT);
		data.add(reset);
		data.add(Ui.gap(8));
		data.add(Ui.wrapped("The bundled data ships with the plugin and never touches the network. Downloading is optional "
			+ "and off by default.", Ui.MUTED, 220));
		add(top(data));

		JPanel exclusions = Ui.card("Excluded and forced items");
		Set<Integer> excluded = plugin.getExcluded();
		Set<Integer> locked = plugin.getLocked();
		addItemRows(exclusions, excluded, "excludedItems", "Excluded", "Include");
		addItemRows(exclusions, locked, "lockedItems", "Always used", "Release");
		if (excluded.isEmpty() && locked.isEmpty())
		{
			exclusions.add(Ui.wrapped("Nothing yet. Right-click any item in a setup to exclude it or force it.", Ui.MUTED, 220));
		}
		add(top(exclusions));

		JPanel skipped = Ui.card("Skipped for level requirements");
		Map<String, String> unwearable = result == null ? Map.of() : result.getUnwearable();
		if (unwearable.isEmpty())
		{
			skipped.add(Ui.small("Nothing skipped", Ui.MUTED));
		}
		for (Map.Entry<String, String> entry : unwearable.entrySet())
		{
			skipped.add(Ui.small(entry.getKey() + ": needs " + entry.getValue(), Color.WHITE));
		}
		add(top(skipped));
	}

	private void addItemRows(JPanel card, Set<Integer> ids, String key, String label, String action)
	{
		for (int id : ids)
		{
			String name = account.nameOf(id);
			if (name == null && repository.getItem(id) != null)
			{
				name = repository.getItem(id).getName();
			}
			JPanel row = new JPanel(new BorderLayout(6, 0));
			row.setOpaque(false);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.add(Ui.small(label + ": " + (name == null ? "item " + id : name), Color.WHITE), BorderLayout.CENTER);
			row.add(Ui.button(action, null, () -> plugin.toggleItemSet(key, id)), BorderLayout.EAST);
			card.add(row);
			card.add(Ui.gap(2));
		}
	}

	/**
	 * Keeps a card at the top of its grid cell instead of stretching it.
	 */
	private static JPanel top(JPanel card)
	{
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.add(card, BorderLayout.NORTH);
		return wrap;
	}
}
