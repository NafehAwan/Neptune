package net.tuffclient.module.util;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.tuffclient.TuffClient;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;
import net.tuffclient.setting.KeybindSetting;

/** Hold a key to see yourself from the front (or behind); let go to snap back. */
public final class SnaplookModule extends Module {
	public final KeybindSetting key = add(new KeybindSetting("key", "Snaplook key", () -> TuffClient.key("snaplook")));
	private final ChoiceSetting view = add(new ChoiceSetting("view", "View", 0, "Front", "Behind"));
	private final BoolSetting toggle = add(new BoolSetting("toggle", "Press to toggle instead of hold", false));
	private boolean active;
	private boolean wasDown;
	private CameraType previous;

	public SnaplookModule() {
		super("snaplook", "Snaplook", "Hold a key to look at yourself from the front, then snap back.", Category.UTILITY, false);
	}

	@Override
	public void tick(Minecraft mc) {
		var k = TuffClient.key("snaplook");
		boolean down = k != null && k.isDown() && net.tuffclient.Mc.screen() == null;
		boolean want = toggle.get() ? (down && !wasDown ? !active : active) : down;
		wasDown = down;
		if (mc.player == null) want = false;
		if (want == active) return;
		active = want;
		if (active) {
			previous = mc.options.getCameraType();
			mc.options.setCameraType(view.get() == 0 ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
		} else restore(mc);
	}

	private void restore(Minecraft mc) {
		if (previous != null) mc.options.setCameraType(previous);
		previous = null;
		active = false;
	}

	@Override
	protected void onDisable() {
		if (active) restore(Minecraft.getInstance());
	}
}
