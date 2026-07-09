package com.alchhero;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Ordering for the profit scanner's suggestion list. Public because the
 * RuneLite config proxy must access it from outside this package.
 */
@Getter
@RequiredArgsConstructor
public enum SuggestionSort
{
	MOST_QUANTITY("Most buyable"),
	MOST_PROFIT("Most profit/item");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
