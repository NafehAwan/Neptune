package net.tuffclient.module.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.tuffclient.gui.Gfx;
import net.tuffclient.module.hud.HudModule;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;

/**
 * Toggle Sprint and Toggle Sneak. Sprint can be always on or switched with the
 * sprint key; sneak is switched with the sneak key. Shows what's on, like
 * "[Sprinting (Toggled)]".
 */
public final class ToggleSprintModule extends HudModule {
	private final BoolSetting sprint = add(new BoolSetting("sprint", "Toggle sprint", true));
	private final ChoiceSetting sprintMode = add(new ChoiceSetting("sprint_mode", "Sprint", 0, "Always on", "Press sprint key")).showIf(() -> sprint.get());
	private final BoolSetting sneak = add(new BoolSetting("sneak", "Toggle sneak (press sneak key)", false));
	private final BoolSetting status = add(new BoolSetting("status", "Show status", true));
	private boolean sprintOn = true;
	private boolean sneakOn;
	private boolean forcedSneak;

	public ToggleSprintModule() {
		super("toggle_sprint", "Toggle Sprint", "Sprint without holding the key, and sneak on a toggle.", 0.006, 0.36, false);
	}

	@Override
	public void tick(Minecraft client) {
		var o = client.options;
		boolean inGame = client.player != null && net.tuffclient.Mc.screen() == null;
		if (sprint.get()) {
			if (sprintMode.get() == 1) {
				while (o.keySprint.consumeClick()) sprintOn = !sprintOn;
			} else {
				// old presses would flip it the moment the mode changes
				while (o.keySprint.consumeClick()) ;
				sprintOn = true;
			}
			if (inGame && sprintOn) o.keySprint.setDown(true);
		}
		if (sneak.get()) {
			boolean was = sneakOn;
			while (o.keyShift.consumeClick()) sneakOn = !sneakOn;
			if (client.player == null) sneakOn = false;
			// holding sneak while flying would pull you down, so the toggle waits until you land
			boolean flying = client.player != null && client.player.getAbilities().flying;
			if (inGame && sneakOn && !flying) {
				o.keyShift.setDown(true);
				forcedSneak = true;
			} else if (forcedSneak && (flying || !sneakOn || was && !sneakOn)) {
				// let go of only the sneak this module was holding
				o.keyShift.setDown(false);
				forcedSneak = false;
			}
		} else {
			while (o.keyShift.consumeClick()) ;
			if (sneakOn) {
				sneakOn = false;
				o.keyShift.setDown(false);
			}
		}
	}

	@Override
	protected void onEnable() {
		// presses from while it was off shouldn't count
		var o = Minecraft.getInstance().options;
		while (o.keySprint.consumeClick()) ;
		while (o.keyShift.consumeClick()) ;
	}

	@Override
	protected void onDisable() {
		var o = Minecraft.getInstance().options;
		o.keySprint.setDown(false);
		if (sneakOn) o.keyShift.setDown(false);
		sneakOn = false;
	}

	/** The status line, or null when there's nothing to show. */
	private String line(Minecraft mc, boolean preview) {
		if (preview && mc.player == null) return "[Sprinting (Toggled)]";
		if (mc.player == null) return null;
		if (mc.player.getAbilities().flying) return "[Flying]";
		if (sneak.get() && sneakOn) return "[Sneaking (Toggled)]";
		if (mc.player.isShiftKeyDown()) return "[Sneaking (Key Held)]";
		if (sprint.get() && sprintOn) return "[Sprinting (Toggled)]";
		if (mc.player.isSprinting()) return "[Sprinting (Key Held)]";
		return preview ? "[Sprinting (Toggled)]" : null;
	}

	@Override
	public void render(Gfx g, Font font, boolean preview) {
		String s = status.get() || preview ? line(Minecraft.getInstance(), preview) : null;
		if (s == null) {
			width = 0;
			height = 0;
			return;
		}
		int p = pad();
		box(g, width(font, s) + p * 2, linesHeight(1));
		value(g, font, s, p, p);
	}
}
