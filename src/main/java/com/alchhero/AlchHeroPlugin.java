package com.alchhero;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientStr;
import net.runelite.api.VarClientInt;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Alch Hero: a Guitar Hero style timing overlay for High Level Alchemy.
 *
 * <p>Setup: fill an inventory row with alchable stacks, set spell filters
 * to show Utility spells only, and enable spellbook resizing so the High
 * Alchemy icon sits over that row. The overlay finds the row the icon is
 * over, draws a four lane fretboard anchored to it, and scores your own
 * manual clicks against a strike line: orange notes for the spell click,
 * blue notes for the item click, rotating across lanes.
 *
 * <p>This plugin performs NO input automation of any kind. It passively
 * observes the timestamps of the player's own clicks via a MouseListener
 * that returns every event unmodified and never calls consume(), so all
 * input passes through to the game untouched, preserving 1:1 input.
 */
@PluginDescriptor(
	name = "Alch Hero",
	description = "Guitar Hero style timing overlay for High Level Alchemy. Visual only, no automation.",
	tags = {"alchemy", "magic", "rhythm", "overlay", "timing"}
)
@Slf4j
public class AlchHeroPlugin extends Plugin implements MouseListener
{
	/**
	 * High Level Alchemy cast animation.
	 */
	static final int HIGH_ALCHEMY_ANIMATION = 713;

	/**
	 * High Alchemy cooldown: 5 game ticks = 3000 ms. Notes fall for exactly
	 * this long, and each cast schedules the next cycle's notes to arrive
	 * one cooldown later. Anchoring to the previous cast (wall clock, not
	 * tick counters) means the rhythm re-syncs itself every cycle.
	 */
	static final long TRAVEL_TIME_MS = 3000L;

	/**
	 * Stop the rhythm loop if no alch cast has been seen for this many ticks.
	 */
	private static final int IDLE_TIMEOUT_TICKS = 15;

	/**
	 * VarClientInt.INVENTORY_TAB values. Only the Magic tab (6) and the
	 * Inventory tab (3) are valid alching states; any other value (e.g.
	 * Settings = 11) pauses the overlay automatically.
	 */
	static final int TAB_MAGIC = 6;
	static final int TAB_INVENTORY = 3;

	/**
	 * Inventory grid dimensions: 4 columns x 7 rows, slots 0-27.
	 */
	static final int LANES = 4;
	static final int ROWS = 7;

	private static final int COINS_ID = 995;


	private static final int NATURE_RUNE_ID = 561;
	private static final int DEFAULT_NATURE_COST = 90;
	private static final String PRICES_LATEST_URL = "https://prices.runescape.wiki/api/v1/osrs/latest";
	private static final String PRICES_VOLUMES_URL = "https://prices.runescape.wiki/api/v1/osrs/volumes";
	private static final String USER_AGENT = "alch-hero RuneLite plugin (github.com/B1ngu55l4y3r/alch-hero)";

	/**
	 * The item target matrix: candidate alchables evaluated by the profit
	 * scanner against live OSRS Wiki price data.
	 */
	private static final int[] TARGET_ITEM_IDS =
	{
		1149, 4087, 1305, 1377, 1215, 1231, 11335, 1249, 1319, 1127,
		1079, 1093, 1201, 1163, 1185, 1347, 1373, 1161, 1123, 1073,
		1091, 1199, 2497, 2503, 2491, 2501, 2495, 2489, 2499, 2493,
		2487, 1099, 1065, 3385, 3387, 3389, 3391, 3393, 859, 855,
		1247, 1135, 1393, 1395, 1397, 1399, 4131, 4587, 3204, 6107,
		6108, 2572, 9245, 9244
	};

	private static final Set<Integer> TARGET_ID_SET = new HashSet<>(Arrays.asList(
		1149, 4087, 1305, 1377, 1215, 1231, 11335, 1249, 1319, 1127,
		1079, 1093, 1201, 1163, 1185, 1347, 1373, 1161, 1123, 1073,
		1091, 1199, 2497, 2503, 2491, 2501, 2495, 2489, 2499, 2493,
		2487, 1099, 1065, 3385, 3387, 3389, 3391, 3393, 859, 855,
		1247, 1135, 1393, 1395, 1397, 1399, 4131, 4587, 3204, 6107,
		6108, 2572, 9245, 9244));

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private AlchHeroOverlay overlay;

