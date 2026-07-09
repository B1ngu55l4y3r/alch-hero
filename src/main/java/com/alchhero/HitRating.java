package com.alchhero;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
enum HitRating
{
	PERFECT("Perfect!", new Color(0, 230, 118)),
	GREAT("Great", new Color(64, 196, 255)),
	GOOD("Good", new Color(255, 202, 40)),
	MISS("Miss", new Color(244, 67, 54));

	private final String text;
	private final Color color;
}
