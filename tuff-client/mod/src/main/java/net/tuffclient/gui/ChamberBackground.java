package net.tuffclient.gui;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The animated Trial Chamber behind the Tuff title screen, matching the
 * launcher's scene: tuff-brick walls with oxidised copper grates, chiseled
 * pillars with flickering copper bulbs, a tiled floor, a rotating trial
 * spawner, rising embers and drifting dust. One GUI pixel is one "texel", so
 * everything stays crisp at any GUI scale. Drawn only with solid fills.
 */
public final class ChamberBackground {
	private static final int MORTAR = 0xFF22251F;
	private static final int[] BRICK = {0xFF3A3D36, 0xFF40443C, 0xFF474B42, 0xFF4E5248};
	private static final int BRICK_HI = 0xFF575B51;
	private static final int[] PILLAR = {0xFF5A5E54, 0xFF686C61, 0xFF4B4E46, 0xFF3B3E37};
	private static final int OXID = 0xFF4F8F7F;
	private static final int RECESS = 0xFF141613;
	private static final int FLOOR_A = 0xFF3D4039;
	private static final int FLOOR_B = 0xFF373A33;
	private static final int SEAM = 0xFF25281F;
	private static final int[] FLAME = {0xFFFFE7A8, 0xFFF0A877, 0xFFD9824B};

	private static final int EMBERS = 34;
	private static final int DUST = 60;
	private final float[] ex = new float[EMBERS], ey = new float[EMBERS], evx = new float[EMBERS], evy = new float[EMBERS], elife = new float[EMBERS], emax = new float[EMBERS];
	private final float[] dx = new float[DUST], dy = new float[DUST], dvx = new float[DUST], dvy = new float[DUST], dlife = new float[DUST], dmax = new float[DUST], ddepth = new float[DUST];
	private final Random random = new Random(1734);
	private float time = 12;
	private long last = System.nanoTime();
	private int lastW = -1;
	private int lastH = -1;

	private static int hash(int x, int y) {
		int h = x * 73856093 ^ y * 19349663 ^ 0x5F3759DF;
		h ^= h >>> 13;
		h *= 1274126177;
		return h ^ (h >>> 16);
	}

	public void render(GuiGraphicsExtractor g, int w, int h, boolean animate) {
		long now = System.nanoTime();
		float dt = Math.min(0.05f, (now - last) / 1e9f);
		last = now;
		if (w != lastW || h != lastH) reset(w, h);
		int horizon = Math.round(h * 0.72f);
		if (animate) step(dt, w, h, horizon);

		float cam = (float) Math.sin(time * 0.07) * 40;
		wall(g, w, horizon, Math.round(-cam * 0.22f));
		pillars(g, w, horizon, Math.round(-cam * 0.55f));
		floor(g, w, h, horizon, cam);
		int spawnerX = Math.round(w * 0.7f - cam * 0.8f);
		spawner(g, spawnerX, horizon, h);
		particles(g, w, cam);
		// soft vignette: darken the top and bottom bands
		for (int i = 0; i < 12; i++) {
			int a = (int) (110 * (1 - i / 12f));
			g.fill(0, i * 3, w, i * 3 + 3, a << 24 | 0x0A0B0A);
			g.fill(0, h - (i + 1) * 4, w, h - i * 4, (int) (a * 1.4f) << 24 | 0x141614);
		}
	}

	private void reset(int w, int h) {
		lastW = w;
		lastH = h;
		for (int i = 0; i < DUST; i++) spawnDust(i, w, h, true);
		for (int i = 0; i < EMBERS; i++) elife[i] = emax[i] = 0;
	}

