package com.gearoptimizer;

import com.gearoptimizer.data.MonsterLocation;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.worldmap.WorldMapData;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Shows where a monster lives: pins on the world map, and a route drawn by the Shortest Path plugin when it's
 * turned on. Shortest Path is asked through RuneLite's plugin messages; nothing is sent to the game server.
 */
@Singleton
class LocationService
{
	private static final String SHORTEST_PATH = "shortestpath";
	private static final String SHORTEST_PATH_NAME = "Shortest Path";
	private static final Color MONSTER_PIN = new Color(200, 50, 40);
	private static final Color ENTRANCE_PIN = new Color(230, 180, 50);

	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final WorldMapPointManager worldMapPoints;
	private final PluginManager pluginManager;
	private final SpriteManager spriteManager;
	private final WorldMapButton mapButton;

	/** Pins this plugin added. Client thread only. */
	private final List<WorldMapPoint> pins = new ArrayList<>();
	/** The pin images, made once the game's sprites have loaded. Client thread only. */
	private BufferedImage monsterPin;
	private BufferedImage entrancePin;
	/** Where the world map button centres the map, best first. Client thread only. */
	private List<WorldPoint> focus = Collections.emptyList();
	/** Where to centre the world map the next time it opens, best first. Client thread only. */
	private List<WorldPoint> pendingFocus;
	/** Whether the route Shortest Path shows is one this plugin asked for. Client thread only. */
	private boolean routeShown;

	@Inject
	LocationService(Client client, ClientThread clientThread, EventBus eventBus, WorldMapPointManager worldMapPoints,
		PluginManager pluginManager, SpriteManager spriteManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		this.worldMapPoints = worldMapPoints;
		this.pluginManager = pluginManager;
		this.spriteManager = spriteManager;
		this.mapButton = new WorldMapButton(client, () -> centre(focus));
	}

	void stop()
	{
		clientThread.invoke(() ->
		{
			removePins();
			pendingFocus = null;
			mapButton.stop();
		});
	}

