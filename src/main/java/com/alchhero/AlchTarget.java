package com.alchhero;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * One profitable alch candidate produced by the profit scanner, ready for
 * display in the sidebar panel.
 */
@Getter
@RequiredArgsConstructor
class AlchTarget
{
	private final int id;
	private final String name;
	private final int gePrice;
	private final int haValue;
	private final int profit;
	private final long volume;
	private final int geLimit;
	private final int maxAffordable;
}
