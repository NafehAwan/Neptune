package net.tuffclient.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.tuffclient.ModuleManager;
import net.tuffclient.TuffClient;
import net.tuffclient.TuffConfig;
import net.tuffclient.module.hud.CpsModule;
import net.tuffclient.module.hud.HudModule;

/**
 * Right Shift in game: the HUD stays visible and can be edited in place. Drag
 * a module to move it (it snaps to screen edges, the centre and other
 * modules), drag its corner to resize, and use its bar for settings or to
 * remove it. The card in the middle opens Glade's mods and settings.
 */
public final class HudEditorScreen extends GladeScreen {
	private static final int SNAP = 4;
	private static final int BAR_H = 13;
	private static final Identifier EMBLEM = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "textures/gui/title/emblem.png");
	/** The small buttons under the card: page, icon, label. */
	private static final Object[][] QUICK = {
			{TuffMenuScreen.Page.THEME, "palette", "Theme"},
			{TuffMenuScreen.Page.PACKS, "package", "Packs"},
			{TuffMenuScreen.Page.SETTINGS, "sliders", "Settings"},
	};
	private final Screen parent;
	private HudModule focus;
	private HudModule dragging;
	private HudModule resizing;
	private double grabX;
	private double grabY;
	private double startScale;
	private int startW;
	private Integer guideX;
	private Integer guideY;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
	}

	@Override
	protected void drawBackground(Gfx g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, minecraft.player == null ? 0xFF2A3A30 : 0x40000000);
	}

	// ------------------------------------------------------------------ layout

	private float cardX() {
		return Ui.snap(width / 2f - 70);
	}

	private float cardY() {
		return Ui.snap(height / 2f - 52);
	}

	private float[] mainButton() {
		return new float[] {width / 2f - 58, cardY() + 50, 116, 22};
	}

	private float[] quick(int i) {
		float size = 22, gap = 6, total = QUICK.length * size + (QUICK.length - 1) * gap;
		return new float[] {width / 2f - total / 2 + i * (size + gap), cardY() + 78, size, size};
	}

	/** The bar over (or under) a module: name and scale, settings, remove. */
	private float[] bar(HudModule m) {
		int[] p = ModuleManager.position(m, width, height);
		float w = barWidth(m);
		float x = Math.max(2, Math.min(width - w - 2, p[0]));
		float y = p[1] > BAR_H + 3 ? p[1] - BAR_H - 2 : p[1] + p[3] + 2;
		return new float[] {x, y, w, BAR_H};
	}

	private static String label(HudModule m) {
		return m.name + "  " + Math.round(m.scale.get() * 100) + "%";
	}

	private static float barWidth(HudModule m) {
		return Ui.width(label(m), Ui.Font.SEMIBOLD) + 10 + 2 * BAR_H;
	}

	private float[] gear(HudModule m) {
		float[] b = bar(m);
		return new float[] {b[0] + b[2] - 2 * BAR_H, b[1], BAR_H, BAR_H};
	}

	private float[] remove(HudModule m) {
		float[] b = bar(m);
		return new float[] {b[0] + b[2] - BAR_H, b[1], BAR_H, BAR_H};
	}

	private int[] handle(HudModule m) {
		int[] p = ModuleManager.position(m, width, height);
		return new int[] {p[0] + p[2] - 3, p[1] + p[3] - 3, 6, 6};
	}

	private static boolean in(double mx, double my, float[] r) {
		return Theme.inside(mx, my, r[0], r[1], r[0] + r[2], r[1] + r[3]);
	}

	private static boolean in(double mx, double my, int[] r) {
		return Theme.inside(mx, my, r[0], r[1], r[0] + r[2], r[1] + r[3]);
	}

	private boolean overCard(double mx, double my) {
		return Theme.inside(mx, my, cardX(), cardY(), cardX() + 140, cardY() + 108);
	}

	// ------------------------------------------------------------------ drawing

	@Override
	protected void draw(Gfx g, int mouseX, int mouseY, float partialTick) {
		CpsModule.sample(minecraft);
		updateFocus(mouseX, mouseY);
		for (HudModule m : TuffClient.modules().hud()) {
			if (!m.isEnabled()) continue;
			ModuleManager.renderModule(g, font, m, true);
			int[] p = ModuleManager.position(m, width, height);
			boolean active = m == focus;
			Ui.round(g, p[0] - 2, p[1] - 2, p[2] + 4, p[3] + 4, 3, active ? Theme.alpha(Theme.COPPER, 0.18f) : 0x14FFFFFF);
			Ui.outline(g, p[0] - 2, p[1] - 2, p[2] + 4, p[3] + 4, 3, active ? Theme.COPPER : 0x50FFFFFF);
		}
		if (focus != null) drawBar(g, focus, mouseX, mouseY);
		if (guideX != null) g.fill(guideX, 0, guideX + 1, height, Theme.COPPER);
		if (guideY != null) g.fill(0, guideY, width, guideY + 1, Theme.COPPER);
		drawCard(g, mouseX, mouseY);

		String help = "Drag to move  ·  Drag the corner to resize  ·  Right-click for settings  ·  Right Shift to close";
		float hw = Ui.width(help, Ui.Font.MEDIUM) + 20;
		float hy = height - 24;
		Ui.round(g, width / 2f - hw / 2, hy, hw, 16, 8, 0xD9FFFFFF, Theme.GLASS_RIM, 1);
		Ui.centered(g, help, width / 2f, hy + 4.5f, Theme.TEXT, Ui.Font.MEDIUM);
	}

	private void drawBar(Gfx g, HudModule m, int mouseX, int mouseY) {
		float[] b = bar(m);
		Ui.round(g, b[0], b[1], b[2], b[3], 4, Theme.alpha(Theme.COPPER, 0.95f));
		Ui.text(g, label(m), b[0] + 5, b[1] + 3, Theme.INK, Ui.Font.SEMIBOLD);
		float[] gear = gear(m), x = remove(m);
		if (in(mouseX, mouseY, gear)) Ui.round(g, gear[0], gear[1], gear[2], gear[3], 4, 0x33FFFFFF);
		if (in(mouseX, mouseY, x)) Ui.round(g, x[0], x[1], x[2], x[3], 4, Theme.alpha(Theme.DANGER, 0.9f));
		Ui.lucide(g, "settings", gear[0] + 2, gear[1] + 2, 9, Theme.INK);
		Ui.lucide(g, "close", x[0] + 2, x[1] + 2, 9, Theme.INK);
		int[] h = handle(m);
		Ui.round(g, h[0], h[1], h[2], h[3], 2, Theme.INK);
		Ui.round(g, h[0] + 1, h[1] + 1, h[2] - 2, h[3] - 2, 1.5f, Theme.COPPER);
	}

	private void drawCard(Gfx g, int mouseX, int mouseY) {
		float x = cardX(), y = cardY(), cx = width / 2f;
		Ui.glass(g, x, y, 140, 108, 12, 0xF2FFFFFF, 1);
		Ui.image(g, EMBLEM, cx - 15, y + 10, 30, 30, 512, 512, 0xFFFFFFFF);
		float[] main = mainButton();
		Ui.accentButton(g, main[0], main[1], main[2], main[3], in(mouseX, mouseY, main) ? 1 : 0, 1);
		String text = "Mods & Settings";
		float tw = 12 + 6 + Ui.width(text, Ui.Font.SEMIBOLD);
		float tx = cx - tw / 2;
		Ui.lucide(g, "grid", tx, main[1] + 5, 12, Theme.INK);
		Ui.text(g, text, tx + 18, main[1] + 7, Theme.INK, Ui.Font.SEMIBOLD);
		String tip = null;
		for (int i = 0; i < QUICK.length; i++) {
			float[] q = quick(i);
			boolean over = in(mouseX, mouseY, q);
			Ui.glassButton(g, q[0], q[1], q[2], q[3], over ? 1 : 0, 1);
			Ui.lucide(g, (String) QUICK[i][1], q[0] + 5, q[1] + 5, 12, over ? Theme.COPPER : Theme.TEXT);
			if (over) tip = (String) QUICK[i][2];
		}
		if (tip != null) {
			float w = Ui.width(tip, Ui.Font.MEDIUM) + 12;
			Ui.round(g, cx - w / 2, y + 112, w, 14, 7, 0xE6FFFFFF, Theme.GLASS_RIM, 1);
			Ui.centered(g, tip, cx, y + 115.5f, Theme.TEXT, Ui.Font.MEDIUM);
		}
	}

	private void updateFocus(double mx, double my) {
		if (dragging != null || resizing != null) {
			focus = dragging != null ? dragging : resizing;
			return;
		}
		if (focus != null && focus.isEnabled() && (in(mx, my, bar(focus)) || in(mx, my, handle(focus)))) return;
		focus = overCard(mx, my) ? null : at(mx, my);
	}

	private HudModule at(double mx, double my) {
		var list = TuffClient.modules().hud();
		for (int i = list.size() - 1; i >= 0; i--) {
			HudModule m = list.get(i);
			if (!m.isEnabled()) continue;
			int[] p = ModuleManager.position(m, width, height);
			if (Theme.inside(mx, my, p[0] - 2, p[1] - 2, p[0] + p[2] + 2, p[1] + p[3] + 2)) return m;
		}
		return null;
	}

	// ------------------------------------------------------------------ input

	private void openMenu(TuffMenuScreen.Page page, HudModule settingsOf) {
		TuffConfig.save(TuffClient.modules());
		TuffMenuScreen menu = new TuffMenuScreen(this, page);
		if (settingsOf != null && !settingsOf.settings().isEmpty()) menu.openSettings(settingsOf);
		net.tuffclient.Mc.setScreen(menu);
	}

	@Override
	protected boolean onClick(Input.Click event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		if (in(mx, my, mainButton())) {
			openMenu(TuffMenuScreen.Page.MODS, null);
			return true;
		}
		for (int i = 0; i < QUICK.length; i++) {
			if (in(mx, my, quick(i))) {
				openMenu((TuffMenuScreen.Page) QUICK[i][0], null);
				return true;
			}
		}
		if (overCard(mx, my)) return true;
		updateFocus(mx, my);
		if (focus != null) {
			HudModule m = focus;
			if (in(mx, my, gear(m))) {
				openMenu(TuffMenuScreen.Page.MODS, m);
				return true;
			}
			if (in(mx, my, remove(m))) {
				m.setEnabled(false);
				focus = null;
				return true;
			}
			if (in(mx, my, handle(m)) && event.button() == 0) {
				resizing = m;
				startScale = m.scale.get();
				startW = Math.max(1, ModuleManager.position(m, width, height)[2]);
				grabX = mx;
				return true;
			}
		}
		HudModule m = at(mx, my);
		if (m != null) {
			if (event.button() == 1) {
				openMenu(TuffMenuScreen.Page.MODS, m);
			} else {
				int[] p = ModuleManager.position(m, width, height);
				dragging = m;
				grabX = mx - p[0];
				grabY = my - p[1];
			}
			return true;
		}
		return super.onClick(event, doubleClick);
	}

	@Override
	protected boolean onDrag(Input.Click event, double dx, double dy) {
		if (resizing != null) {
			double w = Math.max(4, startW + (event.x() - grabX));
			resizing.scale.set(startScale * w / startW);
			return true;
		}
		if (dragging == null) return super.onDrag(event, dx, dy);
		int[] p = ModuleManager.position(dragging, width, height);
		int w = p[2], h = p[3];
		int nx = (int) Math.round(event.x() - grabX);
		int ny = (int) Math.round(event.y() - grabY);
		guideX = null;
		guideY = null;
		// candidate edges: screen edges and center, plus every other module's edges
		java.util.List<Integer> xs = new java.util.ArrayList<>(java.util.List.of(0, width / 2, width));
		java.util.List<Integer> ys = new java.util.ArrayList<>(java.util.List.of(0, height / 2, height));
		for (HudModule o : TuffClient.modules().hud()) {
			if (o == dragging || !o.isEnabled()) continue;
			int[] q = ModuleManager.position(o, width, height);
			xs.add(q[0]);
			xs.add(q[0] + q[2]);
			ys.add(q[1]);
			ys.add(q[1] + q[3]);
		}
		for (int x : xs) {
			if (Math.abs(nx - x) <= SNAP) { nx = x; guideX = x; break; }
			if (Math.abs(nx + w - x) <= SNAP) { nx = x - w; guideX = x; break; }
			if (x == width / 2 && Math.abs(nx + w / 2 - x) <= SNAP) { nx = x - w / 2; guideX = x; break; }
		}
		for (int y : ys) {
			if (Math.abs(ny - y) <= SNAP) { ny = y; guideY = y; break; }
			if (Math.abs(ny + h - y) <= SNAP) { ny = y - h; guideY = y; break; }
			if (y == height / 2 && Math.abs(ny + h / 2 - y) <= SNAP) { ny = y - h / 2; guideY = y; break; }
		}
		nx = Math.max(0, Math.min(width - w, nx));
		ny = Math.max(0, Math.min(height - h, ny));
		dragging.x = nx / (double) width;
		dragging.y = ny / (double) height;
		return true;
	}

	@Override
	protected boolean onRelease(Input.Click event) {
		dragging = null;
		resizing = null;
		guideX = null;
		guideY = null;
		return super.onRelease(event);
	}

	@Override
	protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
		HudModule m = at(mouseX, mouseY);
		if (m != null) {
			m.scale.set(m.scale.get() + (scrollY > 0 ? 0.1 : -0.1));
			return true;
		}
		return false;
	}

	@Override
	protected boolean onKey(Input.Key key) {
		if (keyMatches(TuffClient.menuKey())) {
			onClose();
			return true;
		}
		return super.onKey(key);
	}

	@Override
	public void onClose() {
		TuffConfig.save(TuffClient.modules());
		net.tuffclient.Mc.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
