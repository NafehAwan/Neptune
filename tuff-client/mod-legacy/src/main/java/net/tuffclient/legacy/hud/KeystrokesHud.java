package net.tuffclient.legacy.hud;

import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.tuffclient.legacy.gui.Draw;
import net.tuffclient.legacy.setting.BoolSetting;
import net.tuffclient.legacy.setting.ColorSetting;

/** W, A, S, D, mouse buttons (with CPS) and the space bar; keys light up while held. */
public final class KeystrokesHud extends HudModule {
	public final ColorSetting keyColor = add(new ColorSetting("key_color", "Key color", 0x66000000));
	public final ColorSetting pressColor = add(new ColorSetting("press_color", "Pressed color", 0xEB2F6B4F));
	public final ColorSetting pressedText = add(new ColorSetting("pressed_text", "Pressed text", 0xFFFFFFFF));
	public final BoolSetting mouse = add(new BoolSetting("mouse", "Mouse buttons", true));
	public final BoolSetting space = add(new BoolSetting("space", "Space bar", true));
	private final float[] glow = new float[7];
	private long last = System.nanoTime();

	public KeystrokesHud() {
		super("keystrokes", "Keystrokes", "Shows the movement keys and mouse buttons as you press them.", 0.005, 0.7, true);
		background.set(false);
	}

	@Override
	public int width() {
		return 74;
	}

	@Override
	public int height() {
		return 48 + (mouse.get() ? 24 : 0) + (space.get() ? 14 : 0);
	}

	@Override
	protected void draw(boolean preview) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		GameOptions o = mc().options;
		key(0, o.forwardKey, "W", 25, 0, 24, 22, dt);
		key(1, o.leftKey, "A", 0, 25, 24, 22, dt);
		key(2, o.backKey, "S", 25, 25, 24, 22, dt);
		key(3, o.rightKey, "D", 50, 25, 24, 22, dt);
		int y = 50;
		if (mouse.get()) {
			key(4, o.attackKey, "LMB " + Cps.left(), 0, y, 36, 22, dt);
			key(5, o.useKey, "RMB " + Cps.right(), 38, y, 36, 22, dt);
			y += 24;
		}
		if (space.get()) key(6, o.jumpKey, "——", 0, y, 74, 12, dt);
	}

	private void key(int i, KeyBinding binding, String text, int x, int y, int w, int h, float dt) {
		glow[i] = Draw.approach(glow[i], binding.isPressed() ? 1 : 0, dt, 18);
		Draw.round(x, y, w, h, 3, Draw.mix(keyColor.argb(i * 0.07f), pressColor.argb(), glow[i]));
		int color = Draw.mix(this.text.argb(i * 0.07f), pressedText.argb(), glow[i]);
		mc().textRenderer.draw(text, x + (w - Draw.width(text)) / 2f, y + (h - 8) / 2f, color, shadow.get());
	}
}
