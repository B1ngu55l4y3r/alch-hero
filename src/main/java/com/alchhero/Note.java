package com.alchhero;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * A single falling note. SPELL notes represent the High Alchemy spell click
 * that starts a cast cycle; ITEM notes represent the follow-up click on an
 * alchable stack in the strike row.
 *
 * <p>Notes are scheduled by absolute arrival time (wall clock), anchored to
 * the previous cast. This self-corrects any drift between game ticks and
 * the player's real rhythm.
 */
@Getter
@RequiredArgsConstructor
class Note
{
	enum Type
	{
		SPELL,
		ITEM
	}

	private final Type type;
	private final int lane;
	private final int cycle;
	private final long arrivalTime;

	@Setter
	private boolean judged;
}
