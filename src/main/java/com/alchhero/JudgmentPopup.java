package com.alchhero;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A transient floating text popup ("Perfect!", "Miss", etc.) rendered near
 * the strike zone after a click has been scored.
 */
@Getter
@RequiredArgsConstructor
class JudgmentPopup
{
	private final HitRating rating;
	private final long createdAt;
}
