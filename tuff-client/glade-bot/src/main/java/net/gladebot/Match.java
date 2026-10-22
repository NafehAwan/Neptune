package net.gladebot;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * One fight against the bot: /bot start clears both inventories, gives the
 * kits, counts down 10 seconds, then the bot fights until one of you would
 * die (nobody actually dies: the loser is healed and the result shown).
 */
public final class Match {
	public enum State { NONE, COUNTDOWN, FIGHT, OVER }

	/** Ticks of countdown before the fight (10 seconds). */
	public static final int COUNTDOWN = 200;
	public static State state = State.NONE;
	public static BotPlayer bot;
	/** Who won the last match ("you" or "bot"), for the result screen and tests. */
	public static String winner;
	private static UUID playerId;
	private static int ticks;
	private static GameType savedMode;

	private Match() {
	}

	public static String start(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		MinecraftServer server = level.getServer();
		stop(server);
		Vec3 ahead = Vec3.directionFromRotation(0, player.getYRot()).scale(6);
		Vec3 pos = ground(level, player.position().add(ahead), player.getY());
		bot = BotPlayer.spawn(server, level, pos, player.getYRot() + 180);
		bot.brain = new BotBrain(bot, player);

		savedMode = player.gameMode.getGameModeForPlayer();
		player.setGameMode(GameType.SURVIVAL);
		for (ServerPlayer p : new ServerPlayer[] {player, bot}) {
			refresh(p);
			Kits.apply(p, Kits.kitFor(server.registryAccess(), p == bot));
		}
		playerId = player.getUUID();
		winner = null;
		state = State.COUNTDOWN;
		ticks = 0;
		return "Neth Pot vs GladeBot (" + Settings.tier.label + (Settings.mend ? ", mend" : ", no mend") + "): get ready!";
	}

	/** Ends the match now and removes the bot. */
	public static void stop(MinecraftServer server) {
		if (bot != null) {
			bot.brain.stop();
			if (!bot.isRemoved()) bot.remove();
		}
		ServerPlayer player = playerId == null ? null : server.getPlayerList().getPlayer(playerId);
		if (player != null && savedMode != null) player.setGameMode(savedMode);
		bot = null;
		playerId = null;
		savedMode = null;
		state = State.NONE;
	}

	public static void tick(MinecraftServer server) {
		if (state == State.NONE) return;
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null || bot == null || bot.isRemoved()) {
			stop(server);
			return;
		}
		ticks++;
		switch (state) {
			case COUNTDOWN -> {
				if (ticks % 20 == 1) {
					int left = (COUNTDOWN - ticks + 1) / 20 + 1;
					title(player, Component.literal(String.valueOf(left)).withStyle(left <= 3 ? ChatFormatting.RED : ChatFormatting.WHITE));
					sound(player, 1f);
				}
				if (ticks >= COUNTDOWN) {
					state = State.FIGHT;
					title(player, Component.literal("Fight!").withStyle(ChatFormatting.GREEN));
					sound(player, 2f);
					bot.brain.startFight();
				}
			}
			case OVER -> {
				if (ticks >= 60) stop(server);
			}
			default -> {
			}
		}
	}

	/**
	 * Called before a player in the match dies. A totem in either hand saves
	 * them as usual; otherwise nobody dies: the match ends and they're healed.
	 */
	public static boolean allowDeath(LivingEntity entity) {
		if (state != State.FIGHT && state != State.COUNTDOWN) return true;
		boolean isBot = entity == bot;
		boolean isPlayer = entity instanceof ServerPlayer sp && sp.getUUID().equals(playerId);
		if (!isBot && !isPlayer) return true;
		if (entity.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || entity.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return true;
		entity.setHealth(entity.getMaxHealth());
		entity.removeAllEffects();
		end(((ServerLevel) entity.level()).getServer(), isBot ? "you" : "bot");
		return false;
	}

	private static void end(MinecraftServer server, String who) {
		winner = who;
		state = State.OVER;
		ticks = 0;
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		BotBrain brain = bot.brain;
		brain.stop();
		if (player == null) return;
		boolean won = who.equals("you");
		title(player, Component.literal(won ? "You win!" : "GladeBot wins").withStyle(won ? ChatFormatting.GREEN : ChatFormatting.RED));
		player.sendSystemMessage(Component.literal("GladeBot (" + Settings.tier.label + ") landed " + brain.hits + " hits and threw " + brain.pots + " pots."));
	}

	private static void refresh(ServerPlayer p) {
		p.removeAllEffects();
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
		p.getFoodData().setSaturation(20f);
		p.clearFire();
	}

	/** Where to stand: the spot ahead if there's room, otherwise the ground above it. */
	private static Vec3 ground(ServerLevel level, Vec3 at, double y) {
		BlockPos feet = BlockPos.containing(at.x, y, at.z);
		if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir() && !level.getBlockState(feet.below()).isAir()) {
			return new Vec3(at.x, feet.getY(), at.z);
		}
		return new Vec3(at.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()), at.z);
	}

	private static void title(ServerPlayer player, Component text) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 22, 4));
		player.connection.send(new ClientboundSetTitleTextPacket(text));
	}

	private static void sound(ServerPlayer player, float pitch) {
		player.level().playSound((net.minecraft.world.entity.player.Player) null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, pitch);
	}
}
