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
	/** A self test (CI): a dummy that stands still is "you", and the log tells the story. */
	private static BotPlayer dummy;
	private static boolean selfTest;

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

	/**
	 * /bot selftest: the bot against a second bot ("Sparring", same tier) on a
	 * dedicated server with nobody online, for two minutes. Along the way the
	 * bot is put on low health (it should swap a totem in and pot) and hit
	 * twice for lethal damage (it should double pop). Everything is logged.
	 */
	public static String selfTest(MinecraftServer server) {
		ServerLevel level = server.overworld();
		// load the chunk first, or the height map reads the bottom of the world
		level.getChunk(0, 0);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
		GladeBot.LOGGER.info("[gladebot] selftest arena at y={}", y);
		stop(server);
		Settings.tier = Tier.HT3;
		BotPlayer target = BotPlayer.spawn(server, level, new Vec3(0.5, y, 0.5), 0, "Sparring");
		String msg = start(target);
		target.brain = new BotBrain(target, bot);
		dummy = target;
		selfTest = true;
		GladeBot.LOGGER.info("[gladebot] selftest started: {}", msg);
		return msg;
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
		if (dummy != null && !dummy.isRemoved()) dummy.remove();
		dummy = null;
		selfTest = false;
	}

	public static void tick(MinecraftServer server) {
		if (state == State.NONE) return;
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null || bot == null || bot.isRemoved()) {
			stop(server);
			return;
		}
		ticks++;
		if (Settings.clumps) {
			clumps(player);
			clumps(bot);
			if (dummy != null) clumps(dummy);
		}
		switch (state) {
			case COUNTDOWN -> {
				if (ticks % 20 == 1) {
					int left = (COUNTDOWN - ticks + 1) / 20 + 1;
					title(player, Component.literal(String.valueOf(left)).withStyle(left <= 3 ? ChatFormatting.RED : ChatFormatting.WHITE));
					sound(player, 1f);
				}
				if (ticks >= COUNTDOWN) {
					state = State.FIGHT;
					ticks = 0;
					title(player, Component.literal("Fight!").withStyle(ChatFormatting.GREEN));
					sound(player, 2f);
					bot.brain.startFight();
					if (dummy != null && dummy.brain != null) dummy.brain.startFight();
				}
			}
			case FIGHT -> {
				if (selfTest) selfTestTick();
			}
			case OVER -> {
				if (ticks >= 60) stop(server);
			}
			default -> {
			}
		}
	}

	/** Clumps: every XP orb touching you is picked up at once, so mending is instant. */
	private static void clumps(ServerPlayer p) {
		if (p == null || p.isRemoved()) return;
		for (net.minecraft.world.entity.ExperienceOrb orb : p.level().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, p.getBoundingBox().inflate(1.0))) {
			p.takeXpDelay = 0;
			orb.playerTouch(p);
		}
	}

	private static void selfTestTick() {
		if (ticks % 100 == 0) {
			GladeBot.LOGGER.info("[gladebot] t={} bot hp={} offhand={} | {}", ticks, bot.getHealth(), bot.getOffhandItem().getItem(), bot.brain.stats());
			GladeBot.LOGGER.info("[gladebot] t={} sparring hp={} offhand={} | {}", ticks, dummy.getHealth(), dummy.getOffhandItem().getItem(), dummy.brain.stats());
		}
		ServerLevel level = (ServerLevel) bot.level();
		// low: it should swap a totem in and pot
		if (ticks == 200) {
			bot.setHealth(5f);
			GladeBot.LOGGER.info("[gladebot] bot set to 5 hp");
		}
		// two lethal hits a few ticks apart: it should double pop
		if (ticks == 495) {
			var inv = bot.getInventory();
			bot.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(Items.TOTEM_OF_UNDYING));
			inv.setItem(1, new net.minecraft.world.item.ItemStack(Items.TOTEM_OF_UNDYING));
		}
		if (ticks == 500) {
			bot.setHealth(2f);
			bot.invulnerableTime = 0;
			bot.hurtServer(level, bot.damageSources().generic(), 200f);
			GladeBot.LOGGER.info("[gladebot] lethal hit 1");
		}
		if (ticks == 510 && state == State.FIGHT) {
			bot.invulnerableTime = 0;
			bot.hurtServer(level, bot.damageSources().generic(), 200f);
			GladeBot.LOGGER.info("[gladebot] lethal hit 2");
		}
		// worn armour and room to mend: it should mend every piece back up
		if (ticks == 800) {
			for (var slot : new net.minecraft.world.entity.EquipmentSlot[] {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
					net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
				var piece = bot.getItemBySlot(slot);
				piece.setDamageValue((int) (piece.getMaxDamage() * 0.6));
			}
			bot.setHealth(bot.getMaxHealth());
			dummy.brain.pause(200);
			dummy.teleportTo(level, bot.getX() + 9, bot.getY(), bot.getZ(), java.util.Set.of(), 0, 0, true);
			GladeBot.LOGGER.info("[gladebot] armour worn to 40%: {}", bot.brain.armorReport());
		}
		if (ticks == 1000) GladeBot.LOGGER.info("[gladebot] armour after mending: {}", bot.brain.armorReport());
		if (ticks >= 2400) end(level.getServer(), "draw");
	}

	/**
	 * Called before a player in the match dies. A totem in either hand saves
	 * them as usual; otherwise nobody dies: the match ends and they're healed.
	 */
	public static boolean allowDeath(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (state != State.FIGHT && state != State.COUNTDOWN) return true;
		boolean isBot = entity == bot;
		boolean isPlayer = entity instanceof ServerPlayer sp && sp.getUUID().equals(playerId);
		if (!isBot && !isPlayer) return true;
		GladeBot.LOGGER.info("[gladebot] {} took a fatal hit ({}) during {}", entity.getName().getString(), source.getMsgId(), state);
		if (state == State.COUNTDOWN) {
			// nothing counts before "Fight!"
			entity.setHealth(entity.getMaxHealth());
			return false;
		}
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
		GladeBot.LOGGER.info("[gladebot] result winner={} {}", who, brain.stats());
		if (dummy != null && dummy.brain != null) {
			GladeBot.LOGGER.info("[gladebot] sparring {}", dummy.brain.stats());
			dummy.brain.stop();
		}
		boolean won = who.equals("you");
		title(player, Component.literal(won ? "You win!" : "GladeBot wins").withStyle(won ? ChatFormatting.GREEN : ChatFormatting.RED));
		player.sendSystemMessage(Component.literal("GladeBot (" + Settings.tier.label + ", " + Settings.fight() + ") landed " + brain.hits + " hits (" + brain.crits
				+ " crits) and threw " + brain.pots + " pots."));
		player.sendSystemMessage(Component.literal("Armour durability: you " + Math.round(armor(player) * 100) + "% · GladeBot " + Math.round(armor(bot) * 100) + "%"));
	}

	/** A player's armour, average durability left, 0..1. */
	public static float armor(net.minecraft.world.entity.LivingEntity p) {
		float sum = 0;
		int n = 0;
		for (var slot : new net.minecraft.world.entity.EquipmentSlot[] {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
				net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
			var s = p.getItemBySlot(slot);
			if (s.isEmpty() || s.getMaxDamage() <= 0) continue;
			sum += 1 - s.getDamageValue() / (float) s.getMaxDamage();
			n++;
		}
		return n == 0 ? 1 : sum / n;
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
