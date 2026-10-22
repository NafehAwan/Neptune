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
	/** Jump as it gets hit, to take less knockback. */
	public static boolean jumpReset = true;
	/** Let go of W for a tick or two after a sprint hit. */
	public static boolean wtap = true;
	/** Deivi pops: 0 off, 1 rare, 2 often. */
	public static int deivi = 1;
	/** Playstyle: random switches between the others as the fight goes. */
	public static Style style = Style.RANDOM;
	/** XP orbs are picked up all at once (like the Clumps mod), so mending is instant. */
	public static boolean clumps = true;

	public enum Style {
		RANDOM("Random"), AGGRESSIVE("Aggressive"), CRIT("Crit"), STRAFE("Strafe");

		public final String label;

		Style(String label) {
			this.label = label;
		}

		public static Style parse(String s) {
			for (Style v : values()) if (v.name().equalsIgnoreCase(s)) return v;
			return null;
		}
	}

	public static final String[] DEIVI = {"Off", "Rare", "Often"};

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
			if (o.has("jumpReset")) jumpReset = o.get("jumpReset").getAsBoolean();
			if (o.has("wtap")) wtap = o.get("wtap").getAsBoolean();
			if (o.has("deivi")) deivi = Math.max(0, Math.min(2, o.get("deivi").getAsInt()));
			if (o.has("style")) {
				Style st = Style.parse(o.get("style").getAsString());
				if (st != null) style = st;
			}
			if (o.has("clumps")) clumps = o.get("clumps").getAsBoolean();
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
		o.addProperty("jumpReset", jumpReset);
		o.addProperty("wtap", wtap);
		o.addProperty("deivi", deivi);
		o.addProperty("style", style.name());
		o.addProperty("clumps", clumps);
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
