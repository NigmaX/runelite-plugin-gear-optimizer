package com.gearoptimizer.ui;

import com.gearoptimizer.data.GameDataRepository;
import com.gearoptimizer.data.Monster;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;

/**
 * The monster search field and the list of matches under it: names starting with the text first, then names
 * containing it.
 */
final class MonsterSearch
{
	private static final int MAX_RESULTS = 12;

	private final GameDataRepository repository;
	private final Consumer<Monster> onPick;
	@Getter
	private final IconTextField field = new IconTextField();
	private final JPopupMenu popup = new JPopupMenu();

	MonsterSearch(GameDataRepository repository, Consumer<Monster> onPick)
	{
		this.repository = repository;
		this.onPick = onPick;
		field.setIcon(IconTextField.Icon.SEARCH);
		field.setPreferredSize(new Dimension(300, 30));
		field.setBackground(Ui.BACKGROUND);
		field.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		Ui.onTextChange(field.getDocument(), this::update);
		field.addActionListener(e -> pickFirst());
		popup.setFocusable(false);
	}

	/**
	 * Lists the monsters matching the text, e.g. after the data has loaded.
	 */
	void update()
	{
		popup.setVisible(false);
		popup.removeAll();
		String query = query();
		if (query.length() < 2 || !repository.isLoaded() || !field.isShowing())
		{
			return;
		}

		Map<String, Monster> matches = new LinkedHashMap<>();
		// Prefix matches first, then substring matches
		for (int pass = 0; pass < 2 && matches.size() < MAX_RESULTS; pass++)
		{
			for (Monster monster : repository.getMonsters())
			{
				String name = monster.getName().toLowerCase();
				if (pass == 0 ? name.startsWith(query) : name.contains(query))
				{
					matches.putIfAbsent(monster.getDisplayName(), monster);
					if (matches.size() >= MAX_RESULTS)
					{
						break;
					}
				}
			}
		}
		if (matches.isEmpty())
		{
			JMenuItem none = new JMenuItem("No monster matches \"" + query + "\"");
			none.setEnabled(false);
			popup.add(none);
		}
		for (Monster monster : matches.values())
		{
			JMenuItem item = new JMenuItem(monster.getDisplayName() + "   (level " + monster.getLevel() + ")");
			item.addActionListener(e -> choose(monster));
			popup.add(item);
		}
		popup.setPreferredSize(new Dimension(field.getWidth(), popup.getPreferredSize().height));
		popup.show(field, 0, field.getHeight());
		field.requestFocusInWindow();
	}

	private String query()
	{
		return field.getText() == null ? "" : field.getText().trim().toLowerCase();
	}

	private void pickFirst()
	{
		String query = query();
		for (Monster monster : repository.getMonsters())
		{
			if (monster.getName().toLowerCase().startsWith(query))
			{
				choose(monster);
				return;
			}
		}
	}

	private void choose(Monster monster)
	{
		field.setText("");
		popup.setVisible(false);
		onPick.accept(monster);
	}
}
