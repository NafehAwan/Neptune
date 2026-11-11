package net.tuffclient.legacy.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class NumberSetting extends Setting<Double> {
	public final double min, max, step;

	public NumberSetting(String id, String label, double value, double min, double max, double step) {
		super(id, label, value);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	@Override
	public void set(Double v) {
		double snapped = Math.round(v / step) * step;
		value = Math.max(min, Math.min(max, snapped));
	}

	/** 0..1 along the slider. */
	public double fraction() {
		return (value - min) / (max - min);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive()) set(json.getAsDouble());
	}
}
