// glade: minecraft >=1.21.9
package net.tuffclient.account;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Skin textures for account heads. Premium accounts use their real skin from
 * Minecraft's skin manager; everyone else, and anyone whose skin hasn't
 * loaded yet, gets Steve.
 */
public final class SkinTextures {
	public static final Identifier STEVE = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

	private record Entry(CompletableFuture<Optional<PlayerSkin>> skin, long started) {
	}

	private static final Map<UUID, Entry> CACHE = new HashMap<>();

	private SkinTextures() {
	}

	public static Identifier of(Accounts.Account account) {
		if (!account.microsoft()) return STEVE;
		Minecraft mc = Minecraft.getInstance();
		boolean self = Accounts.isCurrent(account) && Accounts.switchedProfile() == null;
		Entry entry = CACHE.get(account.id());
		long now = System.currentTimeMillis();
		Identifier found = entry == null ? null : texture(entry.skin());
		// the signed-in profile's textures arrive a moment after start-up, so ask again until a real skin shows up
		if (entry == null || (found == null && self && now - entry.started() > 3000)) {
			GameProfile profile = self ? mc.getGameProfile() : new GameProfile(account.id(), account.name());
			entry = new Entry(mc.getSkinManager().get(profile), now);
			CACHE.put(account.id(), entry);
			found = texture(entry.skin());
		}
		return found != null ? found : STEVE;
	}

	/** The loaded skin's texture, or null while loading or when it is only a default skin. */
	private static Identifier texture(CompletableFuture<Optional<PlayerSkin>> future) {
		if (!future.isDone() || future.isCompletedExceptionally()) return null;
		Optional<PlayerSkin> skin = future.getNow(Optional.empty());
		if (skin.isEmpty()) return null;
		Identifier id = skin.get().body().texturePath();
		return id.getPath().startsWith("textures/entity/player/") ? null : id;
	}
}