	private void wall(GuiGraphicsExtractor g, int w, int horizon, int offset) {
		g.fill(0, 0, w, horizon, MORTAR);
		int bw = 16, bh = 8;
		for (int row = 0; row * bh < horizon; row++) {
			int shift = (row % 2) * (bw / 2) + Math.floorMod(offset, bw);
			for (int x = -bw * 2 + shift; x < w + bw; x += bw) {
				int col = Math.floorDiv(x - offset, bw);
				int c = BRICK[Math.floorMod(hash(col, row), BRICK.length)];
				g.fill(x + 1, row * bh + 1, x + bw, row * bh + bh, c);
				g.fill(x + 1, row * bh + 1, x + bw - 1, row * bh + 2, BRICK_HI);
			}
		}
		// arched alcoves with copper grates
		for (int x = Math.floorMod(offset, 152) - 152 + 46; x < w; x += 152) {
			int aw = 46, ah = 74, ay = horizon - ah - 12;
			int[] steps = {9, 6, 4, 3, 2, 1, 1};
			for (int i = 0; i < ah; i++) {
				int inset = i < steps.length ? steps[i] : 0;
				g.fill(x + inset, ay + i, x + aw - inset, ay + i + 1, RECESS);
			}
			for (int bx = x + 5; bx < x + aw - 3; bx += 6) g.fill(bx, ay + 7, bx + 1, ay + ah, OXID);
			for (int by = ay + 16; by < ay + ah; by += 12) g.fill(x + 2, by, x + aw - 2, by + 1, OXID);
			g.fill(x - 2, ay + ah, x + aw + 2, ay + ah + 3, 0xFF5A3420);
			g.fill(x - 2, ay + ah, x + aw + 2, ay + ah + 1, Theme.COPPER_LO);
		}
		// ceiling shadow
		for (int i = 0; i < 16; i++) {
			int a = (int) (200 * (1 - i / 16f));
			int y = Math.round(horizon * 0.35f * i / 16f);
			int y2 = Math.round(horizon * 0.35f * (i + 1) / 16f);
			g.fill(0, y, w, y2, a << 24 | 0x090A09);
		}
	}

	private void pillars(GuiGraphicsExtractor g, int w, int horizon, int offset) {
		int pw = 24;
		for (int x = Math.floorMod(offset, 176) - 176 + 20; x < w + pw; x += 176) {
			g.fill(x, 0, x + pw, horizon, PILLAR[0]);
			g.fill(x, 0, x + 2, horizon, PILLAR[1]);
			g.fill(x + pw - 4, 0, x + pw, horizon, PILLAR[2]);
			for (int y = 0; y < horizon; y += 18) {
				g.fill(x, y, x + pw, y + 1, PILLAR[3]);
				g.fill(x + 6, y + 5, x + pw - 6, y + 6, PILLAR[2]);
				g.fill(x + 6, y + 13, x + pw - 6, y + 14, PILLAR[2]);
				g.fill(x + 6, y + 5, x + 7, y + 14, PILLAR[2]);
				g.fill(x + pw - 7, y + 5, x + pw - 6, y + 14, PILLAR[2]);
			}
			g.fill(x - 4, horizon - 10, x + pw + 4, horizon, PILLAR[1]);
			g.fill(x - 4, horizon - 1, x + pw + 4, horizon, PILLAR[3]);
			// copper bulb with a flickering core and a stepped glow
			int bx = x + pw / 2, by = Math.round(horizon * 0.6f);
			float phase = Math.floorMod(Math.floorDiv(x - offset, 176), 7) * 1.7f;
			float f = 0.82f + (float) (Math.sin(time * 3 + phase) * 0.06 + Math.sin(time * 11.3 + phase * 2) * 0.05);
			for (int r = 5; r >= 1; r--) {
				int a = (int) (22 * f * (6 - r) / 5f);
				g.fill(bx - r * 9, by - r * 9, bx + r * 9, by + r * 9, a << 24 | 0xF0965A);
			}
			g.fill(bx - 7, by - 7, bx + 7, by + 7, 0xFF5A3420);
			g.fill(bx - 6, by - 6, bx + 6, by + 6, Theme.COPPER_LO);
			g.fill(bx - 4, by - 4, bx + 4, by + 4, Theme.COPPER_HI);
			g.fill(bx - 3, by - 3, bx + 3, by + 3, f > 0.86f ? 0xFFFFF1C9 : 0xFFFFE1A0);
		}
		for (int i = 0; i < 10; i++) {
			int a = (int) (190 * (1 - i / 10f));
			int y = Math.round(horizon * 0.3f * i / 10f);
			int y2 = Math.round(horizon * 0.3f * (i + 1) / 10f);
			g.fill(0, y, w, y2, a << 24 | 0x090A09);
		}
	}

