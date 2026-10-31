package com.gearoptimizer;

import com.gearoptimizer.data.GameDataRepository;
import com.gearoptimizer.data.Monster;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
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
	private static final Consumer<Monster> IGNORE = monster ->
	{
	};

	private final Client client;
	private final GearOptimizerConfig config;
	private final GameDataRepository repository;

	private volatile int lastTargetNpcId = -1;
	/** Called on the EDT when the player attacks a known monster and auto-select is on. */
	private volatile Consumer<Monster> onTarget = IGNORE;
	/** Called on the EDT when the player clicks "Gear setup" on a monster. */
	private volatile Consumer<Monster> onMenu = IGNORE;

	@Inject
	TargetSelector(Client client, GearOptimizerConfig config, GameDataRepository repository)
	{
		this.client = client;
		this.config = config;
		this.repository = repository;
	}

	void start(Consumer<Monster> onTarget, Consumer<Monster> onMenu)
	{
		this.onTarget = onTarget;
		this.onMenu = onMenu;
	}

	void stop()
	{
		onTarget = IGNORE;
		onMenu = IGNORE;
		lastTargetNpcId = -1;
	}

	/**
	 * @return the last known monster the player attacked, or null
	 */
	Monster lastTarget()
	{
		return repository.getMonster(lastTargetNpcId);
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (event.getSource() != client.getLocalPlayer() || !(event.getTarget() instanceof NPC))
		{
			return;
		}
		lastTargetNpcId = ((NPC) event.getTarget()).getId();
		Monster monster = repository.getMonster(lastTargetNpcId);
		if (monster != null && config.autoSelectTarget())
		{
			Consumer<Monster> target = onTarget;
			SwingUtilities.invokeLater(() -> target.accept(monster));
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
		client.getMenu().createMenuEntry(-1)
			.setOption("Gear setup")
			.setTarget(entry.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e ->
			{
				Consumer<Monster> menu = onMenu;
				SwingUtilities.invokeLater(() -> menu.accept(monster));
			});
	}
}
