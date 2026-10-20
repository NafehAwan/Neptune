package net.tuffclient.module.hud;

import net.tuffclient.gui.Gfx;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.tuffclient.gui.Theme;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;

public final class KeystrokesModule extends HudModule {
	@Override
	protected boolean boxedByDefault() {
		return true;
	}

	private static final int KEY = 20;
	private static final int GAP = 2;
	private final BoolSetting showMouse = add(new BoolSetting("mouse", "Mouse buttons", true));
	private final BoolSetting showSpace = add(new BoolSetting("space", "Space bar", true));
	private final ColorSetting keyColor = add(new ColorSetting("key_color", "Key color", 0x9A101210, true));
	private final ColorSetting keyText = add(new ColorSetting("key_text", "Key text color", Theme.HUD_TEXT, true));
	private final ColorSetting pressColor = add(new ColorSetting("press_color", "Pressed key color", 0xEB2F6B4F, true));
	private final ColorSetting pressedText = add(new ColorSetting("pressed_text", "Pressed text color", Theme.INK, true));
	private final float[] glow = new float[7];
	private long last = System.nanoTime();

	public KeystrokesModule() {
		super("keystrokes", "Keystrokes", "Shows movement keys and mouse buttons as you press them.", 0.006, 0.72, false);
	}

	@Override
	public void render(Gfx g, Font font, boolean preview) {
		Minecraft client = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		int row = KEY + GAP;
		int w = KEY * 3 + GAP * 2;
		key(g, font, 0, client.options.keyUp, "W", KEY + GAP, 0, KEY, KEY, dt);
		key(g, font, 1, client.options.keyLeft, "A", 0, row, KEY, KEY, dt);
		key(g, font, 2, client.options.keyDown, "S", row, row, KEY, KEY, dt);
		key(g, font, 3, client.options.keyRight, "D", row * 2, row, KEY, KEY, dt);
		int y = row * 2;
		if (showMouse.get()) {
			int half = (w - GAP) / 2;
			key(g, font, 4, client.options.keyAttack, CpsModule.left() + " CPS", 0, y, half, KEY - 4, dt);
			key(g, font, 5, client.options.keyUse, CpsModule.right() + " CPS", half + GAP, y, w - half - GAP, KEY - 4, dt);
			y += KEY - 4 + GAP;
		}
		if (showSpace.get()) {
			key(g, font, 6, client.options.keyJump, "", 0, y, w, 10, dt);
			g.fill(w / 2 - 10, y + 4, w / 2 + 10, y + 5, glow[6] > 0.5f ? pressedText.argb() : keyText.argb(wave(w / 2)));
			y += 10 + GAP;
		}
		width = w;
		height = y - GAP;
	}

	@Override
	protected boolean usesTextColors() {
		return false;
	}

	private float wave(float x) {
		return (float) (this.x * 2 + x * 0.006);
	}

	private void key(Gfx g, Font font, int i, KeyMapping mapping, String label, int x, int y, int w, int h, float dt) {
		glow[i] = Theme.approach(glow[i], mapping.isDown() ? 1 : 0, dt, 18);
		// in chroma wave mode each key sits a step further along the rainbow
		float shift = wave(x + w / 2f);
		int fill = Theme.mix(background.get() ? keyColor.argb(shift) : 0x00000000, pressColor.argb(shift), glow[i]);
		float r = cornerRadius();
		if (r > 0) net.tuffclient.gui.Ui.round(g, x, y, w, h, Math.min(r, h / 2f), fill);
		else g.fill(x, y, x + w, y + h, fill);
		if (border.get()) net.tuffclient.gui.Ui.outline(g, x, y, w, h, Math.max(0.5f, Math.min(r, h / 2f)), borderColor.argb());
		if (!label.isEmpty()) {
			int color = Theme.mix(keyText.argb(shift), pressedText.argb(shift), glow[i]);
			text(g, font, label, x + (w - width(font, label)) / 2, y + (h - 8) / 2, color);
		}
	}
}
