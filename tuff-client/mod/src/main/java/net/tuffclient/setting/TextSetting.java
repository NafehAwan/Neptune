package net.tuffclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A short line of text, typed into the module's settings. */
public final class TextSetting extends Setting<String> {
	public final int maxLength;

	public TextSetting(String id, String name, String value, int maxLength) {
		super(id, name, value);
		this.maxLength = maxLength;
	}

	@Override
	public void set(String v) {
		value = v == null ? "" : v.length() > maxLength ? v.substring(0, maxLength) : v;
	}

	@Override
	public String display() {
		return value;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive()) set(json.getAsString());
	}
}
