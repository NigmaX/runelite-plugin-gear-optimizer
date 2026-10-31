package com.gearoptimizer.account;

import com.gearoptimizer.GearOptimizerConfig;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;

/**
 * Keeps an {@link AccountSnapshot} of the logged-in account in sync: bank (whenever it is open),
 * inventory, equipment, rune pouch, levels and relevant unlocks. Each account is saved to its own
 * RuneScape profile, so alts keep separate snapshots.
 */
@Slf4j
@Singleton
public class AccountSyncService
{
	private static final String CONFIG_GROUP = GearOptimizerConfig.GROUP;
	private static final String SNAPSHOT_KEY = "accountSnapshot";
	private static final int SAVE_INTERVAL_TICKS = 10;

	private static final Skill[] TRACKED_SKILLS = {
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC, Skill.PRAYER,
		Skill.HITPOINTS, Skill.SLAYER, Skill.AGILITY,
	};

	private static final int[] RUNE_POUCH_TYPES = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6,
	};
	private static final int[] RUNE_POUCH_QUANTITIES = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6,
	};

	private final Client client;
	private final ClientThread clientThread;
	private final ConfigManager configManager;
	private final ItemManager itemManager;
	private final Gson gson;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	/** The logged-in account's snapshot. Only mutated on the client thread. */
	private AccountSnapshot live = new AccountSnapshot();
	/** A copy of {@link #live} handed to other threads; replaced, never changed. */
	private volatile AccountSnapshot published = new AccountSnapshot();

	private boolean dirty;
	private boolean refreshVars;
	private int ticksSinceSave;

	@Inject
	AccountSyncService(Client client, ClientThread clientThread, ConfigManager configManager, ItemManager itemManager, Gson gson)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.configManager = configManager;
		this.itemManager = itemManager;
		this.gson = gson;
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public void removeListener(Runnable listener)
	{
		listeners.remove(listener);
	}

	/**
	 * @return an immutable-by-convention copy of the logged-in account's data; safe on any thread
	 */
	public AccountSnapshot getSnapshot()
	{
		return published;
	}

	/**
	 * Called on the client thread at plugin start-up.
	 */
	public void start()
	{
		live = loadSnapshot(null);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			readContainers();
			refreshVars = true;
		}
		publish();
	}

	/**
	 * Saves unsaved changes. The snapshot belongs to the client thread, so the save runs there.
	 */
	public void stop()
	{
		clientThread.invoke(() ->
		{
			if (dirty)
			{
				save();
			}
		});
	}

	/**
	 * @return profiles that have a saved snapshot, for the account picker
	 */
	public List<RuneScapeProfile> getProfiles()
	{
		List<RuneScapeProfile> result = new ArrayList<>();
		for (RuneScapeProfile profile : configManager.getRSProfiles())
		{
			if (profile.getType() == RuneScapeProfileType.STANDARD
				&& configManager.getConfiguration(CONFIG_GROUP, profile.getKey(), SNAPSHOT_KEY) != null)
			{
				result.add(profile);
			}
		}
		return result;
	}

	public String getCurrentProfileKey()
	{
		return configManager.getRSProfileKey();
	}

	/**
	 * Loads another account's saved snapshot (read only).
	 */
	public AccountSnapshot loadSnapshot(String profileKey)
	{
		String json = profileKey == null
			? configManager.getRSProfileConfiguration(CONFIG_GROUP, SNAPSHOT_KEY)
			: configManager.getConfiguration(CONFIG_GROUP, profileKey, SNAPSHOT_KEY);
		if (json == null || json.isEmpty())
		{
			return new AccountSnapshot();
		}
		try
		{
			AccountSnapshot snapshot = gson.fromJson(json, AccountSnapshot.class);
			return snapshot == null ? new AccountSnapshot() : snapshot;
		}
		catch (JsonParseException e)
		{
			log.warn("Discarding unreadable gear optimizer snapshot", e);
			return new AccountSnapshot();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		live = loadSnapshot(null);
		readContainers();
		refreshVars = true;
		publish();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			refreshVars = true;
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN && dirty)
		{
			save();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		boolean changed;
		if (id == InventoryID.BANK)
		{
			Map<Integer, Integer> bank = toQuantities(event.getItemContainer());
			changed = !bank.equals(live.getBank());
			live.setBank(bank);
			live.setBankUpdatedAt(System.currentTimeMillis());
			dirty = true;
		}
		else if (id == InventoryID.INV)
		{
			// Eating, drinking and casting only change counts; recalculate when the items themselves change
			Map<Integer, Integer> inventory = toQuantities(event.getItemContainer());
			changed = live.getInventory() == null || !inventory.keySet().equals(live.getInventory().keySet());
			live.setInventory(inventory);
			dirty = true;
		}
		else if (id == InventoryID.WORN)
		{
			// Only which items are worn matters, so firing ammo doesn't count as a change
			Map<Integer, Integer> equipment = toSlots(event.getItemContainer());
			changed = !equipment.equals(live.getEquipment());
			live.setEquipment(equipment);
		}
		else
		{
			return;
		}
		if (changed)
		{
			resolveNames();
			dirty = true;
			publish();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		for (Skill skill : TRACKED_SKILLS)
		{
			if (skill == event.getSkill())
			{
				refreshVars = true;
				return;
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varbit = event.getVarbitId();
		if (varbit == VarbitID.PRAYER_RIGOUR_UNLOCKED || varbit == VarbitID.PRAYER_AUGURY_UNLOCKED
			|| varbit == VarbitID.SLAYER_AUTOKILL_GARGOYLES || varbit == VarbitID.KANDARIN_DIARY_HARD_COMPLETE)
		{
			refreshVars = true;
			return;
		}
		for (int i = 0; i < RUNE_POUCH_TYPES.length; i++)
		{
			if (varbit == RUNE_POUCH_TYPES[i] || varbit == RUNE_POUCH_QUANTITIES[i])
			{
				refreshVars = true;
				return;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		// Stat events fire on every XP drop; only real level or unlock changes trigger a recalculation
		if (refreshVars)
		{
			refreshVars = false;
			if (readVars())
			{
				dirty = true;
				publish();
			}
		}

		if (dirty && ++ticksSinceSave >= SAVE_INTERVAL_TICKS)
		{
			save();
		}
	}

	private void readContainers()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv != null)
		{
			live.setInventory(toQuantities(inv));
		}
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			live.setEquipment(toSlots(worn));
		}
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank != null)
		{
			live.setBank(toQuantities(bank));
			live.setBankUpdatedAt(System.currentTimeMillis());
		}
		resolveNames();
	}

	/**
	 * Reads levels, unlocks and the rune pouch into the live snapshot.
	 *
	 * @return whether anything differs from before
	 */
	private boolean readVars()
	{
		AccountSnapshot before = live.copy();
		Map<Skill, Integer> levels = new HashMap<>();
		for (Skill skill : TRACKED_SKILLS)
		{
			levels.put(skill, client.getRealSkillLevel(skill));
		}
		live.setLevels(levels);

		live.setRigourUnlocked(client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED) == 1);
		live.setAuguryUnlocked(client.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED) == 1);
		live.setGargoyleSmasher(client.getVarbitValue(VarbitID.SLAYER_AUTOKILL_GARGOYLES) == 1);
		live.setKandarinHardDiary(client.getVarbitValue(VarbitID.KANDARIN_DIARY_HARD_COMPLETE) == 1);
		live.setAncientMagicks(Quest.DESERT_TREASURE_I.getState(client) == QuestState.FINISHED);

		Map<Integer, Integer> pouch = new HashMap<>();
		EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		for (int i = 0; i < RUNE_POUCH_TYPES.length; i++)
		{
			int type = client.getVarbitValue(RUNE_POUCH_TYPES[i]);
			int quantity = client.getVarbitValue(RUNE_POUCH_QUANTITIES[i]);
			if (type > 0 && quantity > 0 && runeEnum != null)
			{
				pouch.merge(runeEnum.getIntValue(type), quantity, Integer::sum);
			}
		}
		live.setRunePouch(pouch);
		resolveNames();
		return !before.sameLevelsAndUnlocksAs(live);
	}

	private void resolveNames()
	{
		Map<Integer, String> names = live.getItemNames();
		if (names == null)
		{
			names = new HashMap<>();
			live.setItemNames(names);
		}
		for (int id : live.ownedItemIds())
		{
			if (id > 0 && !names.containsKey(id))
			{
				names.put(id, itemManager.getItemComposition(id).getName());
			}
		}
		// Drop names of items the account no longer has, so the saved snapshot stays small
		names.keySet().retainAll(live.ownedItemIds());
	}

	private static Map<Integer, Integer> toQuantities(ItemContainer container)
	{
		Map<Integer, Integer> map = new HashMap<>();
		for (Item item : container.getItems())
		{
			// Bank placeholders have quantity 0
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				map.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return map;
	}

	private static Map<Integer, Integer> toSlots(ItemContainer container)
	{
		Map<Integer, Integer> map = new HashMap<>();
		Item[] items = container.getItems();
		for (int i = 0; i < items.length; i++)
		{
			if (items[i].getId() > 0)
			{
				map.put(i, items[i].getId());
			}
		}
		return map;
	}

	private void save()
	{
		dirty = false;
		ticksSinceSave = 0;
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		configManager.setRSProfileConfiguration(CONFIG_GROUP, SNAPSHOT_KEY, gson.toJson(live));
	}

	private void publish()
	{
		published = live.copy();
		for (Runnable listener : listeners)
		{
			listener.run();
		}
	}
}
