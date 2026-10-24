package net.tuffclient.account;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.tuffclient.TuffClient;
import net.tuffclient.mixin.MinecraftAccessor;

/**
 * The accounts the in-game account menu offers: the ones the Glade launcher
 * knows (it writes names and kinds to config/tuff/accounts.json, never tokens)
 * plus offline accounts added in game.
 * <p>
 * Offline accounts switch on the spot. Microsoft accounts need a sign-in token,
 * so switching to another one happens in the launcher.
 */
public final class Accounts {
	public record Account(UUID id, String name, boolean microsoft, boolean addedInGame) {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("tuff");
	private static final Path LAUNCHER = DIR.resolve("accounts.json");
	private static final Path LOCAL = DIR.resolve("offline-accounts.json");

	/** The account the game was started with, so it stays in the list after a switch. */
	private static Account launchAccount;
	private static User launchUser;
	/** Set once the player switches accounts in game. */
	private static GameProfile switched;
	private static List<Account> cache;
	private static long cacheTime;

	private Accounts() {
	}

	public static UUID offlineId(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}

	private static UUID parse(String id) {
		String hex = id.replace("-", "").toLowerCase(Locale.ROOT);
		if (hex.length() != 32) return null;
		try {
			return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static boolean isCurrent(Account account) {
		return account.id().equals(Minecraft.getInstance().getUser().getProfileId());
	}

	public static Account launchAccount() {
		if (launchAccount == null) {
			User user = Minecraft.getInstance().getUser();
			launchUser = user;
			boolean microsoft = user.getXuid().isPresent() || user.getAccessToken().length() > 32;
			launchAccount = new Account(user.getProfileId(), user.getName(), microsoft, false);
		}
		return launchAccount;
	}

	/** The account the game is playing as. */
	public static Account current() {
		User user = Minecraft.getInstance().getUser();
		for (Account a : list()) if (a.id().equals(user.getProfileId())) return a;
		return launchAccount();
	}

	/** All accounts, the launcher's first. Re-read at most once a second. */
	public static List<Account> list() {
		long now = System.currentTimeMillis();
		if (cache != null && now - cacheTime < 1000) return cache;
		List<Account> out = new ArrayList<>();
		for (JsonElement e : readArray(LAUNCHER, "accounts")) add(out, e, false);
		for (JsonElement e : readArray(LOCAL, "accounts")) add(out, e, true);
		Account first = launchAccount();
		if (out.stream().noneMatch(a -> a.id().equals(first.id()))) out.add(0, first);
		User user = Minecraft.getInstance().getUser();
		if (out.stream().noneMatch(a -> a.id().equals(user.getProfileId()))) {
			out.add(new Account(user.getProfileId(), user.getName(), false, false));
		}
		cache = out;
		cacheTime = now;
		return out;
	}

	private static void add(List<Account> out, JsonElement e, boolean local) {
		if (!e.isJsonObject()) return;
		JsonObject o = e.getAsJsonObject();
		if (!o.has("id") || !o.has("name")) return;
		String name = o.get("name").getAsString();
		UUID id = parse(o.get("id").getAsString());
		boolean microsoft = o.has("kind") && o.get("kind").getAsString().equals("msa");
		if (id == null) id = offlineId(name);
		UUID finalId = id;
		if (out.stream().anyMatch(a -> a.id().equals(finalId))) return;
		out.add(new Account(id, name, microsoft, local));
	}

	private static JsonArray readArray(Path file, String key) {
		try {
			if (!Files.isRegularFile(file)) return new JsonArray();
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (root.isJsonObject() && root.getAsJsonObject().get(key) instanceof JsonArray array) return array;
		} catch (IOException | RuntimeException e) {
			TuffClient.LOGGER.warn("Could not read {}", file, e);
		}
		return new JsonArray();
	}

	public static boolean validName(String name) {
		return name.matches("[A-Za-z0-9_]{3,16}");
	}

	/** Adds an offline account and switches to it. */
	public static Account addOffline(String name) {
		Account account = new Account(offlineId(name), name, false, true);
		JsonArray array = readArray(LOCAL, "accounts");
		JsonObject o = new JsonObject();
		o.addProperty("id", account.id().toString());
		o.addProperty("name", name);
		o.addProperty("kind", "offline");
		array.add(o);
		writeLocal(array);
		switchTo(account);
		return account;
	}

	public static void remove(Account account) {
		JsonArray array = readArray(LOCAL, "accounts");
		JsonArray kept = new JsonArray();
		for (JsonElement e : array) {
			if (e.isJsonObject() && e.getAsJsonObject().has("id") && account.id().equals(parse(e.getAsJsonObject().get("id").getAsString()))) continue;
			kept.add(e);
		}
		writeLocal(kept);
	}

	private static void writeLocal(JsonArray array) {
		JsonObject root = new JsonObject();
		root.add("accounts", array);
		try {
			Files.createDirectories(DIR);
			Files.writeString(LOCAL, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			TuffClient.LOGGER.warn("Could not save offline accounts", e);
		}
		cache = null;
	}

	/**
	 * Switches to an account. Returns null when it worked, or a short reason
	 * when it can't happen here.
	 */
	public static String switchTo(Account account) {
		if (isCurrent(account)) return null;
		Minecraft mc = Minecraft.getInstance();
		boolean back = account.id().equals(launchAccount().id());
		if (account.microsoft() && !back) return "Switch Microsoft accounts in the Glade launcher";
		if (mc.level != null) return "Leave the world first";
		if (back) {
			// the session the game started with is still valid, Microsoft or not
			((MinecraftAccessor) mc).glade$setUser(launchUser);
			switched = null;
			cache = null;
			return null;
		}
		((MinecraftAccessor) mc).glade$setUser(net.tuffclient.compat.Users.offline(account.name(), account.id()));
		switched = new GameProfile(account.id(), account.name());
		cache = null;
		return null;
	}

	/** The profile to use after an in-game switch, or null to keep the game's own. */
	public static GameProfile switchedProfile() {
		return switched;
	}
}
