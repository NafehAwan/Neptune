package net.gladebot;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Aim trainer, Tracking: a target strafes in front of you and you keep your
 * crosshair on it. Accuracy is the share of ticks your crosshair is on its
 * hitbox; the action bar shows it live, and the result (with your longest
 * streak and your best per difficulty) comes at the end.
 */
public final class AimTrainer {
	public enum Level {
		//               label     dir change (ticks)  jump chance/tick  speed level  forward jitter
		EASY("Easy", 40, 70, 0f, -1, 0f),
		MEDIUM("Medium", 14, 32, 0.03f, 0, 0.2f),
		HARD("Hard", 6, 20, 0.08f, 1, 0.5f);

		public final String label;
		final int minDir, maxDir, speed;
		final float jump, jitter;

		Level(String label, int minDir, int maxDir, float jump, int speed, float jitter) {
			this.label = label;
			this.minDir = minDir;
			this.maxDir = maxDir;
			this.jump = jump;
			this.speed = speed;
			this.jitter = jitter;
		}

		public static Level parse(String s) {
			for (Level l : values()) if (l.name().equalsIgnoreCase(s)) return l;
			return null;
		}
	}

	private static final int COUNTDOWN = 60;
	private static boolean active;
	private static UUID playerId;
	private static BotPlayer target, aimer;
	private static Level level;
	private static int ticks, duration, onTicks, streak, bestStreak;

	private AimTrainer() {
	}

	public static boolean active() {
		return active;
	}

	public static String start(ServerPlayer player, Level lvl, int seconds) {
		ServerLevel world = (ServerLevel) player.level();
		MinecraftServer server = world.getServer();
		stop(server);
		Match.stop(server);
		Vec3 ahead = Vec3.directionFromRotation(0, player.getYRot()).scale(3.5);
		Vec3 pos = Match.ground(world, player.position().add(ahead), player.getY());
		target = BotPlayer.spawn(server, world, pos, player.getYRot() + 180, "Target");
		target.setInvulnerable(true);
		target.mover = new Mover(target, player, lvl);
		if (lvl.speed >= 0) target.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 600, lvl.speed, false, false));
		playerId = player.getUUID();
		level = lvl;
		duration = seconds * 20;
		ticks = onTicks = streak = bestStreak = 0;
		active = true;
		return "Tracking (" + lvl.label + ", " + seconds + "s): keep your crosshair on the target!";
	}

	/** CI: a bot that aims perfectly at the target should score near 100%, and the target should really move. */
	public static String selfTest(MinecraftServer server) {
		ServerLevel world = server.overworld();
		world.getChunk(0, 0);
		int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
		BotPlayer a = BotPlayer.spawn(server, world, new Vec3(20.5, y, 0.5), 0, "Aimer");
		a.setInvulnerable(true);
		String msg = start(a, Level.HARD, 10);
		aimer = a;
		return msg;
	}

	public static void stop(MinecraftServer server) {
		if (target != null && !target.isRemoved()) target.remove();
		if (aimer != null && !aimer.isRemoved()) aimer.remove();
		target = null;
		aimer = null;
		active = false;
		playerId = null;
	}

	public static void tick(MinecraftServer server) {
		if (!active) return;
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player == null || target == null || target.isRemoved()) {
			stop(server);
			return;
		}
		ticks++;
		if (ticks <= COUNTDOWN) {
			if (ticks % 20 == 1) Match.title(player, Component.literal(String.valueOf((COUNTDOWN - ticks + 1) / 20 + 1)).withStyle(ChatFormatting.WHITE));
			if (ticks == COUNTDOWN) Match.title(player, Component.literal("Track!").withStyle(ChatFormatting.GREEN));
			return;
		}
		int t = ticks - COUNTDOWN;
		if (player == aimer) lookAt(player, target.getBoundingBox().getCenter());
		if (onTarget(player)) {
			onTicks++;
			streak++;
			bestStreak = Math.max(bestStreak, streak);
		} else {
			streak = 0;
		}
		if (t % 5 == 0) {
			int pct = Math.round(100f * onTicks / t);
			int left = (duration - t + 19) / 20;
			player.displayClientMessage(Component.literal("Tracking " + pct + "%  ·  " + left + "s left").withStyle(pct >= 70 ? ChatFormatting.GREEN : pct >= 40 ? ChatFormatting.YELLOW : ChatFormatting.RED), true);
		}
		if (t >= duration) finish(server, player);
	}

	private static boolean onTarget(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(6));
		return target.getBoundingBox().inflate(0.1).clip(eye, end).isPresent();
	}

	private static void lookAt(ServerPlayer p, Vec3 point) {
		Vec3 eye = p.getEyePosition();
		double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
		p.setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f);
		p.setXRot((float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
	}

	private static void finish(MinecraftServer server, ServerPlayer player) {
		int pct = Math.round(100f * onTicks / duration);
		int best = Settings.bestTracking[level.ordinal()];
		boolean record = pct > best && player != aimer;
		if (record) {
			Settings.bestTracking[level.ordinal()] = pct;
			Settings.save();
		}
		Match.title(player, Component.literal("Tracking " + pct + "%").withStyle(pct >= 70 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		player.sendSystemMessage(Component.literal("Tracking (" + level.label + "): " + pct + "% on target, longest streak " + String.format("%.1f", bestStreak / 20f) + "s"
				+ (record ? "  New best!" : "  Best: " + best + "%")));
		GladeBot.LOGGER.info("[gladebot] aim result accuracy={} moved={} streak={}", pct, Math.round(target.mover.moved), bestStreak);
		stop(server);
	}

	/** Moves the target: strafes across in front of you, changing direction at random, keeping about 3 blocks away. */
	static final class Mover {
		private final BotPlayer bot;
		private final ServerPlayer player;
		private final Level lvl;
		private final RandomSource random;
		private int dirTicks;
		private float dir = 1;
		private Vec3 last;
		/** Blocks moved (the self test checks the target really moves). */
		double moved;

		Mover(BotPlayer bot, ServerPlayer player, Level lvl) {
			this.bot = bot;
			this.player = player;
			this.lvl = lvl;
			this.random = bot.getRandom();
		}

		void tick() {
			if (!active || ticks <= COUNTDOWN) {
				bot.zza = bot.xxa = 0;
				return;
			}
			Vec3 pos = bot.position();
			if (last != null) moved += pos.subtract(last).horizontalDistance();
			last = pos;
			// face the player
			Vec3 eye = bot.getEyePosition(), to = player.getEyePosition();
			double dx = to.x - eye.x, dz = to.z - eye.z;
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
			bot.setYRot(yaw);
			bot.setYHeadRot(yaw);
			if (--dirTicks <= 0) {
				dir = -dir;
				dirTicks = lvl.minDir + random.nextInt(Math.max(1, lvl.maxDir - lvl.minDir));
			}
			double dist = bot.distanceTo(player);
			float forward = (float) Mth.clamp((dist - 3.2) * 0.8, -1, 1) + (random.nextFloat() - 0.5f) * lvl.jitter;
			bot.zza = forward;
			bot.xxa = dir;
			bot.setSprinting(false);
			bot.setJumping(bot.onGround() && random.nextFloat() < lvl.jump);
		}
	}
}
