package net.tuffclient.module;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.tuffclient.setting.Setting;

public abstract class Module {
	public final String id;
	public final String name;
	public final String description;
	public final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;
	/** Starred in the mod menu; shown under the Favorites filter. */
	public boolean favorite;

	protected Module(String id, String name, String description, Category category, boolean enabledByDefault) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.enabled = enabledByDefault;
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public List<Setting<?>> settings() {
		return settings;
	}

	/** Mixins this module needs; on a Minecraft version without them it's hidden and off. */
	private String[] mixins = {};
	private Boolean available;

	protected void requires(String... mixinNames) {
		mixins = mixinNames;
	}

	/** Whether this Minecraft version has what the module needs (any of its mixins was built for it). */
	public boolean available() {
		if (available == null) {
			boolean found = mixins.length == 0;
			for (String name : mixins) {
				if (Module.class.getClassLoader().getResource("net/tuffclient/mixin/" + name + ".class") != null) found = true;
			}
			available = found;
		}
		return available;
	}

	/** Hidden modules hold settings but don't appear as mod cards. */
	public boolean hidden() {
		return !available();
	}

	public boolean isEnabled() {
		return enabled && available();
	}

	public void setEnabled(boolean value) {
		if (value == enabled) return;
		enabled = value;
		if (value) onEnable();
		else onDisable();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Called at the end of every client tick while enabled. */
	public void tick(Minecraft client) {
	}

	public JsonObject save() {
		JsonObject json = new JsonObject();
		json.addProperty("enabled", enabled);
		if (favorite) json.addProperty("favorite", true);
		JsonObject values = new JsonObject();
		for (Setting<?> s : settings) values.add(s.id, s.toJson());
		json.add("settings", values);
		return json;
	}

	public void load(JsonObject json) {
		if (json.has("enabled")) enabled = json.get("enabled").getAsBoolean();
		favorite = json.has("favorite") && json.get("favorite").getAsBoolean();
		if (json.has("settings") && json.get("settings").isJsonObject()) {
			JsonObject values = json.getAsJsonObject("settings");
			for (Setting<?> s : settings) {
				if (values.has(s.id)) s.fromJson(values.get(s.id));
			}
		}
	}
}
