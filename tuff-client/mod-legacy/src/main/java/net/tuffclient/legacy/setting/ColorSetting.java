package net.tuffclient.legacy.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** An ARGB color, picked from Glade's swatches; chroma cycles through the rainbow. */
public final class ColorSetting extends Setting<Integer> {
	public static final int[] SWATCHES = {
			0xFFFFFFFF, 0xFFE3E8E1, 0xFF9AA59C, 0xFF1F2A22, 0xFF2F6B4F, 0xFF5FB08A, 0xFF7FD3C1, 0xFF55B6FF,
			0xFF8B7CF6, 0xFFE07AC6, 0xFFFF5F6D, 0xFFFF9F45, 0xFFFFE155, 0xFF55FF55, 0xFFB86B4B, 0xFF000000};
	public boolean chroma;

	public ColorSetting(String id, String label, int argb) {
		super(id, label, argb);
	}

	/** The color to draw now (chroma moves with time; offset staggers characters or keys). */
	public int argb(float offset) {
		if (!chroma) return value;
		float hue = ((System.currentTimeMillis() % 4000L) / 4000f + offset) % 1f;
		return (value & 0xFF000000) | (java.awt.Color.HSBtoRGB(hue, 0.65f, 1f) & 0xFFFFFF);
	}

	public int argb() {
		return argb(0);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(String.format("#%08X%s", value, chroma ? "c" : ""));
	}

	@Override
	public void fromJson(JsonElement json) {
		if (!json.isJsonPrimitive()) return;
		String s = json.getAsString();
		chroma = s.endsWith("c");
		try {
			value = (int) Long.parseLong(s.replace("#", "").replace("c", ""), 16);
		} catch (NumberFormatException ignored) {
		}
	}
}
