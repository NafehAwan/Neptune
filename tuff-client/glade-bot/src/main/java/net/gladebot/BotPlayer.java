package net.gladebot;

import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The bot: a real server player with no client behind it. The server runs
 * its movement each tick (a real player's client would), and {@link BotBrain}
 * presses its "keys".
 */
public final class BotPlayer extends ServerPlayer {
	public static final String NAME = "GladeBot";

	private final FakeConnection fake;
	private final MinecraftServer host;
	private Vec3 knockback;
	public BotBrain brain;

	private BotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, FakeConnection fake) {
		super(server, level, profile, ClientInformation.createDefault());
		this.fake = fake;
		this.host = server;
	}

	public static BotPlayer spawn(MinecraftServer server, ServerLevel level, Vec3 pos, float yaw) {
		return spawn(server, level, pos, yaw, NAME);
	}

	public static BotPlayer spawn(MinecraftServer server, ServerLevel level, Vec3 pos, float yaw, String name) {
		UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		GameProfile profile = new GameProfile(id, name);
		FakeConnection connection = new FakeConnection();
		BotPlayer bot = new BotPlayer(server, level, profile, connection);
		server.getPlayerList().placeNewPlayer(connection, bot, CommonListenerCookie.createInitial(profile, false));
		bot.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yaw, 0, true);
		bot.setGameMode(GameType.SURVIVAL);
		bot.setHealth(bot.getMaxHealth());
		bot.unsetRemoved();
		return bot;
	}

	public void remove() {
		host.getPlayerList().remove(this);
	}

	@Override
	public void tick() {
		fake.drain();
		// knockback from a player's hit: the server undoes it for players (their
		// client applies it), so the bot applies it itself
		if (knockback != null) {
			setDeltaMovement(knockback);
			knockback = null;
		}
		if (brain != null) brain.tick();
		super.tick();
		// what a real player's connection does each tick: move and run the player
		double y = getY();
		doTick();
		// a real player's fall is tracked from their client's movement; the bot has
		// none, so track it here (crits need it: you only crit while falling)
		double dy = getY() - y;
		if (!onGround() && dy < 0) fallDistance = Math.max(fallDistance, 0) - dy;
		else if (onGround()) fallDistance = 0;
	}

	public void knockback(double strength, double x, double z) {
		super.knockback(strength, x, z);
		knockback = getDeltaMovement();
	}

	/** The server moves the bot itself (no client sends its position). */
	public boolean isClientAuthoritative() {
		return false;
	}
}
