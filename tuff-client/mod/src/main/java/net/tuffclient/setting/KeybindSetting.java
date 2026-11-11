package net.tuffclient.setting;

import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * A module's key, set right in its settings. The key itself lives in the
 * game's Controls (options.txt), so this setting saves nothing of its own.
 */
public final class KeybindSetting extends Setting<String> {
	private final Supplier<KeyMapping> mapping;

	public KeybindSetting(String id, String name, Supplier<KeyMapping> mapping) {
		super(id, name, "");
		this.mapping = mapping;
	}

	public void bind(InputConstants.Key key) {
		KeyMapping m = mapping.get();
		if (m == null) return;
		m.setKey(key);
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
	}

	public void unbind() {
		bind(InputConstants.UNKNOWN);
	}

	@Override
	public String display() {
		KeyMapping m = mapping.get();
		if (m == null || m.isUnbound()) return "None";
		return m.getTranslatedKeyMessage().getString();
	}

	@Override
	public JsonElement toJson() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void fromJson(JsonElement json) {
	}
}
