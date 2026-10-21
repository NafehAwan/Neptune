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
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.tuffclient.TuffClient;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;

/**
 * A small leaf badge next to the names of Glade players, and a Glade cape.
 * Glade players are listed in tuff-client/cosmetics/users.json in the Glade
 * repository (by UUID); you always see your own badge and cape.
 */
public final class CosmeticsModule extends Module {
	private static final String USERS = "https://raw.githubusercontent.com/NafehAwan/Neptune/master/tuff-client/cosmetics/users.json";
	/** The Glade cape texture (textures/entity/cape/glade.png). */
	public static final Identifier CAPE_TEXTURE = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "entity/cape/glade");
	private final BoolSetting badge = add(new BoolSetting("badge", "Leaf badge next to Glade players", true));
	private final ChoiceSetting badgeSide = add(new ChoiceSetting("badge_side", "Badge side", 0, "Left", "Right")).showIf(() -> badge.get());
	private final BoolSetting badgeTab = add(new BoolSetting("badge_tab", "Badge in the tab list", true)).showIf(() -> badge.get());
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

	/** True when a tab list entry is a Glade player (matched by UUID through the connection, safe on every version). */
	public boolean isGlade(PlayerInfo info) {
		fetch();
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener conn = mc.getConnection();
		if (conn == null || info == null) return false;
		if (mc.player != null && conn.getPlayerInfo(mc.player.getUUID()) == info) return true;
		for (UUID id : users) if (conn.getPlayerInfo(id) == info) return true;
		return false;
	}

	private static final Identifier BADGE_FONT = Identifier.fromNamespaceAndPath("tuff", "badge");

	/** The leaf logo glyph, drawn in its own colours (assets/tuff/font/badge.json). */
	public Component badge() {
		return Component.literal("\uE200").withStyle(s -> net.tuffclient.gui.Fonts.withFont(s.withColor(0xFFFFFF), BADGE_FONT));
	}

	/** Puts the badge on the chosen side of a name. */
	public Component withBadge(Component name) {
		return badgeSide.get() == 0
				? Component.empty().append(badge()).append(" ").append(name)
				: Component.empty().append(name).append(" ").append(badge());
	}

	/** A tab list name with the badge, when it belongs to a Glade player. */
	public Component decorateTab(PlayerInfo info, Component name) {
		if (name == null || !isEnabled() || !badge.get() || !badgeTab.get() || !isGlade(info)) return name;
		return withBadge(name);
	}
}
