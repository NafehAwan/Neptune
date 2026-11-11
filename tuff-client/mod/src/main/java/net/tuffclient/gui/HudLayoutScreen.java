package net.tuffclient.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.tuffclient.ModuleManager;
import net.tuffclient.TuffClient;
import net.tuffclient.TuffConfig;
import net.tuffclient.module.hud.HudModule;

/**
 * Moves HUD elements over the game: drag one to move it (it snaps to the
 * screen's edges and middle), scroll over it to resize, right-click it for its
 * options. The game stays visible behind, without blur.
 */
public final class HudLayoutScreen extends VanillaScreen {
	private static final int SNAP = 4;
	private HudModule dragging;
	private double grabX;
	private double grabY;
	private Integer guideX;
	private Integer guideY;

	public HudLayoutScreen(Screen parent) {
		super(Component.literal("Edit HUD Layout"), parent);
	}

	/** True while this screen is open: the HUD is drawn here instead of by the game. */
	public static boolean open() {
		return net.tuffclient.Mc.screen() instanceof HudLayoutScreen;
	}

	@Override
	protected void drawBackground(Gfx g, int mouseX, int mouseY, float partialTick) {
		// the world shows through as it is
	}

	@Override
	protected void layout(Gfx g) {
		int sw = g.guiWidth(), sh = g.guiHeight();
		if (guideX != null) g.fill(guideX, 0, guideX + 1, sh, 0x80FFFF55);
		if (guideY != null) g.fill(0, guideY, sw, guideY + 1, 0x80FFFF55);
		HudModule over = null;
		for (HudModule m : TuffClient.modules().hud()) {
			if (!m.isEnabled()) continue;
			ModuleManager.renderModule(g, font, m, true);
			int[] p = ModuleManager.position(m, sw, sh);
			boolean hot = m == dragging || dragging == null && hovered(p[0], p[1], p[2], p[3]);
			g.fill(p[0], p[1], p[0] + p[2], p[1] + p[3], hot ? 0x40FFFFFF : 0x20FFFFFF);
			g.outline(p[0] - 1, p[1] - 1, p[2] + 2, p[3] + 2, hot ? WHITE : 0x80FFFFFF);
			if (hot) over = m;
			onClickAt(p[0], p[1], p[2], p[3], false, c -> {
				if (c.button() == 1) {
					playClick();
					minecraft.setScreen(new ModuleScreen(this, m));
					return;
				}
				dragging = m;
				grabX = c.x() - p[0];
				grabY = c.y() - p[1];
				startDrag((mx, my) -> move(m, mx, my, sw, sh));
			});
		}
		if (dragging == null) {
			g.centeredText(font, title, sw / 2, 8, WHITE);
			g.centeredText(font, "Drag to move · Scroll to resize · Right-click for options", sw / 2, 20, GREY);
			int by = sh - 28;
			button(g, sw / 2 - 154, by, 150, Component.literal("Glade Settings..."), false, () -> minecraft.setScreen(new GladeMenuScreen(this)));
			button(g, sw / 2 + 4, by, 150, Component.translatable("gui.done"), false, this::onClose);
			if (over != null) g.centeredText(font, over.name + "  (" + Math.round(over.scale.get() * 100) + "%)", sw / 2, by - 14, WHITE);
		}
	}

	private void move(HudModule m, double mx, double my, int sw, int sh) {
		int[] p = ModuleManager.position(m, sw, sh);
		double x = mx - grabX, y = my - grabY;
		guideX = null;
		guideY = null;
		// snap the edges and the middle to the screen's edges and middle
		if (Math.abs(x) < SNAP) x = 0;
		if (Math.abs(x + p[2] - sw) < SNAP) x = sw - p[2];
		if (Math.abs(x + p[2] / 2.0 - sw / 2.0) < SNAP) {
			x = sw / 2.0 - p[2] / 2.0;
			guideX = sw / 2;
		}
		if (Math.abs(y) < SNAP) y = 0;
		if (Math.abs(y + p[3] - sh) < SNAP) y = sh - p[3];
		if (Math.abs(y + p[3] / 2.0 - sh / 2.0) < SNAP) {
			y = sh / 2.0 - p[3] / 2.0;
			guideY = sh / 2;
		}
		m.x = Math.max(0, Math.min(1, x / sw));
		m.y = Math.max(0, Math.min(1, y / sh));
	}

	@Override
	protected boolean onRelease(Input.Click click) {
		dragging = null;
		guideX = null;
		guideY = null;
		return super.onRelease(click);
	}

	@Override
	protected boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
		for (HudModule m : TuffClient.modules().hud()) {
			if (!m.isEnabled()) continue;
			int[] p = ModuleManager.position(m, width, height);
			if (mouseX >= p[0] && mouseX < p[0] + p[2] && mouseY >= p[1] && mouseY < p[1] + p[3]) {
				m.scale.set(m.scale.get() + (scrollY > 0 ? 0.1 : -0.1));
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		TuffConfig.save(TuffClient.modules());
		super.onClose();
	}
}
