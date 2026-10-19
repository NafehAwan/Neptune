package net.tuffclient.legacy.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ChoiceSetting extends Setting<Integer> {
	public final String[] choices;

	public ChoiceSetting(String id, String label, int value, String... choices) {
		super(id, label, value);
		this.choices = choices;
	}

	public String current() {
		return choices[value];
	}

	public void next() {
		value = (value + 1) % choices.length;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive()) value = Math.max(0, Math.min(choices.length - 1, json.getAsInt()));
	}
}