	private void floor(GuiGraphicsExtractor g, int w, int h, int horizon, float cam) {
		int rows = h - horizon;
		for (int y = 0; y < rows; y += 2) {
			float z = 340f / (y + 6);
			float light = 0.5f + y / (float) rows * 0.55f;
			int band = Math.floorMod((int) Math.floor(z * 1.8f / 10f), 2) == 0 ? FLOOR_A : FLOOR_B;
			boolean seamRow = (z * 1.8f / 10f) % 1f < 0.09f;
			g.fill(0, horizon + y, w, horizon + y + 2, shade(seamRow ? SEAM : band, light));
			float scale = z * 0.11f;
			float worldLeft = -w / 2f * scale + cam * 0.45f;
			int first = (int) Math.ceil(worldLeft / 10f);
			for (int i = first; ; i++) {
				int sx = Math.round((i * 10f - cam * 0.45f) / scale + w / 2f);
				if (sx > w) break;
				g.fill(sx, horizon + y, sx + 1, horizon + y + 2, shade(SEAM, light));
			}
		}
		// fog where the floor meets the wall
		for (int i = 0; i < 8; i++) g.fill(0, horizon + i * 2, w, horizon + i * 2 + 2, (int) (200 * (1 - i / 8f)) << 24 | 0x151714);
	}

