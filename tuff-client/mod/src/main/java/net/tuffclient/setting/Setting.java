package net.tuffclient.setting;

import java.util.function.BooleanSupplier;

import com.google.gson.JsonElement;

/** A user-editable module option, shown in the module's settings panel. */
public abstract sealed class Setting<T> permits BoolSetting, ChoiceSetting, ColorSetting, NumberSetting, TextSetting {
	public final String id;
	public final String name;
	protected T value;
	private BooleanSupplier visible = () -> true;

	protected Setting(String id, String name, T value) {
		this.id = id;
		this.name = name;
		this.value = value;
	}

	/** Shows this setting only while the condition holds (say, a colour only while its feature is on). */
	@SuppressWarnings("unchecked")
	public <S extends Setting<?>> S showIf(BooleanSupplier condition) {
		visible = condition;
		return (S) this;
	}

	public boolean visible() {
		return visible.getAsBoolean();
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	/** Human-readable value for the settings panel. */
	public abstract String display();

	public abstract JsonElement toJson();

	public abstract void fromJson(JsonElement json);
}
