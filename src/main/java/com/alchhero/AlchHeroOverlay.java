package com.alchhero;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Arrays;
import javax.inject.Inject;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.QuantityFormatter;

/**
 * Draws the four lane fretboard, falling SPELL and ITEM notes, a bold
 * strike line through the middle of the strike row, judgment popups,
 * stats, and a setup hint before the loop starts.
 *
 * <p>The strike row is derived from the High Alchemy icon itself: whichever
 * inventory row the icon's center sits over becomes the row the lanes and
 * strike line anchor to, exactly like the original single track anchored
 * to the icon. If the icon has not been seen yet, the configurable
 * fallback row (counted from the bottom) is used.
 *
 * <p>Note movement is interpolated with System.currentTimeMillis(); a note
 * center crosses the strike line exactly at its scheduled arrival time.
 */
class AlchHeroOverlay extends Overlay
{
	static final long POPUP_LIFETIME_MS = 900L;

	private static final int NOTE_HEIGHT = 12;
	private static final int NOTE_INSET = 3;
	private static final int ARC = 8;
	private static final Stroke LINE_STROKE = new BasicStroke(1f);
	private static final Stroke STRIKE_STROKE = new BasicStroke(3f);
	private static final Color STRIKE_COLOR = new Color(255, 255, 255, 230);
	private static final Color LANE_LINE_COLOR = new Color(255, 255, 255, 50);
	private static final Color HINT_ROW_COLOR = new Color(0, 230, 118, 200);
	private static final Color PROFIT_BORDER = new Color(255, 215, 0, 230);
	private static final Color PROFIT_FILL = new Color(255, 215, 0, 55);
	private static final int PANEL_WIDTH = 150;
	private static final String[] SETUP_HINT =
	{
		"ALCH HERO SETUP",
		"1. Fill THIS row with alch item stacks",
		"2. Spell filters: Show Utility only",
		"3. Enable spellbook resizing",
		"Cast High Alchemy to start"
	};

	private final Client client;
	private final AlchHeroPlugin plugin;
	private final AlchHeroConfig config;

	/**
	 * Geometry cached across tab flips. The inventory slots are only
	 * measurable while the Inventory tab is up, and the alch widget only
	 * while the Magic tab is up; neither moves between flips, so the last
	 * known bounds keep the fretboard steady through the whole cycle.
	 */
	private int[] colX;
	private int[] colW;
	private int[] rowY;
	private int[] rowH;
	private Rectangle[] cachedLanes;
	private Rectangle cachedBand;
	private Rectangle cachedAlch;

	@Inject
	AlchHeroOverlay(Client client, AlchHeroPlugin plugin, AlchHeroConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		// Profit highlights render in the inventory AND the bank, so they
		// run before the fretboard's bank/tab visibility gate.
		drawProfitHighlights(graphics);

		if (!plugin.isUiVisible())
		{
			return null;
		}

		updateGeometry();

		Rectangle band = cachedBand;
		if (band == null)
		{
			return null;
		}

		long now = System.currentTimeMillis();
		int strikeY = (int) band.getCenterY();
		int trackTop = strikeY - config.trackLength();

		boolean idle = !plugin.isLooping() && plugin.getNotes().isEmpty();

		if (idle)
		{
			// Fretboard is gone, but keep the stats up if there is a
			// session to show, so the player still sees their combo, hit
			// counts, and session GP between bursts of alching.
			if (config.showSetupHint())
			{
				drawSetupHint(graphics, band);
			}
			if (config.showStats() && hasSession())
			{
				drawSidePanel(graphics, band, trackTop, now);
			}
			return null;
		}

		drawFretboard(graphics, band, trackTop, strikeY);
		drawNotes(graphics, band, strikeY, now);
		drawStrikeLine(graphics, band, strikeY);
		drawPopups(graphics, band, now);

		if (config.showStats())
		{
			drawSidePanel(graphics, band, trackTop, now);
		}

		return null;
	}

	/**
	 * Whether there is anything worth showing in a persistent stats panel:
	 * any judged notes this session or any realized GP.
	 */
	private boolean hasSession()
	{
		return plugin.getMaxCombo() > 0
			|| plugin.getSessionCasts() > 0
			|| plugin.getPerfectCount() > 0
			|| plugin.getGreatCount() > 0
			|| plugin.getGoodCount() > 0
			|| plugin.getMissCount() > 0;
	}

