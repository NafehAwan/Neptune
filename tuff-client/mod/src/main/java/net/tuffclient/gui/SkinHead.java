package net.tuffclient.gui;

import java.util.Arrays;

import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/**
 * 3D skin heads for the GUI, matching the launcher's: the 8x8x8 head is
 * textured from the skin, and every outer-layer pixel is its own block
 * standing off the head.
 * <p>
 * The GUI only draws flat quads, but an orthographic cube face is a
 * parallelogram, so each face is one texture blit under an affine pose.
 * Outer-layer blocks are sorted back to front; empty outer pixels are
 * transparent in the skin, so their blocks draw as nothing.
 */
public final class SkinHead {
	// faces in the order front, back, right (+x), left (-x), top, bottom
	private static final int[][] REGION = {{8, 8}, {24, 8}, {16, 8}, {0, 8}, {8, 0}, {16, 0}};
	/** Corner of texel (0, 0) of each face, and the step for one texel along u and v (head is 8 units). */
	private static final float[][] ORIGIN = {{-4, 4, 4}, {4, 4, -4}, {4, 4, 4}, {-4, 4, -4}, {-4, 4, -4}, {-4, -4, -4}};
	private static final float[][] EU = {{1, 0, 0}, {-1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {1, 0, 0}};
	private static final float[][] EV = {{0, -1, 0}, {0, -1, 0}, {0, -1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, 1}};
	private static final float[][] NORMAL = {{0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
	/** Key light, the same direction as the launcher's (in view space). */
	private static final float[] LIGHT = normalize(-4, 8, 10);
	/** Head box width in head units: the view the launcher's camera frames. */
	private static final float FRAME = 13f;

	/** Reused for every face; the pose copies it. */
	private static final Matrix3x2f FACE = new Matrix3x2f();
	private static final int VOXELS = 6 * 64;
	private static final long[] order = new long[VOXELS];
	/** Outer-layer block centres never change, so they are worked out once. */
	private static final float[][] CENTRES = new float[VOXELS][];

	static {
		for (int f = 0; f < 6; f++)
			for (int v = 0; v < 8; v++)
				for (int u = 0; u < 8; u++) CENTRES[f * 64 + v * 8 + u] = blockCentre(f, u, v);
	}

	private SkinHead() {
	}

	private static float[] normalize(float x, float y, float z) {
		float l = (float) Math.sqrt(x * x + y * y + z * z);
		return new float[] {x / l, y / l, z / l};
	}

	/**
	 * Draws a head centred on (cx, cy) filling a box of {@code size} GUI units.
	 * Angles are in degrees, like the launcher's.
	 */
	public static void draw(Gfx g, Identifier skin, float cx, float cy, float size, float yawDeg, float pitchDeg, float alpha) {
		if (alpha <= 0.01f) return;
		// the default skin's outer layer is empty: no blocks to sort or draw
		boolean voxels = !skin.equals(net.tuffclient.account.SkinTextures.STEVE);
		float yaw = (float) Math.toRadians(yawDeg), tilt = (float) Math.toRadians(-pitchDeg);
		float cyw = (float) Math.cos(yaw), syw = (float) Math.sin(yaw);
		float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
		float unit = size / FRAME;
		// view-space rotation matrix rows (rotate around y by yaw, then around x by tilt)
		float[] r0 = {cyw, 0, syw};
		float[] r1 = {st * syw, ct, -st * cyw};
		float[] r2 = {-ct * syw, st, ct * cyw};
		int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24;

		// which faces look towards the camera, and how bright they are
		boolean[] front = new boolean[6];
		int[] shade = new int[6];
		float[][] su = new float[6][2], sv = new float[6][2];
		for (int f = 0; f < 6; f++) {
			float[] n = NORMAL[f];
			float nx = dot(r0, n), ny = dot(r1, n), nz = dot(r2, n);
			front[f] = nz > 1e-4f;
			float lambert = (1.6f + 1.9f * Math.max(0, nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2])) / (float) Math.PI;
			int grey = Math.round(255 * (float) Math.pow(Math.min(1, lambert), 1 / 2.2));
			shade[f] = a | grey << 16 | grey << 8 | grey;
			su[f][0] = dot(r0, EU[f]) * unit;
			su[f][1] = -dot(r1, EU[f]) * unit;
			sv[f][0] = dot(r0, EV[f]) * unit;
			sv[f][1] = -dot(r1, EV[f]) * unit;
		}

		// outer-layer blocks: centre depth for sorting; blocks on faces turned away go behind the head
		int count = 0;
		if (voxels) {
			for (int i = 0; i < VOXELS; i++) {
				float[] c = CENTRES[i];
				float depth = r2[0] * c[0] + r2[1] * c[1] + r2[2] * c[2];
				// pack depth (sortable) and the block index into one long
				int key = Float.floatToIntBits(depth);
				key ^= (key >> 31) & 0x7FFFFFFF;
				order[count++] = ((long) key << 32) | i;
			}
			Arrays.sort(order, 0, count);
		}

		for (int pass = 0; pass < 2; pass++) {
			if (pass == 1) {
				for (int f = 0; f < 6; f++) {
					if (!front[f]) continue;
					float[] o = project(ORIGIN[f], r0, r1, cx, cy, unit);
					quad(g, skin, o[0], o[1], su[f], sv[f], REGION[f][0], REGION[f][1], 8, shade[f]);
				}
			}
			for (int i = 0; i < count; i++) {
				int index = (int) order[i];
				int f = index / 64, v = (index / 8) % 8, u = index % 8;
				if (front[f] != (pass == 1)) continue;
				float[] c = CENTRES[index];
				int tu = REGION[f][0] + 32 + u, tv = REGION[f][1] + v;
				// each block shows up to three faces; every face uses the block's own texel
				for (int bf = 0; bf < 6; bf++) {
					if (!front[bf]) continue;
					float ox = c[0] + 0.5f * (NORMAL[bf][0] - EU[bf][0] - EV[bf][0]);
					float oy = c[1] + 0.5f * (NORMAL[bf][1] - EU[bf][1] - EV[bf][1]);
					float oz = c[2] + 0.5f * (NORMAL[bf][2] - EU[bf][2] - EV[bf][2]);
					float px = cx + (r0[0] * ox + r0[1] * oy + r0[2] * oz) * unit;
					float py = cy - (r1[0] * ox + r1[1] * oy + r1[2] * oz) * unit;
					quad(g, skin, px, py, su[bf], sv[bf], tu, tv, 1, shade[bf]);
				}
			}
		}
	}

	/** Centre of the outer-layer block over texel (u, v) of face f: half a unit off the head. */
	private static float[] blockCentre(int f, int u, int v) {
		float[] o = ORIGIN[f], eu = EU[f], ev = EV[f], n = NORMAL[f];
		return new float[] {
				o[0] + (u + 0.5f) * eu[0] + (v + 0.5f) * ev[0] + 0.5f * n[0],
				o[1] + (u + 0.5f) * eu[1] + (v + 0.5f) * ev[1] + 0.5f * n[1],
				o[2] + (u + 0.5f) * eu[2] + (v + 0.5f) * ev[2] + 0.5f * n[2]};
	}

	private static float dot(float[] a, float[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static float[] project(float[] p, float[] r0, float[] r1, float cx, float cy, float unit) {
		return new float[] {cx + dot(r0, p) * unit, cy - dot(r1, p) * unit};
	}

	/** One textured parallelogram: {@code n} texels square from (tu, tv), spanned by the screen vectors u and v per texel. */
	private static void quad(Gfx g, Identifier skin, float ox, float oy, float[] u, float[] v, int tu, int tv, int n, int color) {
		g.push();
		g.mul(FACE.set(u[0], u[1], v[0], v[1], ox, oy));
		g.texture(skin, 0, 0, tu, tv, n, n, n, n, 64, 64, color);
		g.pop();
	}
}
