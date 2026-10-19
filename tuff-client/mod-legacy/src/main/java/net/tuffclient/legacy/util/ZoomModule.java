package net.tuffclient.legacy.util;

import net.tuffclient.legacy.module.Category;
import net.tuffclient.legacy.module.Module;
import net.tuffclient.legacy.setting.BoolSetting;
import net.tuffclient.legacy.setting.NumberSetting;

/** Hold the zoom key (C) to zoom in smoothly; scroll to zoom further. */
public final class ZoomModule extends Module {
	public final NumberSetting factor = add(new NumberSetting("factor", "Zoom", 4, 1.5, 12, 0.5));
	public final BoolSetting smooth = add(new BoolSetting("smooth", "Smooth zoom", true));
	public final BoolSetting scroll = add(new BoolSetting("scroll", "Scroll to zoom further", true));
	public final BoolSetting cinematic = add(new BoolSetting("cinematic", "Smooth camera while zoomed", false));
	private boolean held;
	private double extra = 1;
	private float current = 1;
	private long last = System.nanoTime();

	public ZoomModule() {
		super("zoom", "Zoom", "Hold C to zoom in. Scroll while zooming to zoom further.", Category.UTILITY, true);
	}

	public void setHeld(boolean held) {
		if (!held) extra = 1;
		this.held = held && isEnabled();
	}

	public boolean held() {
		return held;
	}

	/** Called on mouse wheel while zooming; returns true to swallow the scroll. */
	public boolean onScroll(int wheel) {
		if (!held || !scroll.get() || wheel == 0) return false;
		extra = Math.max(1, Math.min(8, extra * (wheel > 0 ? 1.25 : 0.8)));
		return true;
	}

	/** The FOV divisor right now (1 when not zooming). */
	public float divisor() {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		float target = held ? (float) (factor.get() * extra) : 1;
		current = smooth.get() ? current + (target - current) * (1 - (float) Math.exp(-14 * dt)) : target;
		return current;
	}
}
