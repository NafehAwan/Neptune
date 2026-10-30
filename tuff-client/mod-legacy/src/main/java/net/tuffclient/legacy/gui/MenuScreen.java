package net.tuffclient.legacy.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gui.screen.Screen;
import net.tuffclient.legacy.Config;
import net.tuffclient.legacy.GladeLegacy;
import net.tuffclient.legacy.module.Category;
import net.tuffclient.legacy.module.Module;
import net.tuffclient.legacy.setting.BoolSetting;
import net.tuffclient.legacy.setting.ChoiceSetting;
import net.tuffclient.legacy.setting.ColorSetting;
import net.tuffclient.legacy.setting.NumberSetting;
import net.tuffclient.legacy.setting.Setting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Glade's menu for 1.8.9: module cards with switches, and each module's settings. */
public final class MenuScreen extends Screen {
	private static final int PW = 420, PH = 270;
	private Category tab = null;
	private Module open;
	private float anim, scroll, scrollTarget;
	private long last = System.nanoTime();
	private NumberSetting dragging;
	private float dragX, dragW;
	private final List<float[]> hits = new ArrayList<float[]>();
	private final List<Runnable> actions = new ArrayList<Runnable>();
	private final float[] switches = new float[64];

	private float px() {
		return (width - PW) / 2f;
	}

	private float py() {
		return (height - PH) / 2f;
	}

	private void hit(float x, float y, float w, float h, Runnable action) {
		hits.add(new float[] {x, y, w, h});
		actions.add(action);
	}

	@Override
	public void render(int mouseX, int mouseY, float tickDelta) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		anim = Draw.approach(anim, 1, dt, 12);
		scroll = Draw.approach(scroll, scrollTarget, dt, 18);
		hits.clear();
		actions.clear();
		if (client.world == null) renderBackground();
		else Draw.rect(0, 0, width, height, Draw.alphaOf(0x50000000, anim));
		if (dragging != null) dragging.set(dragging.min + Math.max(0, Math.min(1, (mouseX - dragX) / dragW)) * (dragging.max - dragging.min));

