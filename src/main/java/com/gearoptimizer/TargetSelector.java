package com.gearoptimizer;

import com.gearoptimizer.data.GameDataRepository;
import com.gearoptimizer.data.Monster;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.eventbus.Subscribe;

/**
 * Picks monsters from the game: the "Gear setup" option on monsters, following what the player attacks, and the
 * window's "Use target" button. The menu option only opens the window; nothing is sent to the game server.
 */
@Singleton
class TargetSelector
{
	private static final BiConsumer<Monster, WorldPoint> IGNORE = (monster, seen) ->
	{
	};

	private final Client client;
	private final GearOptimizerConfig config;
	private final GameDataRepository repository;

	private volatile int lastTargetNpcId = -1;
	/** Where the last monster attacked was standing. */
	private volatile WorldPoint lastTargetLocation;
	/** Called on the EDT with the monster and where it stood when the player attacks it and auto-select is on. */
	private volatile BiConsumer<Monster, WorldPoint> onTarget = IGNORE;
	/** Called on the EDT with the monster and where it stood when the player clicks "Gear setup" on it. */
	private volatile BiConsumer<Monster, WorldPoint> onMenu = IGNORE;

	@Inject
	TargetSelector(Client client, GearOptimizerConfig config, GameDataRepository repository)
	{
		this.client = client;
		this.config = config;
		this.repository = repository;
	}

	void start(BiConsumer<Monster, WorldPoint> onTarget, BiConsumer<Monster, WorldPoint> onMenu)
	{
		this.onTarget = onTarget;
		this.onMenu = onMenu;
	}

	void stop()
	{
		onTarget = IGNORE;
		onMenu = IGNORE;
		lastTargetNpcId = -1;
		lastTargetLocation = null;
	}

	/**
	 * @return the last known monster the player attacked, or null
	 */
	Monster lastTarget()
	{
		return repository.getMonster(lastTargetNpcId);
	}

	/**
	 * @return where the last monster attacked was standing, or null
	 */
	WorldPoint lastTargetLocation()
	{
		return lastTargetLocation;
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (event.getSource() != client.getLocalPlayer() || !(event.getTarget() instanceof NPC))
		{
			return;
		}
		NPC npc = (NPC) event.getTarget();
		lastTargetNpcId = npc.getId();
		WorldPoint seen = npc.getWorldLocation();
		lastTargetLocation = seen;
		Monster monster = repository.getMonster(lastTargetNpcId);
		if (monster != null && config.autoSelectTarget())
		{
			BiConsumer<Monster, WorldPoint> target = onTarget;
			SwingUtilities.invokeLater(() -> target.accept(monster, seen));
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		MenuEntry entry = event.getMenuEntry();
		if (!config.npcMenuOption() || entry.getType() != MenuAction.EXAMINE_NPC || entry.getNpc() == null)
		{
			return;
		}
		Monster monster = repository.getMonster(entry.getNpc().getId());
		if (monster == null)
		{
			return;
		}
		WorldPoint seen = entry.getNpc().getWorldLocation();
		client.getMenu().createMenuEntry(-1)
			.setOption("Gear setup")
			.setTarget(entry.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e ->
			{
				BiConsumer<Monster, WorldPoint> menu = onMenu;
				SwingUtilities.invokeLater(() -> menu.accept(monster, seen));
			});
	}
}
