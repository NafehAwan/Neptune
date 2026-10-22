package net.gladebot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

/** The bot's settings and saved kits, in config/gladebot.json. */
public final class Settings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("gladebot.json");

	public static Tier tier = Tier.LT3;
	/** Mend: XP bottles in both kits, and the bot backs off to repair its armour. */
	public static boolean mend = true;
	/** Saved kits (null = the default Neth Pot kit). */
	public static JsonElement playerKit, botKit;

	private Settings() {
	}

	public static void load() {
		try {
			if (!Files.exists(FILE)) return;
			JsonObject o = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
			if (o.has("tier")) {
				Tier t = Tier.parse(o.get("tier").getAsString());
				if (t != null) tier = t;
			}
			if (o.has("mend")) mend = o.get("mend").getAsBoolean();
			playerKit = o.has("playerKit") ? o.get("playerKit") : null;
			botKit = o.has("botKit") ? o.get("botKit") : null;
		} catch (IOException | RuntimeException e) {
			GladeBot.LOGGER.warn("Couldn't read {}: {}", FILE, e.toString());
		}
	}

	public static void save() {
		JsonObject o = new JsonObject();
		o.addProperty("tier", tier.name());
		o.addProperty("mend", mend);
		if (playerKit != null) o.add("playerKit", playerKit);
		if (botKit != null) o.add("botKit", botKit);
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(o));
		} catch (IOException e) {
			GladeBot.LOGGER.warn("Couldn't save {}: {}", FILE, e.toString());
		}
	}
}