	/**
	 * Pins each place on the world map, and the surface entrances of those underground. Centres the map on the
	 * nearest, now if it's open or else when it opens.
	 *
	 * @param onDone gets a message for the window, on the EDT
	 */
	void showOnMap(String monsterName, List<MonsterLocation> locations, Consumer<String> onDone)
	{
		List<MonsterLocation> placed = locations.stream().filter(MonsterLocation::hasPoints).collect(Collectors.toList());
		if (placed.isEmpty())
		{
			onDone.accept("The wiki gives no map position for " + monsterName);
			return;
		}
		clientThread.invoke(() ->
		{
			removePins();
			loadPinImages();
			for (MonsterLocation location : placed)
			{
				addPin(location.center(), false, monsterName, monsterName + ": " + location.getName());
				for (WorldPoint entrance : location.getEntrances())
				{
					addPin(entrance, true, monsterName, monsterName + ": entrance to " + location.getName());
				}
			}

			MonsterLocation nearest = nearest(placed);
			focus = focusPoints(nearest);
			mapButton.show(monsterName, monsterPin);
			boolean mapOpen = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) != null;
			if (mapOpen)
			{
				centre(focus);
			}
			else
			{
				pendingFocus = focus;
			}

			String message = "Pinned " + nearest.getName();
			if (nearest.isUnderground())
			{
				message += nearest.getEntrances().isEmpty()
					? ". It's underground and the wiki doesn't mark the entrance: it shows on that area's own map, or press Go for a route"
					: " and its entrance (the gold pin)";
			}
			message += mapOpen ? " on the world map" : ": open the world map to see it";
			String text = message;
			SwingUtilities.invokeLater(() -> onDone.accept(text));
		});
	}

	/**
	 * Asks Shortest Path for a route to whichever of the places is quickest to reach.
	 *
	 * @param onDone gets a message for the window, on the EDT
	 */
	void walkTo(String monsterName, List<MonsterLocation> locations, Consumer<String> onDone)
	{
		Set<WorldPoint> targets = new HashSet<>();
		locations.forEach(location -> targets.addAll(location.getPoints()));
		if (targets.isEmpty())
		{
			onDone.accept("The wiki gives no map position for " + monsterName);
			return;
		}
		clientThread.invoke(() ->
		{
			String message;
			if (!shortestPathActive())
			{
				message = "Turn on the Shortest Path plugin (from the Plugin Hub) to get a route";
			}
			else if (client.getGameState() != GameState.LOGGED_IN)
			{
				message = "Log in to get a route";
			}
			else
			{
				Map<String, Object> data = new HashMap<>();
				data.put("target", targets);
				eventBus.post(new PluginMessage(SHORTEST_PATH, "path", data));
				routeShown = true;
				message = "Shortest Path is showing the way to " + (locations.size() == 1
					? locations.get(0).getName() : "the nearest " + monsterName);
			}
			SwingUtilities.invokeLater(() -> onDone.accept(message));
		});
	}

	/**
	 * Removes the pins, and the route if this plugin asked for the one shown.
	 */
	void clear(Consumer<String> onDone)
	{
		clientThread.invoke(() ->
		{
			removePins();
			pendingFocus = null;
			focus = Collections.emptyList();
			mapButton.show(null, null);
			if (routeShown && shortestPathActive())
			{
				eventBus.post(new PluginMessage(SHORTEST_PATH, "clear", new HashMap<>()));
			}
			routeShown = false;
			SwingUtilities.invokeLater(() -> onDone.accept("Cleared the map pins and route"));
		});
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.WORLDMAP)
		{
			return;
		}
		clientThread.invokeLater(mapButton::refresh);
		if (pendingFocus == null)
		{
			return;
		}
		List<WorldPoint> points = pendingFocus;
		pendingFocus = null;
		// The map centres on the player as it opens; move it once it has its data
		clientThread.invokeLater(() ->
		{
			if (client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) == null)
			{
				return true;
			}
			if (client.getWorldMap().getWorldMapData() == null)
			{
				return false;
			}
			centre(points);
			return true;
		});
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// The map rebuilds its bottom bar now and then, dropping the button
		if (!pins.isEmpty() && client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) != null)
		{
			mapButton.refresh();
		}
	}

	/**
	 * Makes the pins: red with the Slayer skull for monsters, gold with the world map's dungeon icon for entrances.
	 */
	private void loadPinImages()
	{
		if (monsterPin == null)
		{
			BufferedImage skull = spriteManager.getSprite(SpriteID.Staticons2.SLAYER, 0);
			BufferedImage dungeon = spriteManager.getSprite(SpriteID.Mapfunction.DUNGEON, 0);
			if (skull == null || dungeon == null)
			{
				// Not loaded yet: plain pins this time
				monsterPin = null;
				entrancePin = null;
				return;
			}
			monsterPin = GameButtons.pin(MONSTER_PIN, skull);
			entrancePin = GameButtons.pin(ENTRANCE_PIN, dungeon);
		}
	}

	private void addPin(WorldPoint point, boolean entrance, String name, String tooltip)
	{
		BufferedImage image = entrance ? entrancePin : monsterPin;
		if (image == null)
		{
			image = GameButtons.pin(entrance ? ENTRANCE_PIN : MONSTER_PIN, null);
		}
		// Pins that jump on click need a name: RuneLite shows it in their "Focus on" option
		WorldMapPoint pin = WorldMapPoint.builder()
			.worldPoint(point)
			.image(image)
			// The pin's tip marks the spot
			.imagePoint(new Point(image.getWidth() / 2, image.getHeight() - 1))
			.name(name)
			.tooltip(tooltip)
			.snapToEdge(true)
			.jumpOnClick(true)
			.build();
		pins.add(pin);
		worldMapPoints.add(pin);
	}

	/**
	 * Moves the open world map to the first of the points the map it shows has: inside a dungeon that's the
	 * monsters, on the surface their entrance.
	 */
	private void centre(List<WorldPoint> points)
	{
		WorldMapData shown = client.getWorldMap().getWorldMapData();
		WorldPoint target = points.stream()
			.filter(p -> shown != null && shown.surfaceContainsPosition(p.getX(), p.getY()))
			.findFirst()
			.orElse(points.get(0));
		client.getWorldMap().setWorldMapPositionTarget(target);
	}

	/**
	 * @return the place's pin, then its entrances nearest the player first
	 */
	private List<WorldPoint> focusPoints(MonsterLocation location)
	{
		List<WorldPoint> points = new ArrayList<>();
		points.add(location.center());
		WorldPoint here = playerLocation();
		location.getEntrances().stream()
			.sorted(Comparator.comparingInt(p -> here == null ? 0 : p.distanceTo2D(here)))
			.forEach(points::add);
		return points;
	}

	/**
	 * @return the place nearest the player, measured to its entrances too since a dungeon's own coordinates are far
	 * from the surface above it
	 */
	private MonsterLocation nearest(List<MonsterLocation> locations)
	{
		WorldPoint here = playerLocation();
		if (here == null)
		{
			return locations.get(0);
		}
		MonsterLocation best = locations.get(0);
		int bestDistance = Integer.MAX_VALUE;
		for (MonsterLocation location : locations)
		{
			List<WorldPoint> points = new ArrayList<>(location.getPoints());
			points.addAll(location.getEntrances());
			for (WorldPoint point : points)
			{
				int distance = point.distanceTo2D(here);
				if (distance < bestDistance)
				{
					best = location;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	private WorldPoint playerLocation()
	{
		Player player = client.getLocalPlayer();
		return player == null ? null : player.getWorldLocation();
	}

	private void removePins()
	{
		pins.forEach(worldMapPoints::remove);
		pins.clear();
	}

	private boolean shortestPathActive()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (SHORTEST_PATH_NAME.equals(plugin.getName()))
			{
				return pluginManager.isPluginActive(plugin);
			}
		}
		return false;
	}
}
