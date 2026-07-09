package com.alchhero;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(AlchHeroConfig.GROUP)
public interface AlchHeroConfig extends Config
{
	String GROUP = "alchhero";

	@Range(min = 60, max = 400)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "trackLength",
		name = "Track length",
		description = "Vertical length of the fretboard drawn above the strike row",
		position = 0
	)
	default int trackLength()
	{
		return 140;
	}

	@Range(min = 1, max = 7)
	@ConfigItem(
		keyName = "strikeRow",
		name = "Fallback strike row",
		description = "Inventory row (counted from the bottom) used when the High Alchemy icon cannot be located; auto detection from the icon takes priority",
		position = 1
	)
	default int strikeRow()
	{
		return 3;
	}

	@Range(min = 20, max = 300)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "perfectWindowMs",
		name = "Perfect window",
		description = "Maximum timing error (plus or minus) for a Perfect rating",
		position = 2
	)
	default int perfectWindowMs()
	{
		return 100;
	}

	@Range(min = 50, max = 500)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "greatWindowMs",
		name = "Great window",
		description = "Maximum timing error (plus or minus) for a Great rating",
		position = 3
	)
	default int greatWindowMs()
	{
		return 250;
	}

	@Range(min = 100, max = 800)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "goodWindowMs",
		name = "Good window",
		description = "Maximum timing error (plus or minus) for a Good rating; beyond this a note is a Miss",
		position = 4
	)
	default int goodWindowMs()
	{
		return 450;
	}

	@Range(min = -600, max = 600)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "calibrationMs",
		name = "Calibration offset",
		description = "Shifts every note earlier (negative) or later (positive) if the strike timing feels consistently off from your real cast rhythm",
		position = 5
	)
	default int calibrationMs()
	{
		return 0;
	}

	@Range(min = 100, max = 1500)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "itemClickDelayMs",
		name = "Item note delay",
		description = "How long after the spell note the item note arrives; match this to your natural spell-then-item click rhythm",
		position = 6
	)
	default int itemClickDelayMs()
	{
		return 600;
	}

	@ConfigItem(
		keyName = "strictLanes",
		name = "Strict lanes",
		description = "Item clicks must land in the lane the note indicates or they count as a Miss",
		position = 7
	)
	default boolean strictLanes()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "spellNoteColor",
		name = "Spell note color",
		description = "Fill color of falling High Alchemy spell notes",
		position = 8
	)
	default Color spellNoteColor()
	{
		return new Color(255, 152, 0, 230);
	}

	@Alpha
	@ConfigItem(
		keyName = "noteColor",
		name = "Item note color",
		description = "Fill color of falling item notes",
		position = 9
	)
	default Color noteColor()
	{
		return new Color(0, 200, 255, 230);
	}

	@Alpha
	@ConfigItem(
		keyName = "trackColor",
		name = "Fretboard color",
		description = "Background color of the fretboard",
		position = 10
	)
	default Color trackColor()
	{
		return new Color(0, 0, 0, 90);
	}

	@Range(min = 1, max = 2000)
	@ConfigItem(
		keyName = "alchBudgetM",
		name = "Alch budget (millions)",
		description = "GP budget in millions used by the profit scanner when judging affordability",
		position = 13
	)
	default int alchBudgetM()
	{
		return 30;
	}

	@Range(min = 0, max = 5000)
	@ConfigItem(
		keyName = "minProfitThreshold",
		name = "Min profit per alch",
		description = "Minimum GP profit per cast for an item to be flagged by the profit scanner",
		position = 14
	)
	default int minProfitThreshold()
	{
		return 10;
	}

	@Range(min = 0, max = 100000)
	@ConfigItem(
		keyName = "min24hVolume",
		name = "Min 24h volume",
		description = "Minimum daily traded volume for an item to be flagged by the profit scanner",
		position = 15
	)
	default int min24hVolume()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "suggestionSort",
		name = "Sort suggestions by",
		description = "Most buyable puts the biggest stack you can buy first; Most profit/item ranks by GP per cast",
		position = 16
	)
	default SuggestionSort suggestionSort()
	{
		return SuggestionSort.MOST_QUANTITY;
	}

	@ConfigItem(
		keyName = "alchBlocker",
		name = "Alch blocker",
		description = "Removes the Cast High Level Alchemy menu entry on any item that is not a scanner target or on the built-in alchable list, so a rhythm misclick cannot alch your gear",
		position = 21
	)
	default boolean alchBlocker()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showSetupHint",
		name = "Show setup hint",
		description = "Draw setup instructions on the strike row until the rhythm loop starts",
		position = 17
	)
	default boolean showSetupHint()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showStats",
		name = "Show combo and stats",
		description = "Draw the combo counter, hit statistics, and star power meter beside the fretboard",
		position = 18
	)
	default boolean showStats()
	{
		return true;
	}
}
