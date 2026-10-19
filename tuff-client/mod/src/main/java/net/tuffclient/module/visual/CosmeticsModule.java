package net.tuffclient.module.visual;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.tuffclient.TuffClient;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;

/**
 * A small leaf badge next to the names of Glade players, and a Glade cape.
 * Glade players are listed in tuff-client/cosmetics/users.json in the Glade
 * repository (by UUID); you always see your own badge and cape.
 */
public final class CosmeticsModule extends Module {
	private static final String USERS = "https://raw.githubusercontent.com/NafehAwan/Neptune/master/tuff-client/cosmetics/users.json";
	public static final ClientAsset.ResourceTexture CAPE = new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "entity/cape/glade"));
	private final BoolSetting badge = add(new BoolSetting("badge", "Leaf badge next to Glade players", true));
	private final ColorSetting badgeColor = add(new ColorSetting("badge_color", "Badge color", 0xFF4FC79A, false)).showIf(() -> badge.get());
	private final BoolSetting cape = add(new BoolSetting("cape", "Glade cape", true));
	private final BoolSetting othersCapes = add(new BoolSetting("others_capes", "Show other Glade players' capes", true)).showIf(() -> cape.get());
	private final Set<UUID> users = new HashSet<>();
	private boolean fetched;

	public CosmeticsModule() {
		super("cosmetics", "Cosmetics", "A leaf badge next to Glade players and a Glade cape.", Category.VISUAL, true);
	}

	private void fetch() {
		if (fetched) return;
		fetched = true;
		HttpClient.newHttpClient().sendAsync(HttpRequest.newBuilder(URI.create(USERS)).build(), HttpResponse.BodyHandlers.ofString())
				.thenAccept(r -> {
					if (r.statusCode() != 200) return;
					try {
						Set<UUID> read = new HashSet<>();
						for (var e : JsonParser.parseString(r.body()).getAsJsonArray()) read.add(UUID.fromString(e.getAsString()));
						Minecraft.getInstance().execute(() -> users.addAll(read));
					} catch (RuntimeException ignored) {
						// a bad list just means no badges for others
					}
				});
	}

	public boolean isGlade(Player player) {
		fetch();
		Minecraft mc = Minecraft.getInstance();
		return player == mc.player || users.contains(player.getUUID());
	}

	public boolean showBadge(Player player) {
		return isEnabled() && badge.get() && isGlade(player);
	}

	public boolean showCape(Player player) {
		if (!isEnabled() || !cape.get()) return false;
		return player == Minecraft.getInstance().player || othersCapes.get() && isGlade(player);
	}

	/** The badge put before a Glade player's name. */
	public Component badge() {
		return Component.literal("☘ ").withStyle(s -> s.withColor(badgeColor.argb() & 0xFFFFFF));
	}
}
