package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;
import net.tuffclient.setting.NumberSetting;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Tint the sky and fog. Strength blends between the real colour and yours. */
public final class CustomSkyModule extends Module {
	private final BoolSetting sky = add(new BoolSetting("sky", "Tint the sky", true));
	private final ColorSetting skyColor = add(new ColorSetting("sky_color", "Sky color", 0xFF7FB8E8, false)).showIf(() -> sky.get());
	private final BoolSetting fog = add(new BoolSetting("fog", "Tint the fog", true));
	private final ColorSetting fogColor = add(new ColorSetting("fog_color", "Fog color", 0xFFBFD9C8, false)).showIf(() -> fog.get());
	private final NumberSetting strength = add(new NumberSetting("strength", "Strength (%)", 60, 0, 100, 5));

	public CustomSkyModule() {
		super("custom_sky", "Custom Sky", "Tint the sky and fog to any colour.", Category.VISUAL, false);
		requires("SkyRendererMixin", "FogRendererMixin");
	}

	private float t() {
		return strength.get().floatValue() / 100f;
	}

	/** Blends a sky colour (0..1 RGB) toward the chosen one. */
	public Vector3f sky(org.joml.Vector3fc original) {
		int c = skyColor.argb();
		float t = t();
		return new Vector3f(
				lerp(original.x(), (c >> 16 & 0xFF) / 255f, t),
				lerp(original.y(), (c >> 8 & 0xFF) / 255f, t),
				lerp(original.z(), (c & 0xFF) / 255f, t));
	}

	public void fog(Vector4f color) {
		int c = fogColor.argb();
		float t = t();
		color.set(lerp(color.x, (c >> 16 & 0xFF) / 255f, t), lerp(color.y, (c >> 8 & 0xFF) / 255f, t), lerp(color.z, (c & 0xFF) / 255f, t), color.w);
	}

	public boolean tintSky() {
		return isEnabled() && sky.get();
	}

	public boolean tintFog() {
		return isEnabled() && fog.get();
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
