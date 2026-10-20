package net.tuffclient.tiers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.tuffclient.TuffClient;

/**
 * Player tiers from MCTiers and PvPTiers, fetched on one background thread and
 * cached (two hours, unranked players too), so nametags never wait on the
 * network and the sites see at most one request per player.
 */
public final class TierService {
	public enum Site {
		MCTIERS, PVPTIERS
	}

	/** One gamemode's rank: tier 1 (best) to 5, high or low, retired or not. */
	public record Rank(int tier, boolean high, boolean retired) {
		public String label() {
			return (retired ? "R" : "") + (high ? "HT" : "LT") + tier;
		}

		/** Lower is better: HT1 = 0, LT1 = 1, HT2 = 2 ... */
		public int score() {
			return (tier - 1) * 2 + (high ? 0 : 1);
		}
	}

	/** A player's ranks on one site (empty when unranked); null while unknown. */
	private record Entry(Map<String, Rank> ranks, long until) {
	}

	private static final long KEEP = Duration.ofHours(2).toMillis(), RETRY = Duration.ofMinutes(5).toMillis();
	private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Glade tiers");
		t.setDaemon(true);
		return t;
	});
	private static volatile HttpClient http;
	/** When a site failed, wait before asking it again. */
	private static final Map<Site, Long> DOWN_UNTIL = new ConcurrentHashMap<>();

	private TierService() {
	}

	/** Puts ranks in the cache directly (for the in-game test, which has no tier site to ask). */
	public static void put(Site site, String name, Map<String, Rank> ranks) {
		CACHE.put(site + ":" + name.toLowerCase(java.util.Locale.ROOT), new Entry(ranks, Long.MAX_VALUE));
	}

	/** The ranks if known (asks in the background otherwise). */
	public static Map<String, Rank> ranks(Site site, String name, UUID uuid) {
		String key = site + ":" + name.toLowerCase(java.util.Locale.ROOT);
		Entry e = CACHE.get(key);
		long now = System.currentTimeMillis();
		if (e == null || e.until < now) {
			if (DOWN_UNTIL.getOrDefault(site, 0L) < now && PENDING.putIfAbsent(key, true) == null) {
				WORKER.submit(() -> {
					try {
						Map<String, Rank> ranks = fetch(site, name, uuid);
						CACHE.put(key, new Entry(ranks, System.currentTimeMillis() + KEEP));
					} catch (Exception ex) {
						DOWN_UNTIL.put(site, System.currentTimeMillis() + RETRY);
						TuffClient.LOGGER.debug("Tiers: {} lookup for {} failed: {}", site, name, ex.toString());
					} finally {
						PENDING.remove(key);
					}
				});
			}
			return e == null ? null : e.ranks;
		}
		return e.ranks;
	}

	private static Map<String, Rank> fetch(Site site, String name, UUID uuid) throws Exception {
		String n = URLEncoder.encode(name, StandardCharsets.UTF_8);
		// online-mode players have real (version 4) UUIDs; others are looked up by name
		boolean real = uuid != null && uuid.version() == 4;
		String id = real ? uuid.toString().replace("-", "") : null;
		String[] urls = site == Site.MCTIERS
				? (real ? new String[] {"https://mctiers.com/api/v2/profile/" + id, "https://mctiers.com/api/v2/profile/by-name/" + n}
						: new String[] {"https://mctiers.com/api/v2/profile/by-name/" + n})
				: (real ? new String[] {"https://pvptiers.com/api/profile/" + id, "https://pvptiers.com/api/search_profile/" + n}
						: new String[] {"https://pvptiers.com/api/search_profile/" + n});
		for (String url : urls) {
			HttpResponse<String> res = client().send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
					.header("User-Agent", "Glade/" + TuffClient.VERSION + " (Minecraft client; tier nametags)")
					.header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() == 404) continue;
			if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
			return parse(res.body());
		}
		return Map.of(); // not on the list
	}

	/** Reads {"rankings": {"sword": {"tier": 2, "pos": 0, "retired": false}, ...}}. */
	static Map<String, Rank> parse(String body) {
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		Map<String, Rank> out = new java.util.HashMap<>();
		if (!root.has("rankings") || !root.get("rankings").isJsonObject()) return out;
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("rankings").entrySet()) {
			if (!e.getValue().isJsonObject()) continue;
			JsonObject r = e.getValue().getAsJsonObject();
			if (!r.has("tier") || r.get("tier").isJsonNull()) continue;
			int tier = r.get("tier").getAsInt();
			boolean high = r.has("pos") && !r.get("pos").isJsonNull() && r.get("pos").getAsInt() == 0;
			boolean retired = r.has("retired") && !r.get("retired").isJsonNull() && r.get("retired").getAsBoolean();
			if (tier >= 1 && tier <= 5) out.put(e.getKey(), new Rank(tier, high, retired));
		}
		return out;
	}

	private static HttpClient client() {
		if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
		return http;
	}
}