	private static int shade(int argb, float light) {
		int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * light));
		int gr = Math.min(255, (int) (((argb >> 8) & 0xFF) * light));
		int b = Math.min(255, (int) ((argb & 0xFF) * light));
		return 0xFF000000 | r << 16 | gr << 8 | b;
	}

	private void spawner(GuiGraphicsExtractor g, int cx, int horizon, int h) {
		int size = Math.max(30, Math.round(h * 0.19f));
		int baseY = horizon + Math.round((h - horizon) * 0.32f);
		int pw = size + 16;
		g.fill(cx - pw / 2 - 4, baseY + 1, cx + pw / 2 + 4, baseY + 5, 0x59000000);
		g.fill(cx - pw / 2, baseY - 12, cx + pw / 2, baseY, PILLAR[2]);
		g.fill(cx - pw / 2, baseY - 12, cx + pw / 2, baseY - 10, PILLAR[1]);
		float cy = baseY - 12 - size * 0.62f + (float) Math.sin(time * 1.3) * 1.5f;
		float pulse = 0.85f + (float) (Math.sin(time * 2.1) * 0.1 + Math.sin(time * 7.3) * 0.05);
		for (int r = 6; r >= 1; r--) {
			int a = (int) (26 * pulse * (7 - r) / 6f);
			int s = Math.round(size * 0.4f * r);
			g.fill(cx - s, Math.round(cy) - s, cx + s, Math.round(cy) + s, a << 24 | 0xE6783C);
		}

		float half = size / 2f, angle = time * 0.55f, tilt = -0.42f;
		float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle), ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
		float[][] v = new float[8][3];
		for (int i = 0; i < 8; i++) {
			float x = (i & 1) == 0 ? -half : half;
			float y = (i & 2) == 0 ? -half : half;
			float z = (i & 4) == 0 ? -half : half;
			float x1 = x * ca - z * sa, z1 = x * sa + z * ca;
			float y2 = y * ct - z1 * st, z2 = y * st + z1 * ct;
			float f = 160 / (160 + z2);
			v[i][0] = cx + x1 * f;
			v[i][1] = cy + y2 * f;
			v[i][2] = z2;
		}
		int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		for (int[] e : edges) {
			if (v[e[0]][2] + v[e[1]][2] > 0) line(g, v[e[0]], v[e[1]], 0xFF2A2D29);
		}
		// flame core between the back and front bars
		for (int i = 0; i < 14; i++) {
			int fx = cx + (int) Math.round(Math.sin(time * 5 + i * 1.7) * (4 - i * 0.2));
			int fy = Math.round(cy) + 5 - i;
			int c = FLAME[Math.min(2, i / 5)];
			g.fill(fx - 1, fy, fx + 2 + (i < 6 ? 1 : 0), fy + 1, c);
		}
		for (int[] e : edges) {
			if (v[e[0]][2] + v[e[1]][2] <= 0) line(g, v[e[0]], v[e[1]], 0xFF73776C);
		}
	}

	private static void line(GuiGraphicsExtractor g, float[] a, float[] b, int color) {
		int steps = (int) Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
		for (int i = 0; i <= steps; i++) {
			float t = steps == 0 ? 0 : i / (float) steps;
			int x = Math.round(a[0] + (b[0] - a[0]) * t);
			int y = Math.round(a[1] + (b[1] - a[1]) * t);
			g.fill(x, y, x + 1, y + 1, color);
		}
	}

	private void step(float dt, int w, int h, int horizon) {
		time += dt;
		int size = Math.max(30, Math.round(h * 0.19f));
		float coreY = horizon + Math.round((h - horizon) * 0.32f) - 12 - size * 0.62f;
		for (int i = 0; i < EMBERS; i++) {
			elife[i] += dt;
			if (elife[i] >= emax[i]) {
				if (random.nextFloat() < dt * 14) {
					ex[i] = w * 0.7f + (random.nextFloat() - 0.5f) * 10;
					ey[i] = coreY;
					evx[i] = (random.nextFloat() - 0.5f) * 3;
					evy[i] = -(6 + random.nextFloat() * 12);
					elife[i] = 0;
					emax[i] = 1.4f + random.nextFloat() * 2;
				}
				continue;
			}
			ex[i] += (evx[i] + (float) Math.sin(time * 3 + i) * 4) * dt;
			ey[i] += evy[i] * dt;
		}
		for (int i = 0; i < DUST; i++) {
			dlife[i] += dt;
			dx[i] += (dvx[i] + (float) Math.sin(time * 0.5 + i) * 1.5f) * dt;
			dy[i] += dvy[i] * dt * 0.6f;
			if (dlife[i] > dmax[i] || dy[i] < -2) spawnDust(i, w, h, false);
		}
	}

	private void spawnDust(int i, int w, int h, boolean anywhere) {
		dx[i] = random.nextFloat() * w;
		dy[i] = anywhere ? random.nextFloat() * h : h + 2;
		dvx[i] = (random.nextFloat() - 0.3f) * 2.2f;
		dvy[i] = -(0.6f + random.nextFloat() * 2);
		dlife[i] = 0;
		dmax[i] = 8 + random.nextFloat() * 14;
		ddepth[i] = 0.3f + random.nextFloat() * 0.9f;
	}

	private void particles(GuiGraphicsExtractor g, int w, float cam) {
		for (int i = 0; i < EMBERS; i++) {
			if (elife[i] >= emax[i]) continue;
			float k = elife[i] / emax[i];
			int a = (int) ((1 - k) * 230);
			int rgb = k < 0.4f ? 0xFFD696 : 0xD9824B;
			int x = Math.round(ex[i] - cam * 0.8f), y = Math.round(ey[i]);
			g.fill(x, y, x + 1, y + 1, a << 24 | rgb);
		}
		for (int i = 0; i < DUST; i++) {
			float k = dlife[i] / dmax[i];
			float alpha = (float) Math.sin(k * Math.PI) * (0.18f + 0.2f * (float) Math.sin(time * 2 + i)) * ddepth[i];
			if (alpha <= 0.01f) continue;
			int x = Math.floorMod(Math.round(dx[i] - cam * ddepth[i] * 0.6f), Math.max(1, w));
			int y = Math.round(dy[i]);
			int s = ddepth[i] > 1 ? 2 : 1;
			g.fill(x, y, x + s, y + 1, (int) (alpha * 255) << 24 | 0xE8DEC8);
		}
	}
}
