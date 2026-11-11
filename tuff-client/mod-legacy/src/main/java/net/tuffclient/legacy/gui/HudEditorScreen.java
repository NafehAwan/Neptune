package net.tuffclient.legacy.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.tuffclient.legacy.Config;
import net.tuffclient.legacy.GladeLegacy;
import net.tuffclient.legacy.hud.HudModule;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Drag HUD elements anywhere; scroll over one to resize it, right-click to reset it. */
public final class HudEditorScreen extends Screen {
	private final Screen parent;
	private HudModule dragging;
	private float grabX, grabY;

	public HudEditorScreen(Screen parent) {
		this.parent = parent;
	}

	private HudModule at(Window window, int mx, int my) {
		for (HudModule m : GladeLegacy.modules().hud()) {
			if (!m.isEnabled()) continue;
			float[] b = m.bounds(window);
			if (Draw.inside(mx, my, b[0], b[1], b[2], b[3])) return m;
		}
		return null;
	}

	@Override
	public void render(int mouseX, int mouseY, float tickDelta) {
		Window window = new Window(client);
		Draw.rect(0, 0, width, height, client.world == null ? 0xFF2A3A30 : 0x40000000);
		Draw.rect(width / 2f, 0, 1, height, 0x18FFFFFF);
		Draw.rect(0, height / 2f, width, 1, 0x18FFFFFF);
		if (dragging != null) {
			dragging.x = (mouseX - grabX) / (double) width;
			dragging.y = (mouseY - grabY) / (double) height;
		}
		HudModule hovered = dragging != null ? dragging : at(window, mouseX, mouseY);
		for (HudModule m : GladeLegacy.modules().hud()) {
			if (!m.isEnabled()) continue;
			m.render(window, true);
			float[] b = m.bounds(window);
			Draw.outline(b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, 2, m == hovered ? Draw.ACCENT_HI : 0x60FFFFFF);
		}
		if (hovered != null && dragging == null) {
			float[] b = hovered.bounds(window);
			String label = hovered.name + "  " + Math.round(hovered.scale.get() * 100) + "%";
			Draw.round(b[0], b[1] - 14, Draw.width(label) + 8, 12, 4, 0xE6F6F4EE);
			Draw.text(label, b[0] + 4, b[1] - 12, Draw.INK, 1f);
		}
		String hint = "Drag to move  -  Scroll to resize  -  Right-click to reset  -  Esc to go back";
		Draw.centered(hint, width / 2f, height - 14, 0xB3FFFFFF, 1f);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		Window window = new Window(client);
		HudModule m = at(window, mouseX, mouseY);
		if (m == null) return;
		if (button == 1) {
			m.resetPosition();
			m.scale.reset();
			return;
		}
		float[] b = m.bounds(window);
		dragging = m;
		grabX = mouseX - b[0];
		grabY = mouseY - b[1];
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		dragging = null;
	}

	@Override
	public void handleMouse() {
		super.handleMouse();
		int wheel = Mouse.getEventDWheel();
		if (wheel == 0) return;
		int mx = Mouse.getEventX() * width / client.width, my = height - Mouse.getEventY() * height / client.height - 1;
		HudModule m = at(new Window(client), mx, my);
		if (m != null) m.scale.set(m.scale.get() + Math.signum(wheel) * 0.05);
	}

	@Override
	protected void keyPressed(char c, int key) {
		if (key == Keyboard.KEY_ESCAPE) client.setScreen(parent);
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
