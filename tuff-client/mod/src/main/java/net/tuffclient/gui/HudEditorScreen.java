package net.tuffclient.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.tuffclient.ModuleManager;
import net.tuffclient.TuffClient;
import net.tuffclient.TuffConfig;
import net.tuffclient.module.Category;
import net.tuffclient.module.hud.CpsModule;
import net.tuffclient.module.hud.HudModule;

/** Drag HUD modules around, with snapping to screen edges, the center and each other. */
public final class HudEditorScreen extends Screen {
	private static final int SNAP = 4;
	private final Screen parent;
	private HudModule dragging;
	private double grabX;
	private double grabY;
	private Integer guideX;
	private Integer guideY;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, minecraft.player == null ? Theme.BG : 0x50000000);
		g.fill(width / 2, 0, width / 2 + 1, height, 0x18FFFFFF);
		g.fill(0, height / 2, width, height / 2 + 1, 0x18FFFFFF);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		CpsModule.sample(minecraft);
		HudModule hovered = dragging != null ? dragging : at(mouseX, mouseY);
		for (HudModule m : TuffClient.modules().hud()) {
			if (!m.isEnabled()) continue;
			ModuleManager.renderModule(g, font, m, true);
			int[] p = ModuleManager.position(m, width, height);
			int color = m == dragging ? Theme.COPPER : m == hovered ? Theme.COPPER_HI : 0x60FFFFFF;
			g.outline(p[0] - 1, p[1] - 1, p[2] + 2, p[3] + 2, color);
			if (m == hovered && dragging == null) {
				String label = m.name + "  " + Math.round(m.scale.get() * 100) + "%";
				int ly = p[1] > 12 ? p[1] - 11 : p[1] + p[3] + 3;
				g.text(font, label, p[0], ly, Theme.COPPER_HI);
			}
		}
		if (guideX != null) g.fill(guideX, 0, guideX + 1, height, Theme.COPPER);
		if (guideY != null) g.fill(0, guideY, width, guideY + 1, Theme.COPPER);

		String help = "Drag to move · Scroll to resize · Right-click to reset size · Esc when done";
		int hw = font.width(help) + 12;
		Theme.panel(g, width / 2 - hw / 2, 6, width / 2 + hw / 2, 22, 0xE0141614, Theme.BORDER_STRONG);
		Theme.centered(g, font, help, width / 2, 10, Theme.MUTED);

		int bw = 90, bx = width / 2 - bw / 2, by = height - 28;
		Theme.pixelButton(g, bx, by, bx + bw, by + 18, Theme.inside(mouseX, mouseY, bx, by, bx + bw, by + 18) ? 1 : 0);
		Theme.centered(g, font, "HUD MODULES", width / 2, by + 5, Theme.COPPER_INK);
	}

	private HudModule at(double mx, double my) {
		var list = TuffClient.modules().hud();
		for (int i = list.size() - 1; i >= 0; i--) {
			HudModule m = list.get(i);
			if (!m.isEnabled()) continue;
			int[] p = ModuleManager.position(m, width, height);
			if (Theme.inside(mx, my, p[0], p[1], p[0] + p[2], p[1] + p[3])) return m;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int bw = 90, bx = width / 2 - bw / 2, by = height - 28;
		if (Theme.inside(event.x(), event.y(), bx, by, bx + bw, by + 18)) {
			minecraft.gui.setScreen(new TuffMenuScreen(this, Category.HUD));
			return true;
		}
		HudModule m = at(event.x(), event.y());
		if (m != null) {
			if (event.button() == 1) {
				m.scale.set(1.0);
			} else {
				int[] p = ModuleManager.position(m, width, height);
				dragging = m;
				grabX = event.x() - p[0];
				grabY = event.y() - p[1];
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) return super.mouseDragged(event, dx, dy);
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
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		guideX = null;
		guideY = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		HudModule m = at(mouseX, mouseY);
		if (m != null) {
			m.scale.set(m.scale.get() + (scrollY > 0 ? 0.1 : -0.1));
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		TuffConfig.save(TuffClient.modules());
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