	@Inject
	private AlchHeroConfig config;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private Gson gson;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ClientToolbar clientToolbar;

	private AlchHeroPanel panel;
	private NavigationButton navButton;

	/**
	 * Active falling notes, oldest first. CopyOnWriteArrayList because it is
	 * touched from the client thread, the AWT thread, and the render loop.
	 */
	@Getter
	private final List<Note> notes = new CopyOnWriteArrayList<>();

	@Getter
	private final List<JudgmentPopup> popups = new CopyOnWriteArrayList<>();

	/**
	 * Geometry published by the overlay every frame, read by the AWT mouse
	 * thread for hit testing. strikeBand is the union of the four lane
	 * rectangles; alchBounds is the High Alchemy widget.
	 */
	@Getter
	@Setter
	private volatile Rectangle strikeBand;

	@Getter
	@Setter
	private volatile Rectangle[] laneRects;

	@Getter
	@Setter
	private volatile Rectangle alchBounds;

	@Getter
	private volatile boolean looping;

	private int tickCounter;
	private int lastCastTick;
	private int cycleCounter;
	private int itemLaneCursor;
	/**
	 * Item IDs currently flagged by the profit scanner as the best alch
	 * targets. Written by the background scan, read by the overlay.
	 */
	@Getter
	private final Set<Integer> optimalAlchTargets = ConcurrentHashMap.newKeySet();

	/**
	 * Full ranked scan results for the sidebar panel.
	 */
	@Getter
	private final List<AlchTarget> scanResults = new CopyOnWriteArrayList<>();

	@Getter
	private volatile int lastNatureCost = DEFAULT_NATURE_COST;

	@Getter
	private volatile long lastScanTime;

	private ScheduledFuture<?> scanTask;
	private Widget geChip;
	private volatile long lastCastMs;
	private Map<Integer, Integer> lastInventory;

	/**
	 * Realized GP this session: coins received per cast minus the live
	 * nature rune cost, minus the item's GE price when the scanner knows
	 * it. Rendered under the combo counter.
	 */
	@Getter
	private volatile long sessionProfit;

	@Getter
	private volatile int sessionCasts;

	@Getter
	private int combo;

	@Getter
	private int maxCombo;

	@Getter
	private int perfectCount;

	@Getter
	private int greatCount;

	@Getter
	private int goodCount;

	@Getter
	private int missCount;

	@Provides
	AlchHeroConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AlchHeroConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		mouseManager.registerMouseListener(this);

