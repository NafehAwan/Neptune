package net.tuffclient.module.util;

import net.minecraft.client.Minecraft;
import net.tuffclient.gui.Theme;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;
import net.tuffclient.setting.NumberSetting;

/** Hold the zoom key (C by default) to zoom in smoothly. */
public final class ZoomModule extends Module {
	public final net.tuffclient.setting.KeybindSetting key = add(new net.tuffclient.setting.KeybindSetting("key", "Zoom key", net.tuffclient.TuffClient::zoomKey));
	public final NumberSetting factor = add(new NumberSetting("factor", "Zoom level", 4, 1.5, 12, 0.5));
	public final BoolSetting smooth = add(new BoolSetting("smooth", "Smooth zoom", true));
	public final ChoiceSetting curve = add(new ChoiceSetting("curve", "Zoom curve", 0, "Smooth", "Snappy", "Linear")).showIf(() -> smooth.get());
	public final NumberSetting speed = add(new NumberSetting("speed", "Zoom speed", 14, 4, 30, 1)).showIf(() -> smooth.get());
	public final BoolSetting scroll = add(new BoolSetting("scroll", "Scroll to zoom further", true));
	public final BoolSetting cinematic = add(new BoolSetting("cinematic", "Cinematic camera while zoomed", true));
	private boolean held;
	private float current = 1;
	/** Extra zoom from scrolling while held; resets when you let go. */
	private float scrolled = 1;
	private boolean restoreSmooth;
	private long last = System.nanoTime();

	public ZoomModule() {
		super("zoom", "Zoom", "Hold your zoom key to zoom in. Set the key and how far in its settings.", Category.UTILITY, true);
	}

	public void setHeld(boolean held) {
		Minecraft client = Minecraft.getInstance();
		if (held == this.held) return;
		this.held = held;
		scrolled = 1;
		if (held) {
			if (cinematic.get()) {
				restoreSmooth = client.options.smoothCamera;
				client.options.smoothCamera = true;
			}
		} else if (cinematic.get()) {
			client.options.smoothCamera = restoreSmooth;
		}
	}

	/** Scroll while zoomed: returns true when the scroll was used. */
	public boolean onScroll(double amount) {
		if (!isZooming() || !scroll.get() || amount == 0) return false;
		scrolled = (float) Math.max(0.4, Math.min(8, scrolled * (amount > 0 ? 1.15 : 1 / 1.15)));
		return true;
	}

	public boolean isZooming() {
		return isEnabled() && held;
	}

	/** Multiplier applied to the camera field of view. */
	public float fovMultiplier() {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		float target = isZooming() ? (float) (1 / (factor.get() * scrolled)) : 1;
		if (!smooth.get()) current = target;
		else {
			float k = speed.get().floatValue();
			switch (curve.get()) {
				case 1 -> current = Theme.approach(current, target, dt, k * 2.2f);
				case 2 -> {
					// constant speed in zoom levels, the same in and out
					float step = dt * k * 0.12f;
					float diff = target - current;
					current += Math.copySign(Math.min(Math.abs(diff), step * Math.max(current, 0.1f) * 4), diff);
				}
				default -> current = Theme.approach(current, target, dt, k);
			}
		}
		return current;
	}
}