	/**
	 * Golden glow over any inventory or bank slot holding an item the
	 * profit scanner currently flags as an optimal alch target.
	 */
	private void drawProfitHighlights(Graphics2D graphics)
	{
		Set<Integer> targets = plugin.getOptimalAlchTargets();
		if (targets.isEmpty())
		{
			return;
		}

		highlightContainer(graphics, client.getWidget(InterfaceID.Inventory.ITEMS), targets);
		highlightContainer(graphics, client.getWidget(InterfaceID.Bankmain.ITEMS), targets);
	}

	private void highlightContainer(Graphics2D graphics, Widget container, Set<Integer> targets)
	{
		if (container == null || container.isHidden())
		{
			return;
		}

		Widget[] children = container.getDynamicChildren();
		if (children == null)
		{
			return;
		}

		graphics.setStroke(STRIKE_STROKE);
		for (Widget slot : children)
		{
			if (slot == null || slot.isHidden())
			{
				continue;
			}

			int itemId = slot.getItemId();
			if (itemId <= 0 || !targets.contains(itemId))
			{
				continue;
			}

			Rectangle bounds = slot.getBounds();
			if (bounds == null || bounds.width <= 0 || (bounds.x <= 0 && bounds.y <= 0))
			{
				continue;
			}

			graphics.setColor(PROFIT_FILL);
			graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, ARC, ARC);
			graphics.setColor(PROFIT_BORDER);
			graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, ARC, ARC);
		}
	}

	/**
	 * Continuously polls widget bounds each frame so the fretboard stays
	 * aligned in both Fixed and Resizable modes.
	 *
	 * <p>Empty inventory slots report garbage bounds at the screen origin,
	 * so the grid is reconstructed only from slots with real bounds: one
	 * valid slot per column fixes that column's x, one per row fixes that
	 * row's y, and any fully empty column or row is extrapolated from the
	 * uniform grid spacing.
	 */
	private void updateGeometry()
	{
		Widget inventory = client.getWidget(InterfaceID.Inventory.ITEMS);
		if (inventory != null && !inventory.isHidden())
		{
			Widget[] children = inventory.getDynamicChildren();
			if (children != null && children.length >= AlchHeroPlugin.LANES * AlchHeroPlugin.ROWS)
			{
				measureGrid(children);
			}
		}

		Widget alch = client.getWidget(InterfaceID.MagicSpellbook.HIGH_ALCHEMY);
		if (alch != null && !alch.isHidden())
		{
			cachedAlch = alch.getBounds();
		}

		if (colX != null && rowY != null)
		{
			int row = resolveStrikeRow();
			Rectangle[] lanes = new Rectangle[AlchHeroPlugin.LANES];
			Rectangle band = null;
			for (int i = 0; i < AlchHeroPlugin.LANES; i++)
			{
				if (colX[i] >= 0 && rowY[row] >= 0)
				{
					lanes[i] = new Rectangle(colX[i], rowY[row], colW[i], rowH[row]);
					band = band == null ? new Rectangle(lanes[i]) : band.union(lanes[i]);
				}
			}
			cachedLanes = lanes;
			cachedBand = band;
		}

		plugin.setLaneRects(cachedLanes);
		plugin.setStrikeBand(cachedBand);
		plugin.setAlchBounds(cachedAlch);
	}

	private void measureGrid(Widget[] children)
	{
		int[] cx = new int[AlchHeroPlugin.LANES];
		int[] cw = new int[AlchHeroPlugin.LANES];
		int[] ry = new int[AlchHeroPlugin.ROWS];
		int[] rh = new int[AlchHeroPlugin.ROWS];
		Arrays.fill(cx, -1);
		Arrays.fill(ry, -1);

		for (int i = 0; i < AlchHeroPlugin.LANES * AlchHeroPlugin.ROWS; i++)
		{
			Rectangle b = children[i].getBounds();
			if (b == null || b.width <= 0 || b.height <= 0 || (b.x <= 0 && b.y <= 0))
			{
				// Empty slot; bounds are garbage anchored at the origin.
				continue;
			}

			int col = i % AlchHeroPlugin.LANES;
			int row = i / AlchHeroPlugin.LANES;
			if (cx[col] < 0)
			{
				cx[col] = b.x;
				cw[col] = b.width;
			}
			if (ry[row] < 0)
			{
				ry[row] = b.y;
				rh[row] = b.height;
			}
		}

		extrapolate(cx, cw);
		extrapolate(ry, rh);

		// Require at least one measurable slot before trusting the grid.
		if (cx[0] >= 0 || cx[1] >= 0 || cx[2] >= 0 || cx[3] >= 0)
		{
			colX = cx;
			colW = cw;
			rowY = ry;
			rowH = rh;
		}
	}

	/**
	 * Fills unknown grid positions (-1) from the uniform spacing implied by
	 * any two known positions, or from a single known slot's size if only
	 * one exists.
	 */
	private static void extrapolate(int[] pos, int[] size)
	{
		int first = -1;
		int second = -1;
		for (int i = 0; i < pos.length; i++)
		{
			if (pos[i] >= 0)
			{
				if (first < 0)
				{
					first = i;
				}
				else
				{
					second = i;
					break;
				}
			}
		}

		if (first < 0)
		{
			return;
		}

		int spacing = second >= 0
			? (pos[second] - pos[first]) / (second - first)
			: size[first] + 6;

		for (int i = 0; i < pos.length; i++)
		{
			if (pos[i] < 0)
			{
				pos[i] = pos[first] + (i - first) * spacing;
				size[i] = size[first];
			}
		}
	}

	/**
	 * The strike row is whichever inventory row the High Alchemy icon's
	 * center currently sits over, so the strike line lands ON the icon by
	 * construction. Falls back to the configured row from the bottom.
	 */
	private int resolveStrikeRow()
	{
		Rectangle alch = cachedAlch;
		if (alch != null && rowY != null)
		{
			int centerY = (int) alch.getCenterY();
			for (int row = 0; row < AlchHeroPlugin.ROWS; row++)
			{
				if (rowY[row] >= 0 && centerY >= rowY[row] && centerY < rowY[row] + rowH[row])
				{
					return row;
				}
			}
		}

		int fromBottom = Math.max(1, Math.min(AlchHeroPlugin.ROWS, config.strikeRow()));
		return AlchHeroPlugin.ROWS - fromBottom;
	}

	private void drawFretboard(Graphics2D graphics, Rectangle band, int trackTop, int strikeY)
	{
		// The fretboard ends at the strike row's bottom edge, exactly like
		// the original track ended at the icon's bottom edge.
		int bottom = band.y + band.height;

		graphics.setColor(config.trackColor());
		graphics.fillRoundRect(band.x, trackTop, band.width, bottom - trackTop, ARC, ARC);

		Rectangle[] lanes = cachedLanes;
		graphics.setStroke(LINE_STROKE);
		graphics.setColor(LANE_LINE_COLOR);
		graphics.drawLine(band.x, trackTop, band.x, bottom);
		graphics.drawLine(band.x + band.width, trackTop, band.x + band.width, bottom);
		if (lanes != null)
		{
			for (int i = 1; i < lanes.length; i++)
			{
				if (lanes[i] != null)
				{
					graphics.drawLine(lanes[i].x, trackTop, lanes[i].x, bottom);
				}
			}
		}

	}

	private void drawNotes(Graphics2D graphics, Rectangle band, int strikeY, long now)
	{
		Rectangle[] lanes = cachedLanes;
		int trackLength = config.trackLength();

		for (Note note : plugin.getNotes())
		{
			long remaining = note.getArrivalTime() - now;

			// remaining == 0 places the note center exactly on the strike
			// line; slightly negative keeps it sliding just past the line
			// during the miss grace window so late hits look honest.
			int y = strikeY - Math.round((remaining / (float) AlchHeroPlugin.TRAVEL_TIME_MS) * trackLength);
			if (y < strikeY - trackLength)
			{
				continue;
			}

			Rectangle lane = lanes != null && note.getLane() < lanes.length
				? lanes[note.getLane()]
				: null;
			int x = lane != null ? lane.x : band.x;
			int width = lane != null ? lane.width : band.width / AlchHeroPlugin.LANES;

			graphics.setColor(note.getType() == Note.Type.SPELL
				? config.spellNoteColor()
				: config.noteColor());
			graphics.fillRoundRect(
				x + NOTE_INSET,
				y - NOTE_HEIGHT / 2,
				width - NOTE_INSET * 2,
				NOTE_HEIGHT,
				ARC,
				ARC);
		}
	}

	private void drawStrikeLine(Graphics2D graphics, Rectangle band, int strikeY)
	{
		int right = band.x + band.width;

		graphics.setStroke(STRIKE_STROKE);
		graphics.setColor(STRIKE_COLOR);
		graphics.drawLine(band.x - 4, strikeY, right + 4, strikeY);

		// Inward pointing arrows so the click line is unmissable.
		Polygon left = new Polygon(
			new int[]{band.x - 12, band.x - 12, band.x - 4},
			new int[]{strikeY - 6, strikeY + 6, strikeY},
			3);
		Polygon rightArrow = new Polygon(
			new int[]{right + 12, right + 12, right + 4},
			new int[]{strikeY - 6, strikeY + 6, strikeY},
			3);
		graphics.fillPolygon(left);
		graphics.fillPolygon(rightArrow);
	}

	private void drawSetupHint(Graphics2D graphics, Rectangle band)
	{
		graphics.setStroke(STRIKE_STROKE);
		graphics.setColor(HINT_ROW_COLOR);
		graphics.drawRoundRect(band.x - 2, band.y - 2, band.width + 4, band.height + 4, ARC, ARC);

		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int lineHeight = metrics.getHeight();
		int y = band.y - 8 - lineHeight * (SETUP_HINT.length - 1);

		for (String line : SETUP_HINT)
		{
			graphics.setColor(Color.BLACK);
			graphics.drawString(line, band.x + 1, y + 1);
			graphics.setColor(Color.WHITE);
			graphics.drawString(line, band.x, y);
			y += lineHeight;
		}
	}

	private void drawPopups(Graphics2D graphics, Rectangle band, long now)
	{
		graphics.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics metrics = graphics.getFontMetrics();

		for (JudgmentPopup popup : plugin.getPopups())
		{
			long age = now - popup.getCreatedAt();
			if (age > POPUP_LIFETIME_MS)
			{
				continue;
			}

			float life = age / (float) POPUP_LIFETIME_MS;
			int alpha = Math.max(0, Math.min(255, Math.round(255 * (1f - life))));
			int rise = Math.round(life * 24);

			String text = popup.getRating().getText();
			Color base = popup.getRating().getColor();
			int textX = band.x + (band.width - metrics.stringWidth(text)) / 2;
			int textY = (int) band.getCenterY() - 20 - rise;

			graphics.setColor(new Color(0, 0, 0, alpha));
			graphics.drawString(text, textX + 1, textY + 1);
			graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
			graphics.drawString(text, textX, textY);
		}
	}

	/**
	 * The primary rhythm UI: a big combo counter, hit stats, and the star
	 * power meter, rendered beside the fretboard instead of on top of it.
	 */
	private void drawSidePanel(Graphics2D graphics, Rectangle band, int trackTop, long now)
	{
		int x = band.x - PANEL_WIDTH - 14;
		if (x < 8)
		{
			x = band.x + band.width + 14;
		}
		int y = trackTop + 20;

		Font comboFont = FontManager.getRunescapeBoldFont().deriveFont(28f);
		graphics.setFont(comboFont);
		String comboText = "x" + plugin.getCombo();
		graphics.setColor(Color.BLACK);
		graphics.drawString(comboText, x + 2, y + 2);
		graphics.setColor(Color.WHITE);
		graphics.drawString(comboText, x, y);

		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int lineHeight = metrics.getHeight();
		y += lineHeight + 8;

		long gp = plugin.getSessionProfit();
		String gpText = gp < 0
			? "-" + QuantityFormatter.quantityToStackSize(-gp)
			: "+" + QuantityFormatter.quantityToStackSize(gp);

		String[] lines =
		{
			"MAX x" + plugin.getMaxCombo(),
			"P:" + plugin.getPerfectCount()
				+ " G:" + plugin.getGreatCount()
				+ " OK:" + plugin.getGoodCount()
				+ " X:" + plugin.getMissCount(),
			"Session " + gpText + " gp (" + plugin.getSessionCasts() + " casts)"
		};

		for (String line : lines)
		{
			graphics.setColor(Color.BLACK);
			graphics.drawString(line, x + 1, y + 1);
			graphics.setColor(Color.WHITE);
			graphics.drawString(line, x, y);
			y += lineHeight;
		}
	}
}
