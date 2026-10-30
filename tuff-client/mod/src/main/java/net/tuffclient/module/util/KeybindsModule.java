package net.tuffclient.module.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;

/**
 * Keybinds: extra keys for any action (sprint on Ctrl and a mouse button, for
 * example). The game's own bindings stay as they are in Controls; the extra
 * keys are listed and set here.
 */
public final class KeybindsModule extends Module {
	public static final int SLOTS = 8;

	/** One extra key: the action's name (like "key.sprint") and the key's name (like "key.mouse.4"). */
	public static final class Bind {
		public String action = "";
		public InputConstants.Key key = InputConstants.UNKNOWN;

		public KeyMapping mapping() {
			return action.isEmpty() ? null : KeyMapping.get(action);
		}

		public boolean active() {
			return mapping() != null && key != InputConstants.UNKNOWN;
		}
	}

	public final Bind[] binds = new Bind[SLOTS];

	public KeybindsModule() {
		super("keybinds", "Keybinds", "Give any action extra keys, set here. The game's Controls keep their own keys.", Category.UTILITY, false);
		for (int i = 0; i < SLOTS; i++) binds[i] = new Bind();
	}

	/** A key went down or up: the actions with it as an extra key go down or up too. */
	public void set(InputConstants.Key key, boolean down) {
		if (!isEnabled()) return;
		for (Bind b : binds) {
			if (b.active() && b.key.equals(key)) b.mapping().setDown(down);
		}
	}

	/** A key was pressed: the actions with it as an extra key get the press too. */
	public void click(InputConstants.Key key) {
		if (!isEnabled()) return;
		for (Bind b : binds) {
			if (b.active() && b.key.equals(key)) {
				var m = (net.tuffclient.mixin.KeyMappingAccessor) b.mapping();
				m.glade$setClickCount(m.glade$getClickCount() + 1);
			}
		}
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		JsonArray list = new JsonArray();
		for (Bind b : binds) {
			JsonObject o = new JsonObject();
			o.addProperty("action", b.action);
			o.addProperty("key", b.key.getName());
			list.add(o);
		}
		json.add("binds", list);
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		if (!json.has("binds") || !json.get("binds").isJsonArray()) return;
		int i = 0;
		for (JsonElement e : json.getAsJsonArray("binds")) {
			if (i >= SLOTS || !e.isJsonObject()) break;
			JsonObject o = e.getAsJsonObject();
			binds[i].action = o.has("action") ? o.get("action").getAsString() : "";
			try {
				binds[i].key = o.has("key") ? InputConstants.getKey(o.get("key").getAsString()) : InputConstants.UNKNOWN;
			} catch (RuntimeException ex) {
				binds[i].key = InputConstants.UNKNOWN;
			}
			i++;
		}
	}
}
