package net.tuffclient.module.util;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.HitResult;
import net.tuffclient.gui.Gfx;
import net.tuffclient.gui.Theme;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;
import net.tuffclient.setting.ColorSetting;
import net.tuffclient.setting.NumberSetting;

/**
 * Replaces the vanilla crosshair. Shapes: the game's own, built-in ones, or
 * one you draw yourself on a 15x15 grid. With Environmental blend on it
 * inverts what's behind it like the vanilla crosshair; off, it's a plain
 * color (white by default). Aiming at a player or mob can turn it red or
 * swap it for the Target shape.
 */
public final class CrosshairModule extends Module {
	public static final int GRID = 15;
	private static final Identifier VANILLA = Identifier.withDefaultNamespace("hud/crosshair");
	private static final int[] OLD_COLORS = {0xFFFFFFFF, Theme.COPPER_HI, 0xFF8FE3B0, 0xFF8EC9FF, 0xFFFF6B6B, 0xFFF2D06B};

	public final ChoiceSetting style = add(new ChoiceSetting("style", "Style", 0, "Default", "Cross", "Dot", "Square", "T", "Circle", "Target", "Draw your own"));
	public final BoolSetting blend = add(new BoolSetting("blend", "Environmental blend", true));
	public final ColorSetting color = add(new ColorSetting("crosshair_color", "Color", 0xFFFFFFFF, true)).showIf(() -> !blend.get());
	public final NumberSetting size = add(new NumberSetting("size", "Size", 5, 2, 12, 1)).showIf(() -> shaped());
	public final NumberSetting gap = add(new NumberSetting("gap", "Gap", 2, 0, 6, 1)).showIf(() -> shaped());
	public final NumberSetting thickness = add(new NumberSetting("thickness", "Thickness", 1, 1, 3, 1)).showIf(() -> shaped() || style.choice().equals("Target"));
	public final BoolSetting dot = add(new BoolSetting("dot", "Center dot", false)).showIf(() -> !style.choice().equals("Default") && !style.choice().equals("Dot"));
	public final BoolSetting outline = add(new BoolSetting("outline", "Outline", true)).showIf(() -> !blend.get() && !style.choice().equals("Default"));
	public final ChoiceSetting onTarget = add(new ChoiceSetting("on_target", "When aiming at a player or mob", 0, "No change", "Turn red", "Switch to Target"));
	public final ColorSetting targetTint = add(new ColorSetting("target_color", "Aiming color", 0xFFFF3B3B, false)).showIf(() -> onTarget.get() == 1);
	public final BoolSetting thirdPerson = add(new BoolSetting("third_person", "Show in third person", false));

	/** The crosshair you draw yourself, row by row. */
	public final boolean[] grid = new boolean[GRID * GRID];
	/** Drawing mirrors across both middle lines. */
	public boolean mirror = true;

	private final ChoiceSetting oldColor = new ChoiceSetting("color", "Color", 0, "White", "Copper", "Mint", "Sky", "Red", "Gold");

	public CrosshairModule() {
		super("crosshair", "Crosshair", "Your own crosshair: the game's, a built-in shape or one you draw. Can turn red on players and mobs.", Category.VISUAL, false);
		resetGrid();
	}

	private boolean shaped() {
		return switch (style.choice()) {
			case "Cross", "Square", "T", "Circle" -> true;
			default -> false;
		};
	}

	/** Whether it draws in the current camera view. */
	public boolean shows(Minecraft mc) {
		return mc.options.getCameraType().isFirstPerson() || thirdPerson.get();
	}

	// ------------------------------------------------------------------ grid

	public boolean cell(int x, int y) {
		return grid[y * GRID + x];
	}

	/** Sets a cell (and its mirror images when mirroring). */
	public void paint(int x, int y, boolean on) {
		int m = GRID - 1;
		grid[y * GRID + x] = on;
		if (mirror) {
			grid[y * GRID + (m - x)] = on;
			grid[(m - y) * GRID + x] = on;
			grid[(m - y) * GRID + (m - x)] = on;
		}
	}

	public void clearGrid() {
		java.util.Arrays.fill(grid, false);
	}

	/** A small plus to start from. */
	public void resetGrid() {
		clearGrid();
		int c = GRID / 2;
		for (int i = c - 4; i <= c + 4; i++) {
			if (Math.abs(i - c) < 2) continue;
			grid[c * GRID + i] = true;
			grid[i * GRID + c] = true;
		}
		grid[c * GRID + c] = true;
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		StringBuilder sb = new StringBuilder();
		for (boolean b : grid) sb.append(b ? '1' : '0');
		json.addProperty("grid", sb.toString());
		json.addProperty("mirror", mirror);
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		var s = json.has("settings") && json.get("settings").isJsonObject() ? json.getAsJsonObject("settings") : null;
		if (s != null && s.has("color") && !s.has("crosshair_color")) {
			// before custom colors there were six named ones, and no blend
			oldColor.fromJson(s.get("color"));
			color.set(OLD_COLORS[oldColor.get()]);
			blend.set(false);
		}
		if (s != null && !s.has("on_target") && s.has("target") && s.get("target").getAsBoolean()) onTarget.set(1);
		if (s != null && !s.has("blend") && s.has("style")) blend.set(false);
		if (json.has("grid")) {
			String g = json.get("grid").getAsString();
			for (int i = 0; i < grid.length && i < g.length(); i++) grid[i] = g.charAt(i) == '1';
		}
		if (json.has("mirror")) mirror = json.get("mirror").getAsBoolean();
	}

