package com.gearoptimizer.ui;

import com.gearoptimizer.GearOptimizerConfig;
import com.gearoptimizer.GearOptimizerPlugin;
import com.gearoptimizer.account.AccountSnapshot;
import com.gearoptimizer.account.AccountSyncService;
import com.gearoptimizer.calc.CombatStyle;
import com.gearoptimizer.calc.Consumables;
import com.gearoptimizer.calc.Experiment;
import com.gearoptimizer.calc.Loadout;
import com.gearoptimizer.calc.MonsterRules;
import com.gearoptimizer.calc.Optimizer;
import com.gearoptimizer.calc.StyleResult;
import com.gearoptimizer.calc.UpgradeFinder;
import com.gearoptimizer.data.EquipmentItem;
import com.gearoptimizer.data.GameDataRepository;
import com.gearoptimizer.data.GearSlot;
import com.gearoptimizer.data.Monster;
import com.gearoptimizer.data.OwnedItem;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The optimizer's own window: monster search and the styles to compare on top, the monster and your stats on
 * the left, and tabs for each combat style (with the settings that change their damage), a comparison and data
 * management.
 */
public class OptimizerWindow extends JFrame implements StyleTab.Actions
{
	private static final int SIDEBAR_WIDTH = 290;
	/** Width text wraps at in the sidebar cards. */
	private static final int SIDEBAR_TEXT_WIDTH = SIDEBAR_WIDTH - 70;
	private static final String TAB_COMPARE = "Compare";
	private static final String TAB_DATA = "Data & exclusions";

	private final GearOptimizerPlugin plugin;
	private final GearOptimizerConfig config;
	private final ConfigManager configManager;
	private final GameDataRepository repository;
	private final AccountSyncService accountSync;
	private final Client client;
	private final SpriteManager spriteManager;
	private final SkillIconManager skillIcons;
	private final ItemIcons itemIcons;
	private final PriceCache prices;
	private final MonsterSearch search;

	private final JLabel statusLabel = Ui.regular(" ", ColorScheme.LIGHT_GRAY_COLOR);
	private final JComboBox<Choice> profileCombo = new JComboBox<>();
	private final JLabel bankLabel = Ui.small(" ", Ui.MUTED);
	private final MonsterCard monsterCard = new MonsterCard(SIDEBAR_TEXT_WIDTH);
	private final LocationsCard locationsCard;
	/** The monster on the monster card. */
	private Monster shownMonster;
	private final LevelsCard levelsCard = new LevelsCard(this::skillIcon, SIDEBAR_TEXT_WIDTH);
	private final JTabbedPane tabs = new JTabbedPane();
	private final Map<Skill, ImageIcon> skillIconCache = new EnumMap<>(Skill.class);

	private OptimizerView lastView;
	private CombatStyle selectedStyle;
	/** Key of the selected tab: a CombatStyle name or a tab title. */
	private String selectedTabKey;
	/** Tab keys in tab order. */
	private final List<String> tabKeys = new ArrayList<>();
	/** The panel holding each tab's content, by tab key. */
	private final Map<String, JPanel> tabPanels = new HashMap<>();
	private final Map<CombatStyle, StyleTab> styleTabs = new EnumMap<>(CombatStyle.class);
	private final ExperimentStore experiments = new ExperimentStore();
	/** How the style tabs show their right panel, shared by all of them. */
	private final TabState tabState = new TabState();
	/** "Max DPS" toggle: show the best gear in the game instead of the best owned gear. */
	private boolean maxMode;
	private MaxDpsView maxView;
	private boolean maxPending;
	private final Set<Integer> ownedStatIds = new HashSet<>();
	private List<OwnedItem> ownedItemsCache = new ArrayList<>();
	private boolean updatingControls;
	private boolean positioned;

