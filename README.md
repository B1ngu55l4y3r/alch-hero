# Alch Hero

A Guitar Hero style timing overlay for High Level Alchemy. A four lane fretboard is drawn over the third inventory row from the bottom, spell and item notes fall in alternation on the 5 tick (3000 ms) alch cooldown, and your own manual casts are scored Perfect / Great / Good / Miss with a combo counter.

## Setup

1. Fill the third inventory row from the bottom (slots 17 to 20 counting from 1) with stacks of alchable items
2. Set spell filters to show Utility spells only (right click the spellbook tab)
3. Enable spellbook resizing so the High Alchemy icon sits over that row
4. Cast High Alchemy once to start the loop

Orange notes are the High Alchemy spell click; blue notes are the item click, rotating across the four lanes. Hit them as they cross the white strike line.

The goal is to make long alching sessions less mind-numbing and help you internalize the cooldown rhythm. Nothing is automated. You still click every cast yourself.

## Compliance and safety

This plugin is strictly a visual overlay and passive observer. Specifically:

- **No synthetic input.** There is no `java.awt.Robot`, no cursor movement, no click generation, no menu automation, and no input queuing of any kind.
- **No consumed events.** A `net.runelite.client.input.MouseListener` observes the timestamp of the player's own left click. Every event is returned unmodified and `event.consume()` is never called, so all input passes through to the client exactly as the player performed it (1:1 input preserved).
- **No hidden game state.** The plugin reads only the local player's animation, the current side panel tab, and public widget bounds. It reveals nothing the player cannot already see.

## Other configuration

- Track length, note color, and track color
- Perfect / Great / Good timing windows
- Calibration offset if the strike timing feels consistently early or late on your setup
- Toggleable combo and stats display

## Profit scanner

Every 60 seconds the plugin fetches live prices and volumes from the OSRS Wiki API (prices.runescape.wiki) on a background thread, computes high alch margins for a curated set of alchable items against the live nature rune price, filters by your configured budget, minimum profit, and minimum 24h volume, draws a golden glow over any matching item in your inventory or bank, and lists every flagged target ranked by profit in a sidebar panel (coin icon in the RuneLite sidebar) with buy price, affordable quantity, and a manual rescan button. Suggested quantities respect both your budget and the item's 4 hour GE buy limit. When the GE quantity or price prompt is open for a flagged item, a clickable gold "Alch Hero: set..." line appears inside the manual entry window; clicking it fills the input with the suggestion, and you still review and press Enter yourself. The plugin never submits an offer or takes any game action. The scanner only reads public price data; nothing about your account is sent anywhere.

## GE search button, alch blocker, session GP

At the Grand Exchange, clicking any item row in the Alch Hero sidebar panel types that item into the open buy-offer search box, so you can pull up exactly what to alch without typing. It only acts when a GE search is open; otherwise the click is a harmless no-op. The plugin fills the search text only; you still pick the result and buy. The optional Alch Blocker removes the Cast High Level Alchemy menu entry on any item that is not a scanner target or on the built-in alchable list, so a rhythm misclick cannot alch your gear. A session GP tracker under the combo counter shows realized profit per cast: coins received minus the live nature rune cost, minus the item's GE price when known. Stats and session GP stay on screen between bursts of alching, not only while notes are falling.

## Behavior notes

- The overlay only activates once it sees you cast High Alchemy, and clears itself after 15 ticks without a cast.
- Rendering pauses automatically in the bank and on any side panel other than Magic or Inventory.
- Widget bounds are polled every frame, so the track aligns in both Fixed and Resizable modes.
