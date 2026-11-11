package net.tuffclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A colour, picked on the colour wheel in a module's settings. Stored as ARGB;
 * saved as "#AARRGGBB". Chroma cycles the hue over time (a rainbow).
 */
public final class ColorSetting extends Setting<Integer> {
	/** Whether the picker shows an opacity bar. */
	public final boolean alpha;
	public boolean chroma;
	/** With chroma on: the rainbow flows letter by letter (wave) and how fast it moves (0 slow, 1 normal, 2 fast). */
	public boolean wave = true;
	public int speed = 1;
	private static final float[] SPEEDS = {0.4f, 1f, 2.2f};
	private final int initial;

	public ColorSetting(String id, String name, int argb, boolean alpha) {
		super(id, name, alpha ? argb : argb | 0xFF000000);
		this.alpha = alpha;
		this.initial = this.value;
	}

	/** The colour to draw with this frame (chroma moves through the hues). */
	public int argb() {
		return argb(0);
	}

	/** The colour with the rainbow shifted by {@code offset} (in loops), for waves across text. */
	public int argb(float offset) {
		if (!chroma) return value;
		float[] hsv = toHsv(value);
		double factor = SPEEDS[Math.max(0, Math.min(2, speed))];
		float hue = (float) ((System.currentTimeMillis() * factor % 4000.0) / 4000.0) - (wave ? offset : 0);
		return fromHsv(hue, Math.max(0.6f, hsv[1]), Math.max(0.7f, hsv[2]), value >>> 24);
	}

	public void reset() {
		value = initial;
		chroma = false;
		wave = true;
		speed = 1;
	}

	@Override
	public void set(Integer v) {
		value = alpha ? v : v | 0xFF000000;
	}

	@Override
	public String display() {
		return alpha && (value >>> 24) != 0xFF ? String.format("#%08X", value) : String.format("#%06X", value & 0xFFFFFF);
	}

	@Override
	public JsonElement toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("color", String.format("#%08X", value));
		if (chroma) json.addProperty("chroma", true);
		if (!wave) json.addProperty("wave", false);
		if (speed != 1) json.addProperty("speed", speed);
		return json;
	}

	@Override
	public void fromJson(JsonElement json) {
		String hex = null;
		if (json.isJsonPrimitive()) hex = json.getAsString();
		else if (json.isJsonObject()) {
			JsonObject o = json.getAsJsonObject();
			if (o.has("color")) hex = o.get("color").getAsString();
			chroma = o.has("chroma") && o.get("chroma").getAsBoolean();
			wave = !o.has("wave") || o.get("wave").getAsBoolean();
			speed = o.has("speed") ? Math.max(0, Math.min(2, o.get("speed").getAsInt())) : 1;
		}
		Integer parsed = parse(hex);
		if (parsed != null) set(parsed);
	}

	/** "#RGB", "#RRGGBB" or "#AARRGGBB" (the # is optional). */
	public static Integer parse(String hex) {
		if (hex == null) return null;
		String h = hex.trim();
		if (h.startsWith("#")) h = h.substring(1);
		try {
			return switch (h.length()) {
				case 3 -> 0xFF000000 | Integer.parseInt("" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2), 16);
				case 6 -> 0xFF000000 | Integer.parseInt(h, 16);
				case 8 -> (int) Long.parseLong(h, 16);
				default -> null;
			};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Hue, saturation and value, each 0..1. */
	public static float[] toHsv(int argb) {
		float r = (argb >> 16 & 0xFF) / 255f, g = (argb >> 8 & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
		float h = 0;
		if (d > 0) {
			if (max == r) h = ((g - b) / d) % 6;
			else if (max == g) h = (b - r) / d + 2;
			else h = (r - g) / d + 4;
			h /= 6;
			if (h < 0) h += 1;
		}
		return new float[] {h, max == 0 ? 0 : d / max, max};
	}

	public static int fromHsv(float h, float s, float v, int alpha) {
		h = (h % 1 + 1) % 1;
		float c = v * s, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c;
		float r, g, b;
		switch ((int) (h * 6)) {
			case 0 -> { r = c; g = x; b = 0; }
			case 1 -> { r = x; g = c; b = 0; }
			case 2 -> { r = 0; g = c; b = x; }
			case 3 -> { r = 0; g = x; b = c; }
			case 4 -> { r = x; g = 0; b = c; }
			default -> { r = c; g = 0; b = x; }
		}
		return (alpha & 0xFF) << 24 | Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
	}
}
