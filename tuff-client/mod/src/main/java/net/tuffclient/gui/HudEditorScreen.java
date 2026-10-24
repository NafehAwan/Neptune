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
 *
 * HUD modules live in GUI units; everything Glade draws on top (outlines,
 * bars, the card, the hint) is laid out in the menu's mockup space, in the
 * menu's fonts, so it matches the Glade menu at any window size.
 */
public final class HudEditorScreen extends GladeScreen {
	private static final int SNAP = 4;
	/** Mockup-space sizes. */
	private static final float BAR_H = 20, CARD_W = 196, CARD_H = 140, HANDLE = 8;
	private static final float DESIGN_H = 820;
	private static final Identifier EMBLEM = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "textures/gui/title/emblem.png");
	/** The small buttons under the main one: page, icon, label. */
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
	/** 0 closed, 1 open: the screen fades and the card rises in, and back out on close. */
	private float open;
	private boolean closing;
	private Screen next;
	private float barAnim;
	private long last = System.nanoTime();

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
	}

	private static boolean animations() {
		return TuffClient.modules().ui.animations.get();
	}

	/** GUI units per mockup pixel (the same scale as the Glade menu). */
	private float zoom() {
		return Ui.zoomFor(height, DESIGN_H, 640);
	}

	@Override
	protected void drawBackground(Gfx g, int mouseX, int mouseY, float partialTick) {
		int dim = minecraft.player == null ? 0xFF2A3A30 : Theme.alpha(0x40000000, Math.max(0, open));
		g.fill(0, 0, width, height, dim);
	}

	// ------------------------------------------------------------------ layout (mockup space)

	private float vw() {
		return width / zoom();
	}

	private float vh() {
		return height / zoom();
	}

	private float whole(float v) {
		float p = zoom() * Ui.guiScale();
		return Math.round(v * p) / p;
	}

	private float cardX() {
		return whole(vw() / 2 - CARD_W / 2);
	}

	private float cardY() {
		return whole(vh() / 2 - CARD_H / 2);
	}

	private float[] mainButton() {
		return new float[] {cardX() + 18, cardY() + 62, CARD_W - 36, 32};
	}

	private float[] quick(int i) {
		float size = 28, gap = 8, total = QUICK.length * size + (QUICK.length - 1) * gap;
		return new float[] {vw() / 2 - total / 2 + i * (size + gap), cardY() + 102, size, size};
	}

	/** A module's box in mockup space. */
	private float[] box(HudModule m) {
		int[] p = ModuleManager.position(m, width, height);
		float z = zoom();
		return new float[] {p[0] / z, p[1] / z, p[2] / z, p[3] / z};
	}

	private static String label(HudModule m) {
		return m.name + "  " + Math.round(m.scale.get() * 100) + "%";
	}

	/** The bar over (or under) a module: name and size, settings, remove. */
	private float[] bar(HudModule m) {
		float[] b = box(m);
		float w = Ui.width(label(m), Ui.Font.UI_12) + 16 + 2 * BAR_H;
		float x = Math.max(4, Math.min(vw() - w - 4, b[0] - 3));
		float y = b[1] > BAR_H + 8 ? b[1] - BAR_H - 6 : b[1] + b[3] + 6;
		return new float[] {x, y, w, BAR_H};
	}

	private float[] gear(HudModule m) {
		float[] b = bar(m);
		return new float[] {b[0] + b[2] - 2 * BAR_H, b[1], BAR_H, BAR_H};
	}

	private float[] remove(HudModule m) {
		float[] b = bar(m);
		return new float[] {b[0] + b[2] - BAR_H, b[1], BAR_H, BAR_H};
	}

	private float[] handle(HudModule m) {
		float[] b = box(m);
		return new float[] {b[0] + b[2] - HANDLE / 2 + 1, b[1] + b[3] - HANDLE / 2 + 1, HANDLE, HANDLE};
	}

	/** Mouse (GUI units) inside a mockup-space rectangle, with a little slack. */
	private boolean in(double mx, double my, float[] r) {
		float z = zoom();
		return Theme.inside(mx / z, my / z, r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1] + r[3] + 1);
	}

	private boolean overCard(double mx, double my) {
		return in(mx, my, new float[] {cardX(), cardY(), CARD_W, CARD_H});
	}

	// ------------------------------------------------------------------ drawing

	@Override
	protected void draw(Gfx g, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		boolean anim = animations();
		open = anim ? Theme.approach(open, closing ? 0 : 1, dt, closing ? 22 : 12) : closing ? 0 : 1;
		if (closing && open < 0.02f) {
			net.tuffclient.Mc.setScreen(next);
			return;
		}
		float a = Math.max(0, Math.min(1, open));
		float ease = 1 - (1 - a) * (1 - a) * (1 - a);

		CpsModule.sample(minecraft);
		updateFocus(mouseX, mouseY);
		barAnim = anim ? Theme.approach(barAnim, focus != null ? 1 : 0, dt, 18) : focus != null ? 1 : 0;
		for (HudModule m : TuffClient.modules().hud()) {
			if (m.isEnabled()) ModuleManager.renderModule(g, font, m, true);
		}
		if (guideX != null) g.fill(guideX, 0, guideX + 1, height, Theme.alpha(Theme.COPPER, a));
		if (guideY != null) g.fill(0, guideY, width, guideY + 1, Theme.alpha(Theme.COPPER, a));

		float z = zoom();
		Ui.zoom = z;
		g.push();
		g.scale(z, z);
		for (HudModule m : TuffClient.modules().hud()) {
			if (!m.isEnabled()) continue;
			float[] b = box(m);
			boolean active = m == focus;
			Ui.round(g, b[0] - 3, b[1] - 3, b[2] + 6, b[3] + 6, 5, Theme.alpha(active ? Theme.alpha(Theme.COPPER, 0.16f) : 0x12FFFFFF, a));
			Ui.outline(g, b[0] - 3, b[1] - 3, b[2] + 6, b[3] + 6, 5, Theme.alpha(active ? Theme.COPPER : 0x66FFFFFF, a));
		}
		if (focus != null) drawBar(g, focus, mouseX, mouseY, a * barAnim);
		drawCard(g, mouseX, mouseY, a, ease);

		String help = "Drag to move  ·  Drag the corner to resize  ·  Right-click for settings  ·  Right Shift to close";
		float hw = Ui.width(help, Ui.Font.UI_12) + 28, hh = 26;
		float hx = whole(vw() / 2 - hw / 2), hy = whole(vh() - hh - 14 + (1 - ease) * 10);
		Ui.round(g, hx, hy, hw, hh, 13, Theme.alpha(0xEBFFFFFF, a), Theme.alpha(Theme.GLASS_RIM, a), 1);
		Ui.centeredAt(g, help, vw() / 2, Ui.mid(hy + hh / 2, 12), Theme.alpha(Theme.TEXT, a), Ui.Font.UI_12);
		g.pop();
		Ui.zoom = 1f;
	}

	private void drawBar(Gfx g, HudModule m, int mouseX, int mouseY, float a) {
		if (a < 0.02f) return;
		float[] b = bar(m);
		Ui.round(g, b[0], b[1], b[2], b[3], 7, Theme.alpha(Theme.COPPER, a));
		Ui.textAt(g, label(m), b[0] + 9, Ui.mid(b[1] + BAR_H / 2, 12), Theme.alpha(Theme.INK, a), Ui.Font.UI_12);
		float[] gear = gear(m), x = remove(m);
		if (in(mouseX, mouseY, gear)) Ui.round(g, gear[0] + 2, gear[1] + 2, gear[2] - 4, gear[3] - 4, 5, Theme.alpha(0x33FFFFFF, a));
		if (in(mouseX, mouseY, x)) Ui.round(g, x[0] + 2, x[1] + 2, x[2] - 4, x[3] - 4, 5, Theme.alpha(Theme.DANGER, a));
		Ui.lucide(g, "settings", gear[0] + 4, gear[1] + 4, 12, Theme.alpha(Theme.INK, a));
		Ui.lucide(g, "close", x[0] + 4, x[1] + 4, 12, Theme.alpha(Theme.INK, a));
		float[] h = handle(m);
		Ui.round(g, h[0], h[1], h[2], h[3], 3, Theme.alpha(Theme.INK, a));
		Ui.round(g, h[0] + 1.5f, h[1] + 1.5f, h[2] - 3, h[3] - 3, 2, Theme.alpha(Theme.COPPER, a));
	}

	private void drawCard(Gfx g, int mouseX, int mouseY, float a, float ease) {
		float x = cardX(), y = cardY(), cx = vw() / 2;
		float s = a > 0.995f ? 1 : 0.94f + 0.06f * ease;
		g.push();
		g.translate(cx, y + CARD_H / 2 + (1 - ease) * 10);
		g.scale(s, s);
		g.translate(-cx, -(y + CARD_H / 2));
		Ui.frost(g, x, y, CARD_W, CARD_H, 20, 0xF0FFFFFF, 0.2f, a);
		Ui.image(g, EMBLEM, cx - 16, y + 14, 32, 32, 512, 512, Theme.alpha(0xFFFFFFFF, a));
		float[] main = mainButton();
		boolean overMain = in(mouseX, mouseY, main);
		Ui.accentButton(g, main[0], main[1], main[2], main[3], overMain ? 1 : 0, a);
		String text = "Mods & Settings";
		float tw = 14 + 8 + Ui.width(text, Ui.Font.UI_13_5_SB), tx = cx - tw / 2;
		Ui.lucide(g, "grid", tx, main[1] + main[3] / 2 - 7, 14, Theme.alpha(Theme.INK, a));
		Ui.textAt(g, text, tx + 22, Ui.mid(main[1] + main[3] / 2, 13.5f), Theme.alpha(Theme.INK, a), Ui.Font.UI_13_5_SB);
		String tip = null;
		float[] tipAt = null;
		for (int i = 0; i < QUICK.length; i++) {
			float[] q = quick(i);
			boolean over = in(mouseX, mouseY, q);
			Ui.glassButton(g, q[0], q[1], q[2], q[3], over ? 1 : 0, a);
			Ui.lucide(g, (String) QUICK[i][1], q[0] + 7, q[1] + 7, 14, Theme.alpha(over ? Theme.COPPER : Theme.TEXT, a));
			if (over) {
				tip = (String) QUICK[i][2];
				tipAt = q;
			}
		}
		g.pop();
		if (tip != null) {
			float w = Ui.width(tip, Ui.Font.UI_12_5_M) + 20, ty = tipAt[1] + tipAt[3] + 16;
			float tx2 = whole(tipAt[0] + tipAt[2] / 2 - w / 2);
			Ui.round(g, tx2, ty, w, 26, 8, Theme.alpha(0xF2FFFFFF, a), Theme.alpha(0xFF1F2A22, 0.08f * a), 1);
			Ui.textAt(g, tip, tx2 + 10, Ui.mid(ty + 13, 12.5f), Theme.alpha(Theme.TEXT, a), Ui.Font.UI_12_5_M);
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

	private void click() {
		minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private void openMenu(TuffMenuScreen.Page page, HudModule settingsOf) {
		click();
		TuffConfig.save(TuffClient.modules());
		TuffMenuScreen menu = new TuffMenuScreen(this, page);
		if (settingsOf != null && !settingsOf.settings().isEmpty()) menu.openSettings(settingsOf);
		net.tuffclient.Mc.setScreen(menu);
	}

	@Override
	protected boolean onClick(Input.Click event, boolean doubleClick) {
		if (closing) return true;
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
				click();
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

	/** Whether the open animation has finished (used by tests). */
	public boolean settled() {
		return open > 0.99f;
	}

	@Override
	public void onClose() {
		TuffConfig.save(TuffClient.modules());
		if (!animations()) {
			net.tuffclient.Mc.setScreen(parent);
			return;
		}
		closing = true;
		next = parent;
	}

	@Override
	public void removed() {
		// coming back from the menu plays the open animation again
		open = 0;
		closing = false;
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