	// ---------------------------------------------------------------- drawing

	public void render(Gfx g) {
		Minecraft mc = Minecraft.getInstance();
		boolean aiming = onTarget.get() != 0 && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY;
		String shape = aiming && onTarget.get() == 2 ? "Target" : style.choice();
		boolean red = aiming && onTarget.get() == 1;
		draw(g, g.guiWidth() / 2, g.guiHeight() / 2, shape, red);
	}

	/** Draws a shape centred on (cx, cy): also used for the preview in the menu. */
	public void draw(Gfx g, int cx, int cy, String shape, boolean red) {
		boolean invert = blend.get() && !red;
		int c = red ? targetTint.argb() : color.argb();
		if (shape.equals("Default")) {
			if (invert) g.invertSprite(VANILLA, cx - 7, cy - 7, 15, 15);
			else g.sprite(VANILLA, cx - 7, cy - 7, 15, 15, c);
			return;
		}
		Mask mask = mask(shape);
		if (dot.get() && !shape.equals("Dot")) {
			int lo = thickness.get().intValue() / 2, hi = thickness.get().intValue() - lo;
			mask.rect(-lo, -lo, hi, hi);
		}
		if (invert) {
			mask.runs((x0, y, x1) -> g.invertRect(cx + x0, cy + y, cx + x1, cy + y + 1));
		} else {
			if (outline.get()) mask.grown().runs((x0, y, x1) -> g.fill(cx + x0, cy + y, cx + x1, cy + y + 1, 0xC0000000));
			mask.runs((x0, y, x1) -> g.fill(cx + x0, cy + y, cx + x1, cy + y + 1, c));
		}
	}

	/** Every pixel of a shape, relative to the centre, each drawn once (overlaps would cancel out an inverting blend). */
	private Mask mask(String shape) {
		Mask m = new Mask();
		int s = size.get().intValue(), gp = gap.get().intValue(), t = thickness.get().intValue();
		int lo = t / 2, hi = t - lo;
		switch (shape) {
			case "Dot" -> m.rect(-lo - 1, -lo - 1, hi + 1, hi + 1);
			case "Square" -> {
				m.rect(-s, -s, s, -s + t);
				m.rect(-s, s - t, s, s);
				m.rect(-s, -s, -s + t, s);
				m.rect(s - t, -s, s, s);
			}
			case "Circle" -> {
				int n = Math.max(12, s * 6);
				for (int i = 0; i < n; i++) {
					double a = Math.PI * 2 * i / n;
					int px = (int) Math.round(Math.cos(a) * s), py = (int) Math.round(Math.sin(a) * s);
					m.rect(px - lo, py - lo, px + hi, py + hi);
				}
			}
			case "T" -> {
				m.rect(-s - gp, -lo, -gp, hi);
				m.rect(gp + 1, -lo, gp + 1 + s, hi);
				m.rect(-lo, gp + 1, hi, gp + 1 + s);
			}
			case "Target" -> {
				// a plus through the centre with a dot in each corner between the arms
				m.rect(-4, -lo, 5, hi);
				m.rect(-lo, -4, hi, 5);
				int d = 1 + t; // dot size
				int[][] spans = {{-lo - 1 - d, -lo - 1}, {1 + hi, 1 + hi + d}};
				for (int[] xs : spans) for (int[] ys : spans) m.rect(xs[0], ys[0], xs[1], ys[1]);
			}
			case "Draw your own" -> {
				int half = GRID / 2;
				for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID; x++) if (cell(x, y)) m.set(x - half, y - half);
			}
			default -> {
				m.rect(-s - gp, -lo, -gp, hi);
				m.rect(gp + 1, -lo, gp + 1 + s, hi);
				m.rect(-lo, -s - gp, hi, -gp);
				m.rect(-lo, gp + 1, hi, gp + 1 + s);
			}
		}
		return m;
	}

	/** Pixels around the centre (-R..R each way). */
	private static final class Mask {
		static final int R = 24, W = R * 2 + 1;
		final boolean[] px = new boolean[W * W];

		void set(int x, int y) {
			if (x < -R || x > R || y < -R || y > R) return;
			px[(y + R) * W + (x + R)] = true;
		}

		/** Fills [x0, x1) x [y0, y1). */
		void rect(int x0, int y0, int x1, int y1) {
			for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) set(x, y);
		}

		/** The shape grown by one pixel all round (for the outline). */
		Mask grown() {
			Mask o = new Mask();
			for (int y = -R; y <= R; y++) {
				for (int x = -R; x <= R; x++) {
					if (px[(y + R) * W + (x + R)]) o.rect(x - 1, y - 1, x + 2, y + 2);
				}
			}
			return o;
		}

		interface Run {
			void draw(int x0, int y, int x1);
		}

		/** Each row's runs of set pixels, so they draw as few rectangles. */
		void runs(Run r) {
			for (int y = -R; y <= R; y++) {
				int start = Integer.MIN_VALUE;
				for (int x = -R; x <= R + 1; x++) {
					boolean on = x <= R && px[(y + R) * W + (x + R)];
					if (on && start == Integer.MIN_VALUE) start = x;
					if (!on && start != Integer.MIN_VALUE) {
						r.draw(start, y, x);
						start = Integer.MIN_VALUE;
					}
				}
			}
		}
	}
}