		panel = new AlchHeroPanel(this, itemManager);
		navButton = NavigationButton.builder()
			.tooltip("Alch Hero")
			.icon(ImageUtil.loadImageResource(getClass(), "panel_icon.png"))
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		scanTask = executor.scheduleWithFixedDelay(this::fetchLiveAlchMargins, 0, 60, TimeUnit.SECONDS);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		mouseManager.unregisterMouseListener(this);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
			panel = null;
		}
		if (scanTask != null)
		{
			scanTask.cancel(false);
			scanTask = null;
		}
		optimalAlchTargets.clear();
		resetLoop();
		resetStats();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN
			|| event.getGameState() == GameState.HOPPING)
		{
			resetLoop();
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (actor == null || actor != client.getLocalPlayer())
		{
			return;
		}

		if (actor.getAnimation() != HIGH_ALCHEMY_ANIMATION)
		{
			return;
		}

		long now = System.currentTimeMillis();
		lastCastTick = tickCounter;
		lastCastMs = now;

		if (!looping)
		{
			looping = true;
			notes.clear();
		}

		// Schedule the next cycle's notes, anchored to this cast.
		spawnCycle(now);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tickCounter++;

		long now = System.currentTimeMillis();
		popups.removeIf(popup -> now - popup.getCreatedAt() > AlchHeroOverlay.POPUP_LIFETIME_MS);
		maintainGeChip();

		if (!looping)
		{
			return;
		}

		if (tickCounter - lastCastTick > IDLE_TIMEOUT_TICKS)
		{
			resetLoop();
			return;
		}

		// Sweep notes the player never hit; anything past the Good window
		// is an automatic Miss.
		for (Note note : notes)
		{
			if (now > note.getArrivalTime() + config.goodWindowMs())
			{
				notes.remove(note);
				if (!note.isJudged())
				{
					registerRating(HitRating.MISS, now);
				}
			}
		}
	}

	private void spawnCycle(long castTime)
	{
		cycleCounter++;
		long spellArrival = castTime + TRAVEL_TIME_MS + config.calibrationMs();

		notes.add(new Note(Note.Type.SPELL, resolveAlchLane(), cycleCounter, spellArrival));

		itemLaneCursor = (itemLaneCursor + 1) % LANES;
		notes.add(new Note(Note.Type.ITEM, itemLaneCursor, cycleCounter,
			spellArrival + config.itemClickDelayMs()));
	}

	/**
	 * The lane the High Alchemy icon currently sits over, so the spell note
	 * falls straight onto the button. Defaults to the second lane if the
	 * geometry has not been seen yet.
	 */
	private int resolveAlchLane()
	{
		Rectangle[] lanes = laneRects;
		Rectangle alch = alchBounds;
		if (lanes != null && alch != null)
		{
			int centerX = (int) alch.getCenterX();
			for (int i = 0; i < lanes.length; i++)
			{
				if (lanes[i] != null && centerX >= lanes[i].x && centerX < lanes[i].x + lanes[i].width)
				{
					return i;
				}
			}
		}
		return 1;
	}

	// MouseListener methods below are passive observation only. Every
	// method returns the event unmodified and never calls event.consume(),
	// so all input passes straight through this transparent overlay to
	// the game engine.

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		if (event.getButton() != MouseEvent.BUTTON1 || !looping)
		{
			return event;
		}

		long now = System.currentTimeMillis();
		Point point = event.getPoint();
		int tab = getCurrentTab();

		if (tab == TAB_MAGIC)
		{
			Rectangle alch = alchBounds;
			if (alch != null && alch.contains(point))
			{
				scoreNote(Note.Type.SPELL, point, now);
			}
		}
		else if (tab == TAB_INVENTORY)
		{
			Rectangle band = strikeBand;
			if (band != null && band.contains(point))
			{
				scoreNote(Note.Type.ITEM, point, now);
			}
		}

		// Not consumed: the click still needs to reach the client so the
		// player's own input actually casts the spell.
		return event;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent event)
	{
		return event;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent event)
	{
		return event;
	}

	// Scoring helpers below.

	private void scoreNote(Note.Type type, Point point, long clickTime)
	{
		Note target = oldestUnjudged(type);
		if (target == null)
		{
			return;
		}

		long delta = clickTime - target.getArrivalTime();
		HitRating rating = rate(delta);

		if (rating == null)
		{
			// Far too early; ignore rather than punish. The note stays live
			// and will be missed by the tick sweep if never hit.
			return;
		}

		// Optional Guitar Hero strictness: the item click must land in the
		// lane the note indicates.
		if (rating != HitRating.MISS
			&& type == Note.Type.ITEM
			&& config.strictLanes())
		{
			Rectangle[] lanes = laneRects;
			if (lanes != null && target.getLane() < lanes.length)
			{
				Rectangle laneRect = lanes[target.getLane()];
				if (laneRect != null && !laneRect.contains(point))
				{
					rating = HitRating.MISS;
				}
			}
		}

		target.setJudged(true);
		notes.remove(target);
		registerRating(rating, clickTime);
	}

	private Note oldestUnjudged(Note.Type type)
	{
		for (Note note : notes)
		{
			if (note.getType() == type && !note.isJudged())
			{
				return note;
			}
		}
		return null;
	}

	/**
	 * Converts a timing error into a rating, or null for clicks so early
	 * they should be ignored entirely.
	 */
	private HitRating rate(long delta)
	{
		long error = Math.abs(delta);
		if (error <= config.perfectWindowMs())
		{
			return HitRating.PERFECT;
		}
		if (error <= config.greatWindowMs())
		{
			return HitRating.GREAT;
		}
		if (error <= config.goodWindowMs())
		{
			return HitRating.GOOD;
		}
		return delta < 0 ? null : HitRating.MISS;
	}

	private synchronized void registerRating(HitRating rating, long now)
	{
		switch (rating)
		{
			case PERFECT:
				perfectCount++;
				combo++;
				break;
			case GREAT:
				greatCount++;
				combo++;
				break;
			case GOOD:
				goodCount++;
				combo++;
				break;
			case MISS:
				missCount++;
				combo = 0;
				break;
		}

		maxCombo = Math.max(maxCombo, combo);
		popups.add(new JudgmentPopup(rating, now));
	}

	private void resetLoop()
	{
		looping = false;
		notes.clear();
	}

	private synchronized void resetStats()
	{
		combo = 0;
		maxCombo = 0;
		perfectCount = 0;
		greatCount = 0;
		goodCount = 0;
		missCount = 0;
		popups.clear();
	}

	// Alch blocker and session GP tracking below.

	/**
	 * The alch blocker: removes the Cast High Level Alchemy menu entry on
	 * any item that is neither a current scanner target nor on the
	 * built-in alchable list, so a rhythm misclick can never alch gear.
	 * Removing menu entries is the established pattern of the core Menu
	 * Entry Swapper and the Alch Blocker hub plugin.
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.alchBlocker())
		{
			return;
		}

		if (!"Cast".equals(event.getOption())
			|| event.getTarget() == null
			|| !event.getTarget().contains("High Level Alchemy"))
		{
			return;
		}

		int itemId = event.getMenuEntry().getItemId();
		if (itemId <= 0 || isAllowedAlchItem(itemId))
		{
			return;
		}

		client.getMenu().removeMenuEntry(event.getMenuEntry());
	}

	private boolean isAllowedAlchItem(int itemId)
	{
		return TARGET_ID_SET.contains(itemId) || optimalAlchTargets.contains(itemId);
	}

	/**
	 * Session GP tracking: after each cast, the inventory diff (one nature
	 * rune down, coins up) yields the realized value. Profit is coins
	 * received minus the live nature rune cost, minus the item's GE price
	 * when the scanner knows the item.
	 */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}

		Map<Integer, Integer> previous = lastInventory;
		Map<Integer, Integer> current = new HashMap<>();
		ItemContainer container = event.getItemContainer();
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() > 0)
			{
				current.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		lastInventory = current;

		if (previous == null || !looping
			|| System.currentTimeMillis() - lastCastMs > 1500)
		{
			return;
		}

		// A cast consumes exactly one nature rune.
		if (current.getOrDefault(NATURE_RUNE_ID, 0)
			!= previous.getOrDefault(NATURE_RUNE_ID, 0) - 1)
		{
			return;
		}

		long coinsGained = (long) current.getOrDefault(COINS_ID, 0)
			- previous.getOrDefault(COINS_ID, 0);
		if (coinsGained <= 0)
		{
			return;
		}

		int alchedId = -1;
		for (Map.Entry<Integer, Integer> entry : previous.entrySet())
		{
			int id = entry.getKey();
			if (id == NATURE_RUNE_ID || id == COINS_ID)
			{
				continue;
			}
			if (current.getOrDefault(id, 0) < entry.getValue())
			{
				alchedId = id;
				break;
			}
		}

		int itemCost = 0;
		for (AlchTarget target : scanResults)
		{
			if (target.getId() == alchedId)
			{
				itemCost = target.getGePrice();
				break;
			}
		}

		sessionProfit += coinsGained - lastNatureCost - itemCost;
		sessionCasts++;
	}

	// GE offer quick-fill chip below.

	/**
	 * When the Grand Exchange quantity or price prompt opens for an item
	 * the scanner has flagged, adds a clickable gold line inside the
	 * manual entry window. Clicking it sets the input text to the
	 * suggested value; the player still reviews and presses Enter
	 * themselves, so no game action is ever taken by the plugin. This is
	 * the same pattern established by existing Plugin Hub plugins.
	 */
	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.CHAT_PROMPT_INIT)
		{
			buildGeChip();
		}
	}

	/**
	 * Per-tick fallback so the chip appears even if the build script
	 * fires at an inconvenient moment or the prompt was already open.
	 */
	private void maintainGeChip()
	{
		if (promptTitle() == null)
		{
			geChip = null;
			return;
		}

		if (geChip == null || geChip.isHidden())
		{
			buildGeChip();
		}
	}

	private Widget promptTitle()
	{
		Widget title = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
		if (title != null && !title.isHidden() && title.getText() != null
			&& !title.getText().isEmpty())
		{
			return title;
		}

		title = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
		if (title != null && !title.isHidden() && title.getText() != null
			&& !title.getText().isEmpty())
		{
			return title;
		}

		return null;
	}

	private void buildGeChip()
	{
		if (geChip != null)
		{
			geChip.setHidden(true);
			geChip = null;
		}

		// Only inside the Grand Exchange offer screen.
		Widget geScreen = client.getWidget(InterfaceID.GeOffers.UNIVERSE);
		if (geScreen == null || geScreen.isHidden())
		{
			return;
		}

		Widget title = promptTitle();
		Widget layer = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
		if (title == null || layer == null)
		{
			return;
		}

		String prompt = title.getText().toLowerCase();
		boolean price = prompt.contains("price");
		boolean qty = prompt.contains("how many") || prompt.contains("quantity")
			|| prompt.contains("amount") || prompt.contains("enter");
		if (!price && !qty)
		{
			log.info("GE chip: unrecognized prompt '{}'", prompt);
			return;
		}

		// The item currently in the GE offer setup screen.
		int itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		AlchTarget target = null;
		for (AlchTarget candidate : scanResults)
		{
			if (candidate.getId() == itemId)
			{
				target = candidate;
				break;
			}
		}
		if (target == null)
		{
			log.info("GE chip: item {} is not a flagged target", itemId);
			return;
		}

		final int value = price ? target.getGePrice() : target.getMaxAffordable();
		if (value <= 0)
		{
			return;
		}

		Widget chip = layer.createChild(WidgetType.TEXT);
		chip.setText("Alch Hero: set " + (price ? "price to " : "quantity to ")
			+ QuantityFormatter.formatNumber(value));
		chip.setTextColor(0xFFD700);
		chip.setFontId(FontID.BOLD_12);
		chip.setTextShadowed(true);
		chip.setOriginalX(12);
		chip.setOriginalY(80);
		chip.setOriginalWidth(300);
		chip.setOriginalHeight(16);
		chip.setHasListener(true);
		chip.setAction(0, "Set");
		chip.setOnOpListener((JavaScriptCallback) ev ->
		{
			client.setVarcStrValue(VarClientStr.INPUT_TEXT, Integer.toString(value));
			client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
		});
		chip.revalidate();
		geChip = chip;
		log.info("GE chip created: {} = {}", price ? "price" : "quantity", value);
	}

	// Grand Exchange profit scanner below.

	/**
	 * Fetches live prices and volumes from the OSRS Wiki API on a
	 * background thread, then finishes the high alch math on the client
	 * thread (ItemComposition access requires it). Runs once on startup
	 * and every 60 seconds thereafter; never blocks the game.
	 */
	private void fetchLiveAlchMargins()
	{
		try
		{
			JsonObject latest = fetchJson(PRICES_LATEST_URL);
			JsonObject volumes = fetchJson(PRICES_VOLUMES_URL);
			if (latest == null)
			{
				return;
			}

			JsonObject priceData = latest.getAsJsonObject("data");
			JsonObject volumeData = volumes != null ? volumes.getAsJsonObject("data") : null;
			if (priceData == null)
			{
				return;
			}

			final int natureRuneCost = extractHigh(priceData, NATURE_RUNE_ID, DEFAULT_NATURE_COST);
			final long budget = config.alchBudgetM() * 1_000_000L;
			final int minProfit = config.minProfitThreshold();
			final int minVolume = config.min24hVolume();

			// Candidates that pass price and volume checks; HA values are
			// resolved on the client thread below.
			final List<long[]> candidates = new ArrayList<>();
			for (int itemId : TARGET_ITEM_IDS)
			{
				int geHigh = extractHigh(priceData, itemId, -1);
				if (geHigh <= 0 || geHigh > budget)
				{
					continue;
				}

				long volume = -1;
				if (volumeData != null)
				{
					JsonElement volumeEntry = volumeData.get(String.valueOf(itemId));
					if (volumeEntry == null || volumeEntry.isJsonNull())
					{
						continue;
					}
					volume = volumeEntry.getAsLong();
					if (volume < minVolume)
					{
						continue;
					}
				}

				candidates.add(new long[]{itemId, geHigh, volume});
			}

			clientThread.invoke(() ->
			{
				List<AlchTarget> results = new ArrayList<>();
				for (long[] candidate : candidates)
				{
					int itemId = (int) candidate[0];
					int geHigh = (int) candidate[1];
					ItemComposition composition = itemManager.getItemComposition(itemId);
					int haValue = composition.getHaPrice();
					int profit = haValue - natureRuneCost - geHigh;
					if (profit >= minProfit)
					{
						// Suggested quantity respects both the budget and
						// the 4 hour Grand Exchange buy limit.
						ItemStats stats = itemManager.getItemStats(itemId);
						int geLimit = stats != null ? stats.getGeLimit() : 0;
						int affordable = (int) (budget / geHigh);
						if (geLimit > 0)
						{
							affordable = Math.min(affordable, geLimit);
						}

						results.add(new AlchTarget(itemId, composition.getName(), geHigh,
							haValue, profit, candidate[2], geLimit, affordable));
					}
				}

				results.sort(resultComparator());

				optimalAlchTargets.clear();
				for (AlchTarget target : results)
				{
					optimalAlchTargets.add(target.getId());
				}

				scanResults.clear();
				scanResults.addAll(results);
				lastNatureCost = natureRuneCost;
				lastScanTime = System.currentTimeMillis();

				AlchHeroPanel currentPanel = panel;
				if (currentPanel != null)
				{
					SwingUtilities.invokeLater(currentPanel::rebuild);
				}

				log.debug("Alch profit scan: {} optimal targets (nature rune {} gp)",
					results.size(), natureRuneCost);
			});
		}
		catch (Exception e)
		{
			log.warn("Alch profit scan failed", e);
		}
	}

	private Comparator<AlchTarget> resultComparator()
	{
		Comparator<AlchTarget> byProfit =
			Comparator.comparingInt(AlchTarget::getProfit).reversed();
		if (config.suggestionSort() == SuggestionSort.MOST_QUANTITY)
		{
			return Comparator.comparingInt(AlchTarget::getMaxAffordable).reversed()
				.thenComparing(byProfit);
		}
		return byProfit;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!AlchHeroConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		// Re-rank the existing results immediately when the sort toggle
		// changes; no need to wait for the next scan.
		if ("suggestionSort".equals(event.getKey()))
		{
			List<AlchTarget> sorted = new ArrayList<>(scanResults);
			sorted.sort(resultComparator());
			scanResults.clear();
			scanResults.addAll(sorted);

			AlchHeroPanel currentPanel = panel;
			if (currentPanel != null)
			{
				SwingUtilities.invokeLater(currentPanel::rebuild);
			}
		}
	}

	/**
	 * Types an item's name into the Grand Exchange search box, if a buy
	 * offer search is currently open. Triggered by clicking a row in the
	 * sidebar panel. Sets the search text and runs the item search script
	 * so matching items appear; the player still picks and buys. Returns
	 * false (a harmless no-op) if the GE search is not currently open.
	 */
	boolean searchGeFor(AlchTarget target)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}

		Widget input = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
		if (input == null || input.isHidden())
		{
			return false;
		}

		final String name = target.getName();
		clientThread.invoke(() ->
		{
			client.setVarcStrValue(VarClientID.MESLAYERINPUT, name);
			client.runScript(ScriptID.GE_ITEM_SEARCH, name, 0);
		});
		return true;
	}

	/**
	 * Manual rescan from the sidebar panel; runs on the background executor.
	 */
	void requestRescan()
	{
		executor.execute(this::fetchLiveAlchMargins);
	}

	private JsonObject fetchJson(String url) throws IOException
	{
		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				log.warn("Price API request failed: {} {}", url, response.code());
				return null;
			}
			return gson.fromJson(response.body().charStream(), JsonObject.class);
		}
	}

	private static int extractHigh(JsonObject priceData, int itemId, int fallback)
	{
		JsonElement entry = priceData.get(String.valueOf(itemId));
		if (entry == null || entry.isJsonNull())
		{
			return fallback;
		}
		JsonElement high = entry.getAsJsonObject().get("high");
		if (high == null || high.isJsonNull())
		{
			return fallback;
		}
		return high.getAsInt();
	}

	// UI state helpers used by the overlay.

	int getCurrentTab()
	{
		return client.getVarcIntValue(VarClientInt.INVENTORY_TAB);
	}

	/**
	 * The overlay only renders while the player is on the Magic tab (6) or
	 * the Inventory tab (3) and the bank is closed. Any other side panel,
	 * including Settings (11), automatically pauses rendering.
	 */
	boolean isUiVisible()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}

		int tab = getCurrentTab();
		if (tab != TAB_MAGIC && tab != TAB_INVENTORY)
		{
			return false;
		}

		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank == null || bank.isHidden();
	}
}
