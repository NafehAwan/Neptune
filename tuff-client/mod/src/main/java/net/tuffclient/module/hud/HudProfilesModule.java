package net.tuffclient.module.hud;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.tuffclient.TuffClient;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.ChoiceSetting;

/**
 * Three HUD layouts to switch between (say PvP, building and recording).
 * Picking another one keeps the current layout in its slot and loads the new
 * one; an empty slot starts as a copy of the current layout.
 */
public final class HudProfilesModule extends Module {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tuff").resolve("hud-profiles.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public final ChoiceSetting profile = add(new ChoiceSetting("profile", "Layout", 0, "1", "2", "3"));
	private int loaded = -1;

	public HudProfilesModule() {
		super("hud_profiles", "HUD Profiles", "Keep three HUD layouts and switch between them.", Category.HUD, true);
	}

	@Override
	public void tick(Minecraft client) {
		if (loaded < 0) loaded = profile.get();
		if (profile.get() == loaded) return;
		JsonObject all = read();
		all.add(String.valueOf(loaded), snapshot());
		JsonObject next = all.has(String.valueOf(profile.get())) ? all.getAsJsonObject(String.valueOf(profile.get())) : null;
		if (next != null) {
			for (Module m : TuffClient.modules().all()) {
				if (m instanceof HudModule && next.has(m.id) && next.get(m.id).isJsonObject()) m.load(next.getAsJsonObject(m.id));
			}
		}
		loaded = profile.get();
		write(all);
	}

	private static JsonObject snapshot() {
		JsonObject out = new JsonObject();
		for (Module m : TuffClient.modules().all()) {
			if (m instanceof HudModule) out.add(m.id, m.save());
		}
		return out;
	}

	private static JsonObject read() {
		try {
			if (Files.exists(FILE)) return JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			TuffClient.LOGGER.warn("Couldn't read HUD profiles", e);
		}
		return new JsonObject();
	}

	private static void write(JsonObject all) {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(all), StandardCharsets.UTF_8);
		} catch (Exception e) {
			TuffClient.LOGGER.warn("Couldn't save HUD profiles", e);
		}
	}
}