		float x = px(), y = py() + (1 - anim) * 10;
		GlStateManager.pushMatrix();
		Draw.glass(x, y, PW, PH, 12, anim);
		Draw.text("Glade", x + 16, y + 14, Draw.alphaOf(Draw.INK, anim), 2f);
		Draw.text(open == null ? "Mods" : open.name, x + 82, y + 21, Draw.alphaOf(Draw.MUTED, anim), 1f);
		header(x, y, mouseX, mouseY);
		Draw.rect(x + 14, y + 42, PW - 28, 1, Draw.alphaOf(Draw.LINE, anim));
		if (open == null) cards(x + 14, y + 52, PW - 28, PH - 62, mouseX, mouseY, dt);
		else settings(x + 14, y + 52, PW - 28, PH - 62, mouseX, mouseY, dt);
		GlStateManager.popMatrix();
	}

	private void header(float x, float y, int mx, int my) {
		if (open != null) {
			pill(x + PW - 76, y + 12, 62, "< Back", false, mx, my, () -> {
				open = null;
				scrollTarget = 0;
			});
			return;
		}
		float bx = x + PW - 16;
		bx -= 62;
		pill(bx, y + 12, 62, "Edit HUD", true, mx, my, () -> client.setScreen(new HudEditorScreen(this)));
		String[] names = {"All", "HUD", "Utility", "Visual"};
		Category[] cats = {null, Category.HUD, Category.UTILITY, Category.VISUAL};
		for (int i = cats.length - 1; i >= 0; i--) {
			float w = Draw.width(names[i]) + 14;
			bx -= w + 4;
			final Category c = cats[i];
			pill(bx, y + 12, w, names[i], tab == c, mx, my, () -> {
				tab = c;
				scrollTarget = 0;
			});
		}
	}

	private void pill(float x, float y, float w, String label, boolean on, int mx, int my, Runnable action) {
		boolean hover = Draw.inside(mx, my, x, y, w, 18);
		Draw.round(x, y, w, 18, 9, Draw.alphaOf(on ? Draw.ACCENT : hover ? 0xFFFFFFFF : 0x99FFFFFF, anim));
		Draw.centered(label, x + w / 2, y + 5, Draw.alphaOf(on ? 0xFFFFFFFF : Draw.INK, anim), 1f);
		hit(x, y, w, 18, action);
	}

	private void cards(float x, float y, float w, float h, int mx, int my, float dt) {
		List<Module> list = new ArrayList<Module>();
		for (Module m : GladeLegacy.modules().all()) if (tab == null || m.category == tab) list.add(m);
		float cw = (w - 8) / 3f, ch = 46;
		int rows = (list.size() + 2) / 3;
		float max = Math.max(0, rows * (ch + 4) - h);
		scrollTarget = Math.max(0, Math.min(max, scrollTarget));
		Scissor.push(x, y, w, h);
		for (int i = 0; i < list.size(); i++) {
			final Module m = list.get(i);
			float cx = x + (i % 3) * (cw + 4), cy = y + (i / 3) * (ch + 4) - scroll;
			if (cy + ch < y || cy > y + h) continue;
			boolean hover = Draw.inside(mx, my, cx, cy, cw, ch) && my >= y && my < y + h;
			Draw.round(cx, cy, cw, ch, 8, Draw.alphaOf(hover ? 0xEEFFFFFF : Draw.CARD, anim));
			Draw.text(m.name, cx + 9, cy + 9, Draw.alphaOf(Draw.INK, anim), 1f);
			String d = client.textRenderer.trimToWidth(m.description, (int) ((cw - 18) / 0.75f));
			Draw.text(d, cx + 9, cy + 22, Draw.alphaOf(Draw.FAINT, anim), 0.75f);
			int idx = Math.min(switches.length - 1, GladeLegacy.modules().all().indexOf(m));
			switches[idx] = Draw.approach(switches[idx], m.isEnabled() ? 1 : 0, dt, 16);
			toggle(cx + cw - 30, cy + 8, switches[idx]);
			if (!m.settings().isEmpty()) Draw.text("Settings >", cx + 9, cy + 33, Draw.alphaOf(Draw.ACCENT, anim), 0.75f);
			if (my >= y && my < y + h) {
				// later hit areas win: the whole card toggles, the switch too, "Settings >" opens them
				hit(cx, cy, cw, ch, m::toggle);
				if (!m.settings().isEmpty()) hit(cx, cy + 28, cw, ch - 28, () -> {
					open = m;
					scrollTarget = 0;
				});
				hit(cx + cw - 34, cy + 4, 30, 16, m::toggle);
			}
		}
		Scissor.pop();
	}

	private void toggle(float x, float y, float t) {
		Draw.round(x, y, 22, 11, 5.5f, Draw.alphaOf(Draw.mix(0x401F2A22, Draw.ACCENT, t), anim));
		Draw.circle(x + 5.5f + 11 * t, y + 5.5f, 4, Draw.alphaOf(0xFFFFFFFF, anim));
	}

	private void settings(float x, float y, float w, float h, int mx, int my, float dt) {
		Module m = open;
		float row = 22, total = 30;
		for (Setting<?> s : m.settings()) total += s instanceof ColorSetting ? 42 : row;
		float max = Math.max(0, total - h);
		scrollTarget = Math.max(0, Math.min(max, scrollTarget));
		Scissor.push(x, y, w, h);
		float cy = y - scroll;
		Draw.text(m.description, x + 2, cy + 2, Draw.alphaOf(Draw.MUTED, anim), 1f);
		toggle(x + w - 26, cy, m.isEnabled() ? 1 : 0);
		hit(x + w - 30, cy - 3, 30, 16, m::toggle);
		cy += 22;
		for (Setting<?> s : m.settings()) {
			boolean visible = cy + 40 > y && cy < y + h;
			Draw.text(s.label, x + 2, cy + 6, Draw.alphaOf(Draw.INK, anim), 1f);
			if (s instanceof BoolSetting) {
				final BoolSetting b = (BoolSetting) s;
				toggle(x + w - 26, cy + 4, b.get() ? 1 : 0);
				if (visible) hit(x + w - 30, cy, 30, 18, b::toggle);
				cy += row;
			} else if (s instanceof NumberSetting) {
				final NumberSetting n = (NumberSetting) s;
				float sx = x + w - 150, sw = 110;
				Draw.round(sx, cy + 8, sw, 4, 2, Draw.alphaOf(0x331F2A22, anim));
				Draw.round(sx, cy + 8, (float) (sw * n.fraction()), 4, 2, Draw.alphaOf(Draw.ACCENT, anim));
				Draw.circle(sx + (float) (sw * n.fraction()), cy + 10, 5, Draw.alphaOf(0xFFFFFFFF, anim));
				String v = n.step >= 1 ? String.valueOf(Math.round(n.get())) : String.format("%.2f", n.get());
				Draw.text(v, sx + sw + 8, cy + 6, Draw.alphaOf(Draw.MUTED, anim), 1f);
				if (visible) {
					final float fx = sx;
					hit(sx - 4, cy + 2, sw + 8, 16, () -> {
						dragging = n;
						dragX = fx;
						dragW = 110;
					});
				}
				cy += row;
			} else if (s instanceof ChoiceSetting) {
				final ChoiceSetting c = (ChoiceSetting) s;
				float bw = Draw.width(c.current()) + 16;
				pill(x + w - bw - 4, cy + 1, bw, c.current(), true, mx, my, c::next);
				cy += row;
			} else if (s instanceof ColorSetting) {
				final ColorSetting c = (ColorSetting) s;
				Draw.round(x + w - 22, cy + 3, 18, 12, 4, c.argb());
				float sx = x + 2;
				for (final int sw : ColorSetting.SWATCHES) {
					Draw.round(sx, cy + 20, 14, 14, 4, (sw | 0xFF000000));
					if ((c.get() & 0xFFFFFF) == (sw & 0xFFFFFF) && !c.chroma) Draw.outline(sx - 1, cy + 19, 16, 16, 5, Draw.ACCENT);
					if (visible) hit(sx, cy + 20, 14, 14, () -> {
						c.set((c.get() & 0xFF000000) | (sw & 0xFFFFFF));
						c.chroma = false;
					});
					sx += 17;
				}
				pill(sx + 4, cy + 18, 54, "Chroma", c.chroma, mx, my, () -> c.chroma = !c.chroma);
				pill(sx + 62, cy + 18, 54, alphaLabel(c), false, mx, my, () -> {
					int a = c.get() >>> 24;
					a = a >= 255 ? 64 : a >= 192 ? 255 : a >= 128 ? 192 : 128;
					c.set((a << 24) | (c.get() & 0xFFFFFF));
				});
				cy += 42;
			}
		}
		Scissor.pop();
	}

	private static String alphaLabel(ColorSetting c) {
		return Math.round((c.get() >>> 24) / 2.55f) + "%";
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			float[] h = hits.get(i);
			if (Draw.inside(mouseX, mouseY, h[0], h[1], h[2], h[3])) {
				actions.get(i).run();
				return;
			}
		}
		if (!Draw.inside(mouseX, mouseY, px(), py(), PW, PH)) client.setScreen(null);
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		dragging = null;
	}

	@Override
	public void handleMouse() {
		super.handleMouse();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) scrollTarget -= Math.signum(wheel) * 30;
	}

	@Override
	protected void keyPressed(char c, int key) {
		if (key == Keyboard.KEY_ESCAPE) {
			if (open != null) {
				open = null;
				scrollTarget = 0;
			} else {
				client.setScreen(null);
			}
		}
	}

	@Override
	public void removed() {
		Config.save(GladeLegacy.modules());
	}

	@Override
	public boolean shouldPauseGame() {
		return false;
	}
}
