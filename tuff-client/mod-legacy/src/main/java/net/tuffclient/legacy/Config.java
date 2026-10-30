package net.tuffclient.legacy;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.tuffclient.legacy.module.Module;
import net.tuffclient.legacy.module.ModuleManager;

/** Saves module settings to config/tuff/config-1.8.json. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Config() {
	}

	private static File file() {
		return FabricLoader.getInstance().getConfigDir().resolve("tuff").resolve("config-1.8.json").toFile();
	}

	public static void load(ModuleManager modules) {
		File f = file();
		if (!f.isFile()) return;
		try (Reader r = new FileReader(f)) {
			JsonObject root = new JsonParser().parse(r).getAsJsonObject();
			for (Module m : modules.all()) {
				if (root.has(m.id) && root.get(m.id).isJsonObject()) m.load(root.getAsJsonObject(m.id));
			}
		} catch (Exception e) {
			GladeLegacy.LOGGER.warn("Couldn't read the Glade config", e);
		}
	}

	public static void save(ModuleManager modules) {
		JsonObject root = new JsonObject();
		for (Module m : modules.all()) root.add(m.id, m.save());
		File f = file();
		f.getParentFile().mkdirs();
		try (Writer w = new FileWriter(f)) {
			GSON.toJson(root, w);
		} catch (Exception e) {
			GladeLegacy.LOGGER.warn("Couldn't save the Glade config", e);
		}
	}
}