	@Inject
	OptimizerWindow(GearOptimizerPlugin plugin, GearOptimizerConfig config, ConfigManager configManager,
		ItemManager itemManager, GameDataRepository repository, AccountSyncService accountSync, Client client,
		SpriteManager spriteManager, SkillIconManager skillIcons)
	{
		super("Gear Optimizer");
		this.spriteManager = spriteManager;
		this.skillIcons = skillIcons;
		this.plugin = plugin;
		this.config = config;
		this.configManager = configManager;
		this.repository = repository;
		this.accountSync = accountSync;
		this.client = client;
		this.itemIcons = new ItemIcons(itemManager);
		this.prices = new PriceCache(plugin::lookupPrices);
		this.search = new MonsterSearch(repository, plugin::selectMonster);
		this.locationsCard = new LocationsCard(SIDEBAR_TEXT_WIDTH, plugin::selectPlace,
			places -> plugin.showOnMap(shownMonster, places, this::showStatus),
			places -> plugin.walkTo(shownMonster, places, this::showStatus),
			() -> plugin.clearMapAndRoute(this::showStatus));

		setIconImage(Ui.appIcon(32));
		setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		setMinimumSize(new Dimension(1100, 680));
		setSize(1280, 780);

		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(Ui.BACKGROUND);
		root.add(topBar(), BorderLayout.NORTH);
		root.add(sidebar(), BorderLayout.WEST);

		tabs.setBackground(Ui.BACKGROUND);
		tabs.setFont(FontManager.getRunescapeFont());
		tabs.addChangeListener(e -> onTabChanged());
		JPanel center = new JPanel(new BorderLayout());
		center.setBackground(Ui.BACKGROUND);
		center.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 8));
		center.add(tabs, BorderLayout.CENTER);
		root.add(center, BorderLayout.CENTER);

		JPanel status = new JPanel(new BorderLayout());
		status.setBackground(Ui.CARD);
		status.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
		status.add(statusLabel, BorderLayout.WEST);
		status.add(Ui.small("Monster and item data: OSRS Wiki (CC BY-NC-SA 3.0)", Ui.MUTED), BorderLayout.EAST);
		root.add(status, BorderLayout.SOUTH);

		setContentPane(root);
		showEmptyState();
	}

	public static BufferedImage toolbarIcon()
	{
		return Ui.appIcon(16);
	}

	public static BufferedImage appIcon(int size)
	{
		return Ui.appIcon(size);
	}

	/**
	 * @return the setup on screen: the style tab open or last opened, else the best style; null before a monster
	 */
	public StyleResult currentSetup()
	{
		if (lastView == null)
		{
			return null;
		}
		StyleTab tab = styleTabs.get(selectedStyle);
		return tab != null ? tab.getShown() : lastView.getResult().getBest();
	}

	/**
	 * Shows the window, next to the client the first time.
	 */
	public void open()
	{
		if (!positioned)
		{
			Window owner = client.getCanvas() == null ? null : SwingUtilities.getWindowAncestor(client.getCanvas());
			setLocationRelativeTo(owner);
			positioned = true;
		}
		setVisible(true);
		setExtendedState(getExtendedState() & ~ICONIFIED);
		toFront();
		search.getField().requestFocusInWindow();
	}

	// ---- Layout ----

	private JPanel topBar()
	{
		JPanel bar = new JPanel(new BorderLayout(12, 0));
		bar.setBackground(Ui.CARD);
		bar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

		JPanel searchRow = new JPanel(new BorderLayout(6, 0));
		searchRow.setOpaque(false);
		searchRow.add(search.getField(), BorderLayout.CENTER);
		searchRow.add(Ui.button("Use target", "Select the monster you last attacked", plugin::selectLastTarget), BorderLayout.EAST);
		bar.add(searchRow, BorderLayout.WEST);

		// Which styles to compare is a choice about the whole fight, so it sits next to the monster search
		JPanel styles = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		styles.setOpaque(false);
		styles.add(Ui.small("Compare", Ui.MUTED));
		styles.add(Ui.segmented(new String[][]{
			{"BEST", "All styles", "Optimise melee, ranged and magic, and pick the best"},
			{"MELEE", "Melee", "Only optimise melee"},
			{"RANGED", "Ranged", "Only optimise ranged"},
			{"MAGIC", "Magic", "Only optimise magic"},
		}, config.styleFilter(), value -> changeSetting("styleFilter", value)));
		bar.add(styles, BorderLayout.CENTER);

		JPanel account = new JPanel(new BorderLayout(8, 0));
		account.setOpaque(false);
		profileCombo.setFocusable(false);
		profileCombo.setPreferredSize(new Dimension(190, 26));
		profileCombo.addActionListener(e ->
		{
			if (updatingControls)
			{
				return;
			}
			Choice option = (Choice) profileCombo.getSelectedItem();
			plugin.viewProfile(option == null ? null : option.value);
		});
		account.add(bankLabel, BorderLayout.CENTER);
		account.add(profileCombo, BorderLayout.EAST);
		bankLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		bar.add(account, BorderLayout.EAST);
		return bar;
	}

	private JScrollPane sidebar()
	{
		JPanel column = new Ui.WidthTrackingPanel(new GridBagLayout());
		column.setBackground(Ui.BACKGROUND);
		column.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTH;
		c.insets = new Insets(0, 0, 8, 0);

		column.add(monsterCard.getPanel(), c);
		column.add(locationsCard.getPanel(), c);
		column.add(levelsCard.getPanel(), c);
		c.weighty = 1;
		JPanel filler = new JPanel();
		filler.setOpaque(false);
		column.add(filler, c);

		JScrollPane scroll = Ui.scroll(column, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, false);
		scroll.setPreferredSize(new Dimension(SIDEBAR_WIDTH + 16, 100));
		return scroll;
	}

	/**
	 * @return the skill's small icon, made once
	 */
	private ImageIcon skillIcon(Skill skill)
	{
		return skillIconCache.computeIfAbsent(skill, s -> new ImageIcon(skillIcons.getSkillImage(s, true)));
	}

	// ---- Updates from the plugin ----

	public void onDataLoaded()
	{
		if (lastView == null)
		{
			showEmptyState();
		}
		else
		{
			renderTabs(lastView);
		}
		search.update();
	}

	/**
	 * @param place where the player fights it, one of its location names, or null
	 */
	public void showMonster(Monster monster, String place)
	{
		shownMonster = monster;
		monsterCard.show(monster);
		locationsCard.show(repository.getLocations(monster.getId()), place);
	}

	public void showCalculating()
	{
		showStatus("Calculating...");
	}

	public void showStatus(String text)
	{
		statusLabel.setText(text);
	}

	public void showResult(OptimizerView view)
	{
		lastView = view;
		experiments.useMonster(view.getMonster());
		levelsCard.show(view);
		ownedItemsCache = loadOwnedItems();
		ownedStatIds.clear();
		for (OwnedItem item : ownedItemsCache)
		{
			ownedStatIds.add(item.getStats().getId());
		}
		// A new calculation invalidates the max DPS result (any older pending run is dropped by the plugin)
		maxView = null;
		maxPending = false;
		if (maxMode)
		{
			requestMaxDps();
		}
		for (StyleResult style : view.getResult().getStyles().values())
		{
			for (Map.Entry<Integer, Integer> price : style.getPrices().entrySet())
			{
				prices.put(price.getKey(), price.getValue());
			}
		}
		for (UpgradeFinder.Upgrade upgrade : view.getUpgrades().all())
		{
			prices.put(upgrade.getItem().getId(), upgrade.getPrice());
		}
		refreshProfiles();
		updateBankLabel(view.getAccount());

		renderTabs(view);
		showStatus(statusText(view));
	}

	// ---- Tabs ----

	/**
	 * Rebuilds every tab in a fixed order (melee, ranged, magic, then the overview tabs) and
	 * re-selects the tab the player was on.
	 */
	private void renderTabs(OptimizerView view)
	{
		Optimizer.Result result = view.getResult();
		String keep = selectedTabKey;
		updatingControls = true;
		try
		{
			clearTabs();
			for (CombatStyle style : CombatStyle.values())
			{
				StyleResult recommended = result.getStyles().get(style);
				if (recommended == null)
				{
					continue;
				}
				boolean best = recommended == result.getBest();
				StyleTab tab = buildTab(recommended, best, view);
				styleTabs.put(style, tab);
				addTab(style.name(), tabTitle(tab, best), skillIcon(skillOf(style)), tab);
			}
			if (styleTabs.isEmpty())
			{
				addTab("Result", "Result", null, messagePanel(view.getAccount().hasBank()
					? "None of your gear can attack this monster with the selected styles."
					: "Open your bank once so the optimizer knows what you own."));
			}
			addTab(TAB_COMPARE, TAB_COMPARE, null, compareTab(view));
			addTab(TAB_DATA, TAB_DATA, null, new DataTab(plugin, config, repository, result, account()));

			int index = keep == null ? -1 : tabKeys.indexOf(keep);
			if (index < 0 && result.getBest() != null)
			{
				index = tabKeys.indexOf(result.getBest().getStyle().name());
			}
			tabs.setSelectedIndex(Math.max(0, index));
		}
		finally
		{
			updatingControls = false;
		}
		onTabChanged();
	}

	/**
	 * Rebuilds the tabs of some styles, and the comparison that shows every style, without touching the others. The
	 * tabs themselves stay: only the content of their holders and their titles change, so the order, icons and
	 * selection stay put.
	 */
	private void refreshStyleTabs(CombatStyle... styles)
	{
		Optimizer.Result result = lastView.getResult();
		updatingControls = true;
		try
		{
			for (CombatStyle style : styles)
			{
				StyleResult recommended = result.getStyles().get(style);
				int index = tabKeys.indexOf(style.name());
				if (recommended == null || index < 0)
				{
					continue;
				}
				boolean best = recommended == result.getBest();
				StyleTab tab = buildTab(recommended, best, lastView);
				styleTabs.put(style, tab);
				setTabContent(style.name(), tab);
				tabs.setTitleAt(index, tabTitle(tab, best));
			}
			setTabContent(TAB_COMPARE, compareTab(lastView));
			int selected = tabKeys.indexOf(selectedTabKey);
			if (selected >= 0)
			{
				tabs.setSelectedIndex(selected);
			}
		}
		finally
		{
			updatingControls = false;
		}
		onTabChanged();
	}

	private void clearTabs()
	{
		tabs.removeAll();
		tabKeys.clear();
		tabPanels.clear();
		styleTabs.clear();
	}

	/**
	 * Adds a tab whose content sits in a holder panel, so it can be swapped later without replacing the tab itself:
	 * replacing a tab's component makes the tabbed pane draw HTML titles on the wrong tabs.
	 */
	private void addTab(String key, String title, Icon icon, Component content)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(Ui.BACKGROUND);
		holder.add(content, BorderLayout.CENTER);
		tabs.addTab(title, icon, holder);
		tabKeys.add(key);
		tabPanels.put(key, holder);
	}

	private void setTabContent(String key, Component content)
	{
		JPanel holder = tabPanels.get(key);
		holder.removeAll();
		holder.add(content, BorderLayout.CENTER);
		holder.revalidate();
		holder.repaint();
	}

	/**
	 * @param best the style's recommendation is the best style
	 * @return the style's name, whether it shows the max DPS or an edited setup, and its DPS
	 */
	private static String tabTitle(StyleTab tab, boolean best)
	{
		boolean maxShown = tab.isMaxView();
		Color color = maxShown ? Ui.MAX_GOLD : best ? ColorScheme.BRAND_ORANGE : Ui.MUTED;
		return "<html>" + tab.getStyle().getDisplayName() + (maxShown ? " max" : tab.isEdited() ? " (custom)" : "") + " "
			+ Ui.colored(String.format("%.2f", tab.getShown().effectiveDps()), color) + "</html>";
	}

	/**
	 * The comparison of the setups the style tabs show, so it has to be built after them.
	 */
	private CompareTab compareTab(OptimizerView view)
	{
		List<StyleResult> shown = new ArrayList<>();
		for (StyleTab tab : styleTabs.values())
		{
			shown.add(tab.getShown());
		}
		Optimizer.Result result = view.getResult();
		return new CompareTab(view.getMonster(), shown, result.getBest(), result.getCurrent(), itemIcons, style -> selectTab(style.name()));
	}

	private static Skill skillOf(CombatStyle style)
	{
		switch (style)
		{
			case MELEE:
				return Skill.ATTACK;
			case RANGED:
				return Skill.RANGED;
			default:
				return Skill.MAGIC;
		}
	}

	private void selectTab(String key)
	{
		int index = tabKeys.indexOf(key);
		if (index >= 0)
		{
			tabs.setSelectedIndex(index);
		}
	}

	private void onTabChanged()
	{
		if (updatingControls || lastView == null)
		{
			return;
		}
		int index = tabs.getSelectedIndex();
		if (index < 0 || index >= tabKeys.size())
		{
			return;
		}
		selectedTabKey = tabKeys.get(index);
		for (StyleTab tab : styleTabs.values())
		{
			if (tab.getStyle().name().equals(selectedTabKey))
			{
				selectedStyle = tab.getStyle();
				plugin.highlight(tab.getShown());
			}
		}
	}

	private StyleTab buildTab(StyleResult recommended, boolean best, OptimizerView view)
	{
		CombatStyle style = recommended.getStyle();
		StyleResult max = maxMode && maxView != null ? maxView.getResult().getStyles().get(style) : null;
		TabModel.TabModelBuilder model = TabModel.builder()
			.current(view.getResult().getCurrent())
			.upgrades(view.getUpgrades())
			.ownedIds(ownedStatIds)
			.prices(prices);
		if (max != null)
		{
			model.result(max).ownedBest(recommended);
		}
		else
		{
			boolean edited = experiments.isEdited(style);
			model.result(experiments.shown(recommended, view.getResult().getRules(), view.getAccount()))
				.best(best && !edited)
				.recommended(edited ? recommended : null)
				.experimentNote(experiments.note(style))
				.initialSlot(experiments.slot(style))
				.maxLoading(maxMode && maxView == null);
		}
		return new StyleTab(model.build(), this, itemIcons, tabState);
	}

	private String statusText(OptimizerView view)
	{
		StringBuilder sb = new StringBuilder();
		if (view.getSlayerTask() != null || view.isOnTask())
		{
			sb.append(view.isOnTask() ? "On slayer task" : "Not on task")
				.append(view.getSlayerTask() == null ? "" : " (" + view.getSlayerTask() + ")").append(".  ");
		}
		StyleResult best = view.getResult().getBest();
		if (best == null)
		{
			return sb.append("No loadout found").toString();
		}
		for (String note : view.getResult().getRules().getNotes())
		{
			sb.append(note).append("  ");
		}
		return sb.append(String.format("Best: %s at %.2f DPS, about %s per kill.", best.getStyle().getDisplayName().toLowerCase(),
			best.effectiveDps(), Ui.formatSeconds(best.timeToKill()))).toString();
	}

	private static JPanel messagePanel(String text)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(Ui.BACKGROUND);
		panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
		JLabel label = Ui.regular("<html><div style='text-align:center'>" + Ui.escape(text) + "</div></html>", Ui.MUTED);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		panel.add(label, BorderLayout.NORTH);
		return panel;
	}

	private void showEmptyState()
	{
		clearTabs();
		addTab("Start", "Start", null, messagePanel("Search for a monster at the top left, or attack one in game and press Use target. "
			+ "Open your bank once so the optimizer knows what you own."));
		addTab(TAB_DATA, TAB_DATA, null, new DataTab(plugin, config, repository, null, account()));
		monsterCard.showNone();
		locationsCard.showNone();
		updateBankLabel(accountSync.getSnapshot());
		refreshProfiles();
	}

	// ---- StyleTab.Actions ----

	@Override
	public void toggleExcluded(int itemId)
	{
		plugin.toggleItemSet("excludedItems", itemId);
	}

	@Override
	public void toggleLocked(int itemId)
	{
		plugin.toggleItemSet("lockedItems", itemId);
	}

	@Override
	public Set<Integer> locked()
	{
		return plugin.getLocked();
	}

	@Override
	public List<OwnedItem> ownedItems()
	{
		return ownedItemsCache;
	}

	@Override
	public List<EquipmentItem> allItems()
	{
		return repository.getEquipment();
	}

	@Override
	public MonsterRules rules()
	{
		return lastView.getResult().getRules();
	}

	@Override
	public Long price(int itemId)
	{
		return prices.get(itemId);
	}

	@Override
	public void requestPrices(Collection<Integer> itemIds, Component owner, Runnable onLoaded)
	{
		prices.request(itemIds, owner, onLoaded);
	}

	@Override
	public GearOptimizerConfig settings()
	{
		return config;
	}

	@Override
	public void changeSetting(String key, Object value)
	{
		// The plugin recalculates every tab when a setting changes
		configManager.setConfiguration(GearOptimizerConfig.GROUP, key, value);
	}

	@Override
	public SpriteManager spriteManager()
	{
		return spriteManager;
	}

	@Override
	public boolean isMaxMode()
	{
		return maxMode;
	}

	@Override
	public void setMaxMode(boolean on)
	{
		maxMode = on;
		if (on && maxView == null)
		{
			requestMaxDps();
			showStatus("Finding the best gear in the game...");
		}
		if (lastView != null)
		{
			refreshStyleTabs(CombatStyle.values());
		}
	}

	private void requestMaxDps()
	{
		if (maxPending)
		{
			return;
		}
		maxPending = true;
		plugin.computeMaxDps(view ->
		{
			maxPending = false;
			maxView = view;
			prices.putAll(view.getPrices());
			// Without max mode the tabs don't show the result yet
			if (lastView != null && maxMode)
			{
				refreshStyleTabs(CombatStyle.values());
				showStatus("Showing max DPS gear: the best items in the game within your levels");
			}
		});
	}

	@Override
	public AccountSnapshot account()
	{
		return lastView == null ? accountSync.getSnapshot() : lastView.getAccount();
	}

	@Override
	public void showInBank(StyleResult shown, Runnable onDone)
	{
		plugin.showInBank(shown, account(), message ->
		{
			showStatus(message);
			onDone.run();
		});
	}

	// ---- Experiments ----

	/**
	 * Rebuilds a style's tab after its setup was edited, and opens it.
	 */
	private void rebuildTab(CombatStyle style)
	{
		if (lastView == null)
		{
			return;
		}
		selectedTabKey = style.name();
		refreshStyleTabs(style);
	}

	@Override
	public void chooseItem(CombatStyle style, GearSlot slot)
	{
		if (lastView == null)
		{
			return;
		}
		StyleTab tab = styleTabs.get(style);
		EquipmentItem weapon = tab == null ? null : tab.getShown().getLoadout().weapon();
		new ItemChooser(this, slot, ownedItems(), repository.getEquipment(), weapon, itemIcons,
			item -> tryItem(style, slot, item)).setVisible(true);
	}

	@Override
	public void tryItem(CombatStyle style, GearSlot slot, OwnedItem item)
	{
		StyleTab tab = styleTabs.get(style);
		if (tab == null)
		{
			return;
		}
		Loadout edited = new Loadout(tab.getShown().getLoadout());
		List<OwnedItem> ammo = new ArrayList<>();
		for (OwnedItem owned : ownedItems())
		{
			if (owned.getStats().getGearSlot() == GearSlot.AMMO)
			{
				ammo.add(owned);
			}
		}
		StyleResult recommended = lastView.getResult().getStyles().get(style);
		String note = Experiment.apply(edited, slot, item, ammo, recommended.getLoadout().get(GearSlot.AMMO), style, recommended.getContext());
		experiments.selectSlot(style, slot);
		if (Experiment.sameItems(edited, recommended.getLoadout()))
		{
			// Every slot is back to the recommendation
			experiments.reset(style);
		}
		else
		{
			String changed = "Changed " + slot.getDisplayName().toLowerCase() + " to " + (item == null ? "nothing" : item.getStats().getName())
				+ (item != null && !isOwned(item) ? " (not owned)" : "");
			experiments.edit(style, edited, note == null ? changed : changed + ". " + note);
		}
		rebuildTab(style);
	}

	@Override
	public void tryOption(CombatStyle style, Consumables.Option option)
	{
		experiments.edit(style, new Loadout(option.getLoadout()), "Using " + option.getLabel() + " (" + Ui.gp(option.getCost().getPerHour())
			+ "/hr)");
		rebuildTab(style);
	}

	@Override
	public void tryDart(CombatStyle style, OwnedItem dart)
	{
		StyleTab tab = styleTabs.get(style);
		if (tab == null)
		{
			return;
		}
		Loadout edited = new Loadout(tab.getShown().getLoadout());
		edited.setBlowpipeDart(dart);
		if (Experiment.sameItems(edited, lastView.getResult().getStyles().get(style).getLoadout()))
		{
			experiments.reset(style);
		}
		else
		{
			experiments.edit(style, edited, "Blowpipe loaded with " + dart.getStats().getName() + (isOwned(dart) ? "" : " (not owned)"));
		}
		rebuildTab(style);
	}

	@Override
	public void resetExperiment(CombatStyle style)
	{
		experiments.reset(style);
		rebuildTab(style);
	}

	@Override
	public void slotSelected(CombatStyle style, GearSlot slot)
	{
		experiments.selectSlot(style, slot);
	}

	private boolean isOwned(OwnedItem item)
	{
		return ownedStatIds.contains(item.getStats().getId());
	}

	private List<OwnedItem> loadOwnedItems()
	{
		List<OwnedItem> owned = new ArrayList<>();
		if (lastView == null)
		{
			return owned;
		}
		for (int id : lastView.getAccount().ownedItemIds())
		{
			OwnedItem item = repository.resolve(id);
			if (item != null)
			{
				owned.add(item);
			}
		}
		return owned;
	}

	// ---- Account ----

	private void refreshProfiles()
	{
		updatingControls = true;
		try
		{
			Choice selected = (Choice) profileCombo.getSelectedItem();
			String selectedKey = selected == null ? null : selected.value;
			DefaultComboBoxModel<Choice> model = new DefaultComboBoxModel<>();
			model.addElement(new Choice(null, "Logged-in account"));
			String current = accountSync.getCurrentProfileKey();
			for (RuneScapeProfile profile : accountSync.getProfiles())
			{
				if (!profile.getKey().equals(current))
				{
					model.addElement(new Choice(profile.getKey(), profile.getDisplayName() == null ? "Saved account" : profile.getDisplayName()));
				}
			}
			profileCombo.setModel(model);
			for (int i = 0; i < model.getSize(); i++)
			{
				if (Objects.equals(model.getElementAt(i).value, selectedKey))
				{
					profileCombo.setSelectedIndex(i);
				}
			}
			profileCombo.setVisible(model.getSize() > 1);
		}
		finally
		{
			updatingControls = false;
		}
	}

	private void updateBankLabel(AccountSnapshot account)
	{
		if (account == null || !account.hasBank())
		{
			bankLabel.setText("Bank not synced: open your bank once");
			bankLabel.setForeground(Ui.WARN);
			bankLabel.setToolTipText(null);
			return;
		}
		long minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - account.getBankUpdatedAt());
		String age = minutes < 1 ? "just now" : minutes < 60 ? minutes + " min ago"
			: minutes < 1440 ? (minutes / 60) + " h ago" : (minutes / 1440) + " days ago";
		String text = String.format("Bank synced %s, %d items", age, account.getBank().size());
		int putAway = account.putAwayCount();
		bankLabel.setText(putAway > 0 ? text + ", " + putAway + " put away" : text);
		bankLabel.setToolTipText(putAway > 0 ? "Gear that left your inventory or equipment while the bank was closed, like at a"
			+ " deposit box. It still counts as yours until you next open your bank." : null);
		bankLabel.setForeground(minutes > 1440 * 7 ? Ui.WARN : Ui.MUTED);
	}

	// ---- Controls ----

	private static final class Choice
	{
		final String value;
		final String label;

		Choice(String value, String label)
		{
			this.value = value;
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
