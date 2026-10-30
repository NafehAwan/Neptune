package net.tuffclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.tuffclient.TuffClient;

/** Rounded boxes for HUD element backgrounds and borders, sharp at every GUI scale. */
public final class Ui {
	private static final int MAX_RADIUS = 40;
	private static final Identifier[] ROUND = new Identifier[MAX_RADIUS + 1];
	private static final Identifier[] RING = new Identifier[MAX_RADIUS + 1];

	static {
		for (int i = 1; i <= MAX_RADIUS; i++) {
			ROUND[i] = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "round_" + i);
			RING[i] = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "ring_" + i);
		}
	}

	private Ui() {
	}

	/** Filled rounded rectangle. Picks the sprite whose corner is closest to 1:1 on screen. */
	public static void round(Gfx g, float x, float y, float w, float h, float r, int argb) {
		roundSprite(g, ROUND, x, y, w, h, r, argb);
	}

	/** Rounded outline only, about one screen pixel wide. */
	public static void outline(Gfx g, float x, float y, float w, float h, float r, int argb) {
		roundSprite(g, RING, x, y, w, h, r, argb);
	}

	private static void roundSprite(Gfx g, Identifier[] set, float x, float y, float w, float h, float r, int argb) {
		if ((argb >>> 24) == 0 || w <= 0 || h <= 0) return;
		r = Math.max(0.5f, Math.min(r, Math.min(w, h) / 2));
		// draw in screen pixels with the shape made for this radius, so every
		// texel is exactly one pixel (scaled sprites look broken up)
		float scale = scale();
		int radius = Math.max(1, Math.min(MAX_RADIUS, Math.round(r * scale)));
		// past the largest shape (4K screens) the largest one is scaled up instead
		float s = Math.max(1f / scale, r / MAX_RADIUS);
		g.push();
		g.translate(snap(x), snap(y));
		g.scale(s, s);
		g.sprite(set[radius], 0, 0, Math.max(2 * radius, Math.round(w / s)), Math.max(2 * radius, Math.round(h / s)), argb);
		g.pop();
	}

	/** Moves a coordinate onto the nearest whole screen pixel. */
	public static float snap(float v) {
		float p = scale();
		return p <= 0 ? v : Math.round(v * p) / p;
	}

	/** Physical pixels per GUI unit. */
	public static float scale() {
		return (float) Minecraft.getInstance().getWindow().getGuiScale();
	}
}
