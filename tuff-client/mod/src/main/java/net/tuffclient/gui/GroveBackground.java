package net.tuffclient.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.tuffclient.TuffClient;

/**
 * The Glade title scene, the same one the launcher shows: a parallax pixel
 * forest under a slow-moving sun, with drifting clouds, swaying trees, birds
 * and falling leaves. Drawn at the window's real pixel resolution as a pure
 * function of time, so it can be paused on any frame.
 */
public final class GroveBackground {
	private static final Identifier GLOW = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "textures/gui/title/glow.png");
	private static final Identifier RAY = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "textures/gui/title/ray.png");

	/** cx, cy, scale, speed, alpha */
	private static final float[][] CLOUDS = {
			{0.08f, 0.13f, 1.5f, 9, 0.95f}, {0.46f, 0.08f, 1.9f, 14, 0.95f}, {0.8f, 0.18f, 1.2f, 11, 0.9f},
			{0.25f, 0.26f, 0.8f, 5, 0.6f}, {0.66f, 0.3f, 0.7f, 4, 0.55f}, {0.95f, 0.24f, 0.9f, 6, 0.6f},
	};
	/** base, parallax, amplitude */
	private static final float[][] HILLS = {{0.7f, 0.05f, 3}, {0.76f, 0.1f, 5}};
	private static final int[] HILL_COLORS = {0xFFC6D6C2, 0xFFAFC7AE};
	/** leaf, dark leaf, trunk */
	private static final int[][] TREE_COLORS = {
			{0xFF9FBE9F, 0xFF8BAE8C, 0xFF7C6A52}, {0xFF6E9C7A, 0xFF5E8C6B, 0xFF6B5640}, {0xFF447A5C, 0xFF386B4F, 0xFF594531},
	};
	/** base, seed, parallax, size, sway */
	private static final float[][] TREE_LAYERS = {{0.8f, 41, 0.25f, 3, 0.6f}, {0.87f, 47, 0.5f, 4, 1.0f}, {0.95f, 53, 1.0f, 5, 1.6f}};

	private float time = 30;
	private long last = System.nanoTime();

	/** w/h in GUI units. */
	public void render(GuiGraphicsExtractor g, int w, int h, boolean animate) {
		step(animate);
		drawAt(g, w, h, null);
	}

	/**
	 * Frosted glass over the scene, like CSS backdrop-filter: the scene is drawn,
	 * blurred with the game's own blur, then drawn sharp again everywhere except
	 * behind the glass. Glass shapes are rounded rectangles {x, y, w, h, radius}
	 * in GUI units; the blurred area follows their rounded corners row by row.
	 */
	public void renderWithGlass(GuiGraphicsExtractor g, int w, int h, boolean animate, Runnable blur, List<float[]> glass) {
		step(animate);
		drawAt(g, w, h, null);
		if (glass.isEmpty()) return;
		// own layers either side of the blur, so only the first pass is blurred
		g.nextStratum();
		blur.run();
		g.nextStratum();
		for (int[] r : regionsFor(w, h, glass)) {
			g.enableScissor(r[0], r[1], r[2], r[3]);
			drawAt(g, w, h, r);
			g.disableScissor();
		}
	}

	private float[] cachedKey;
	private List<int[]> cachedRegions;

	/** The regions only change when the glass moves or the window resizes, so they are kept between frames. */
	private List<int[]> regionsFor(int w, int h, List<float[]> glass) {
		float[] key = new float[2 + glass.size() * 5];
		key[0] = w;
		key[1] = h;
		for (int i = 0; i < glass.size(); i++) System.arraycopy(glass.get(i), 0, key, 2 + i * 5, 5);
		if (cachedRegions == null || !java.util.Arrays.equals(key, cachedKey)) {
			cachedRegions = sharpRegions(w, h, glass);
			cachedKey = key;
		}
		return cachedRegions;
	}

	private void step(boolean animate) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		if (animate) time += dt;
	}

	private void drawAt(GuiGraphicsExtractor g, int w, int h, int[] region) {
		int gs = Ui.guiScale();
		if (region == null) {
			clipOn = false;
		} else {
			clipOn = true;
			clipX1 = region[0] * gs;
			clipY1 = region[1] * gs;
			clipX2 = region[2] * gs;
			clipY2 = region[3] * gs;
		}
		g.pose().pushMatrix();
		g.pose().scale(1f / gs, 1f / gs);
		draw(g, w * gs, h * gs, time);
		g.pose().popMatrix();
		clipOn = false;
	}

	/**
	 * The screen minus the glass, as whole-GUI-unit rectangles that never overlap
	 * (overlaps would draw the translucent rays twice). Rows inside a corner are
	 * trimmed to stay inside the curve, so blur never shows outside the glass.
	 */
	static List<int[]> sharpRegions(int w, int h, List<float[]> glass) {
		List<int[]> covered = new ArrayList<>();
		for (float[] s : glass) {
			float x = s[0], y = s[1], sw = s[2], sh = s[3], r = Math.min(s[4], Math.min(sw, sh) / 2);
			int top = (int) Math.ceil(y), bottom = (int) Math.floor(y + sh);
			for (int row = top; row < bottom; row++) {
				// distance into the curve from the nearer edge, using the row edge nearest that edge
				float dy = Math.min(row - y, y + sh - (row + 1));
				float inset = dy >= r ? 0 : r - (float) Math.sqrt(Math.max(0, r * r - (r - dy) * (r - dy)));
				int x1 = (int) Math.ceil(x + inset), x2 = (int) Math.floor(x + sw - inset);
				if (x2 > x1) covered.add(new int[] {x1, row, x2, row + 1});
			}
		}
		// sweep rows: every row is cut into the gaps between covered spans
		TreeSet<Integer> ys = new TreeSet<>();
		ys.add(0);
		ys.add(h);
		for (int[] c : covered) {
			ys.add(Math.max(0, Math.min(h, c[1])));
			ys.add(Math.max(0, Math.min(h, c[3])));
		}
		List<int[]> out = new ArrayList<>();
		Map<Long, int[]> open = new HashMap<>();
		Integer prev = null;
		for (int y : ys) {
			if (prev != null && y > prev) {
				int ya = prev, yb = y;
				List<int[]> spans = new ArrayList<>();
				for (int[] c : covered) if (c[1] <= ya && c[3] >= yb) spans.add(new int[] {c[0], c[2]});
				spans.sort(Comparator.comparingInt(sp -> sp[0]));
				List<int[]> gaps = new ArrayList<>();
				int cx = 0;
				for (int[] sp : spans) {
					if (sp[0] > cx) gaps.add(new int[] {cx, Math.min(w, sp[0])});
					cx = Math.max(cx, sp[1]);
				}
				if (cx < w) gaps.add(new int[] {cx, w});
				// grow regions down while a gap repeats, so a plain band stays one region
				Map<Long, int[]> next = new HashMap<>();
				for (int[] gap : gaps) {
					if (gap[1] <= gap[0]) continue;
					long key = ((long) gap[0] << 32) | (gap[1] & 0xFFFFFFFFL);
					int[] region = open.remove(key);
					if (region != null && region[3] == ya) region[3] = yb;
					else {
						region = new int[] {gap[0], ya, gap[1], yb};
						out.add(region);
					}
					next.put(key, region);
				}
				open = next;
			}
			prev = y;
		}
		return out;
	}

	private static boolean clipOn;
	private static float clipX1, clipY1, clipX2, clipY2;

	/** Skips shapes outside the region being redrawn, so the sharp pass costs little. */
	private static boolean outside(float x1, float y1, float x2, float y2) {
		return clipOn && (x2 <= clipX1 || x1 >= clipX2 || y2 <= clipY1 || y1 >= clipY2);
	}

	/** A tiny LCG, the same one the launcher uses, so both scenes plant the same trees. */
	private static final class Rng {
		private long s;

		Rng(long seed) {
			s = seed & 0xFFFFFFFFL;
		}

		double next() {
			s = (s * 1664525L + 1013904223L) & 0xFFFFFFFFL;
			return s / 4294967296.0;
		}
	}

	private static int rgb(float r, float g, float b) {
		return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int clamp(float v) {
		return Math.max(0, Math.min(255, Math.round(v)));
	}

	private static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, int argb) {
		if (outside(x, y, x + w, y + h)) return;
		int x1 = Math.round(x), y1 = Math.round(y);
		int x2 = Math.round(x + w), y2 = Math.round(y + h);
		if (x2 > x1 && y2 > y1) g.fill(x1, y1, x2, y2, argb);
	}

	private static void draw(GuiGraphicsExtractor g, int w, int h, float t) {
		final float px = Math.max(3, Math.round(w / 320f));
		java.util.function.DoubleUnaryOperator snapOp = v -> Math.round(v / px) * px;

		// sun travels a slow arc (a full sweep takes about four minutes)
		float sa = 0.18f + (t * 0.004f) % 0.64f;
		float sx = w * (0.15f + 0.7f * sa), sy = h * (0.42f - 0.3f * (float) Math.sin(sa * Math.PI));
		float warm = 1 - (float) Math.sin(sa * Math.PI);

		int top = rgb(178 + 18 * warm, 214 - 4 * warm, 232 - 22 * warm);
		int mid = rgb(226 + 14 * warm, 236 - 6 * warm, 226 - 20 * warm);
		int split = Math.round(h * 0.55f);
		g.fillGradient(0, 0, w, split, top, mid);
		g.fillGradient(0, split, w, h, mid, 0xFFF6F1E4);

		// soft rays turning slowly around the sun
		for (int i = 0; i < 9; i++) {
			float angle = t * 0.05f + (float) (Math.PI * 2 / 9) * (i + 1);
			float a = 0.06f + 0.03f * (float) Math.sin(t * 0.8f + i);
			g.pose().pushMatrix();
			g.pose().translate(sx, sy);
			g.pose().rotate(angle);
			Ui.image(g, RAY, 0, -h * 0.05f, w * 0.5f, h * 0.1f, 256, 64, Theme.alpha(0xFFFFF0C8, a * 1.6f));
			g.pose().popMatrix();
		}
		float halo = h * 0.42f;
		Ui.image(g, GLOW, sx - halo, sy - halo, halo * 2, halo * 2, 256, 256, Theme.alpha(0xFFFFE8AF, 0.55f + 0.05f * (float) Math.sin(t * 1.3f)));

		// pixel sun: a square disc with a lighter core, gently pulsing
		float sr = (float) snapOp.applyAsDouble(h * (0.055f + 0.003f * (float) Math.sin(t * 1.3f)));
		rect(g, (float) snapOp.applyAsDouble(sx - sr), (float) snapOp.applyAsDouble(sy - sr), sr * 2, sr * 2, 0xFFFFE7A8);
		rect(g, (float) snapOp.applyAsDouble(sx - sr * 0.6f), (float) snapOp.applyAsDouble(sy - sr * 0.6f),
				(float) snapOp.applyAsDouble(sr * 1.2f), (float) snapOp.applyAsDouble(sr * 1.2f), 0xFFFFF4CF);

		// blocky clouds at three depths, each drifting at its own speed
		for (float[] c : CLOUDS) {
			float cw = (float) snapOp.applyAsDouble(px * 26 * c[2]), ch = (float) snapOp.applyAsDouble(px * 4 * c[2]);
			float span = w + cw * 2;
			float x0 = (float) snapOp.applyAsDouble((((c[0] * w + t * c[3]) % span) + span) % span - cw);
			float y0 = (float) snapOp.applyAsDouble(c[1] * h + Math.sin(t * 0.3f + c[0] * 9) * px);
			rect(g, x0 + px, y0 + ch, cw - px * 2, px, Theme.alpha(0xFFD6E2E8, c[4] * 0.6f));
			int white = Theme.alpha(0xFFFFFFFF, c[4]);
			rect(g, x0, y0, cw, ch, white);
			rect(g, x0 + (float) snapOp.applyAsDouble(cw * 0.18f), y0 - (float) snapOp.applyAsDouble(ch * 0.75f),
					(float) snapOp.applyAsDouble(cw * 0.46f), (float) snapOp.applyAsDouble(ch * 0.75f), white);
			rect(g, x0 + (float) snapOp.applyAsDouble(cw * 0.3f), y0 - (float) snapOp.applyAsDouble(ch * 1.35f),
					(float) snapOp.applyAsDouble(cw * 0.2f), (float) snapOp.applyAsDouble(ch * 0.6f), white);
		}

		// birds crossing the sky, wings flapping
		for (int i = 0; i < 3; i++) {
			float span = w + 200, speed = 34 + i * 9;
			float bx = (((t * speed + i * 380) % span) + span) % span - 100;
			float by = h * (0.2f + i * 0.05f) + (float) Math.sin(t * 1.5f + i) * 8;
			boolean up = Math.sin(t * 9 + i * 2) > 0;
			int bird = 0xBF2F483A;
			float b = Math.max(2, Math.round(px * 0.6f));
			float x = (float) snapOp.applyAsDouble(bx), y = (float) snapOp.applyAsDouble(by);
			rect(g, x, y, b, b, bird);
			rect(g, x - b, y + (up ? -b : b), b, b, bird);
			rect(g, x + b, y + (up ? -b : b), b, b, bird);
			rect(g, x - 2 * b, y + (up ? -2 * b : b), b, b, bird);
			rect(g, x + 2 * b, y + (up ? -2 * b : b), b, b, bird);
		}

		// distant rolling hills
		for (int hi = 0; hi < HILLS.length; hi++) {
			float[] hill = HILLS[hi];
			int col = HILL_COLORS[hi];
			float hx1 = clipOn ? Math.max(0, (float) Math.floor(clipX1 / px) * px) : 0, hx2 = clipOn ? Math.min(w, clipX2) : w;
			for (float x = hx1; x < hx2; x += px) {
				float u = (x + t * 6 * hill[1] * 10) / w;
				float y = (float) snapOp.applyAsDouble(h * hill[0] - (Math.sin(u * 5.3) * 0.5 + Math.sin(u * 11.7) * 0.25 + 0.75) * px * hill[2] * 4);
				rect(g, x, y, px, h - y, col);
			}
		}

		// pixel trees in three layers; crowns sway with the wind, rows higher up swaying more
		for (int li = 0; li < TREE_LAYERS.length; li++) {
			int leaf = TREE_COLORS[li][0], leafDark = TREE_COLORS[li][1], trunk = TREE_COLORS[li][2];
			float base = TREE_LAYERS[li][0], par = TREE_LAYERS[li][2], size = TREE_LAYERS[li][3], sway = TREE_LAYERS[li][4];
			Rng rr = new Rng((long) TREE_LAYERS[li][1]);
			float u = px * size / 2;
			float ground = (float) snapOp.applyAsDouble(h * base);
			rect(g, 0, ground, w, h - ground, leaf);
			float shift = (t * 5 * par) % (u * 40);
			for (float x = -u * 20 - shift; x < w + u * 20; x += u * (7 + (int) Math.floor(rr.next() * 5))) {
				int th = (int) Math.floor(5 + rr.next() * 4);
				float tx = (float) snapOp.applyAsDouble(x);
				rect(g, tx + u * 2, ground - u * 3, u, u * 3, trunk);
				float wind = (float) (Math.sin(t * 1.4 + tx * 0.013 + li) * 0.6 + Math.sin(t * 0.55 + tx * 0.004) * 0.4);
				for (int row = 0; row < th; row++) {
					int width = row == th - 1 ? 3 : row == 0 ? 3 : 5;
					float off = (float) snapOp.applyAsDouble(wind * sway * px * ((float) row / th) * 1.5f);
					float left = tx + u * (2.5f - width / 2f) + off, y = ground - u * 3 - u * (row + 1);
					rect(g, left, y, u * width, u, leaf);
					rect(g, left + u * (width - 1), y, u, u, leafDark);
					if (row == 0) rect(g, left, y, u * width, u * 0.5f, leafDark);
					if (row >= th - 2) rect(g, left, y, u, u, 0x24FFFFFF);
				}
			}
		}

		// grass tufts along the front edge
		float edge = (float) snapOp.applyAsDouble(h * 0.95f);
		for (float x = 0; x < w; x += px * 5) {
			int lean = (int) Math.round(Math.sin(t * 2 + x * 0.05) * 1.2);
			rect(g, x + lean * px, edge - px * 2, px, px * 2, 0xFF2F6247);
			rect(g, x + px * 2, edge - px, px, px, 0xFF2F6247);
		}

		// falling leaves drifting with the wind
		Rng lr = new Rng(77);
		for (int i = 0; i < 18; i++) {
			double life = 9 + lr.next() * 6, phase = lr.next() * life;
			double p = ((t + phase) % life) / life;
			double x0 = lr.next() * w * 1.2;
			float x = (float) snapOp.applyAsDouble(x0 - p * w * 0.25 + Math.sin(t * 2 + i) * 16);
			float y = (float) snapOp.applyAsDouble(h * (0.55 + p * 0.45));
			boolean flip = Math.sin(t * 4 + i) > 0;
			rect(g, x, y, flip ? px * 2 : px, flip ? px : px * 2, i % 3 == 0 ? 0xE6C9A66B : 0xE65E9A62);
		}

		// light haze at the bottom
		g.fillGradient(0, Math.round(h * 0.8f), w, h, 0x00F6F4EE, 0x40F6F4EE);
	}
}
