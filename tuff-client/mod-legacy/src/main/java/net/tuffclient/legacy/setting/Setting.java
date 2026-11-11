package net.tuffclient.legacy.setting;

import com.google.gson.JsonElement;

/** One module option, saved to the config by id. */
public abstract class Setting<T> {
	public final String id;
	public final String label;
	protected T value;
	private final T fallback;

	protected Setting(String id, String label, T value) {
		this.id = id;
		this.label = label;
		this.value = value;
		this.fallback = value;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	public void reset() {
		value = fallback;
	}

	public abstract JsonElement toJson();

	public abstract void fromJson(JsonElement json);
}
