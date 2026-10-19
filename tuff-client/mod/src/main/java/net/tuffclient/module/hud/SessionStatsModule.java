package net.tuffclient.module.hud;

import net.tuffclient.gui.Gfx;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.tuffclient.setting.BoolSetting;

/** Play time, blocks mined, kills, deaths and distance walked this session. */
public final class SessionStatsModule extends HudModule {
	private static SessionStatsModule instance;
	private final BoolSetting showTime = add(new BoolSetting("time", "Play time", true));
	private final BoolSetting showBlocks = add(new BoolSetting("blocks", "Blocks mined", true));
	private final BoolSetting showKills = add(new BoolSetting("kills", "Kills", true));
	private final BoolSetting showDeaths = add(new BoolSetting("deaths", "Deaths", true));
	private final BoolSetting showDistance = add(new BoolSetting("distance", "Distance walked", true));
	private final BoolSetting perWorld = add(new BoolSetting("per_world", "Reset when joining a world", true));

	private long ticks;
	private int blocks;
	private int kills;
	private int deaths;
	private double distance;
	private double lastX = Double.NaN, lastZ;
	private boolean wasDead;
	private Object world;
	/** The entity last hit, and when: it counts as a kill if it dies soon after. */
	private Entity lastHit;
	private long lastHitTick;

	public SessionStatsModule() {
		super("session_stats", "Session Stats", "Play time, blocks mined, kills, deaths and distance this session.", 0.885, 0.22, false);
		instance = this;
	}

	/** Called when the player breaks a block (see MultiPlayerGameModeMixin). */
	public static void onBlockBroken() {
		if (instance != null) instance.blocks++;
	}

	/** Called when the player attacks an entity. */
	public static void onAttack(Entity target) {
		if (instance != null && target instanceof LivingEntity) {
			instance.lastHit = target;
			instance.lastHitTick = instance.ticks;
		}
	}

	@Override
	public void tick(Minecraft client) {
		LocalPlayer p = client.player;
		if (p == null) {
			lastX = Double.NaN;
			return;
		}
		if (client.level != world) {
			world = client.level;
			if (perWorld.get()) reset();
		}
		ticks++;
		if (!Double.isNaN(lastX)) {
			double d = Math.hypot(p.getX() - lastX, p.getZ() - lastZ);
			if (d < 10) distance += d; // skip teleports
		}
		lastX = p.getX();
		lastZ = p.getZ();
		boolean dead = p.isDeadOrDying();
		if (dead && !wasDead) deaths++;
		wasDead = dead;
		if (lastHit != null) {
			if (lastHit instanceof LivingEntity l && l.isDeadOrDying()) {
				kills++;
				lastHit = null;
			} else if (ticks - lastHitTick > 100 || lastHit.isRemoved()) {
				lastHit = null;
			}
		}
	}

	private void reset() {
		ticks = 0;
		blocks = kills = deaths = 0;
		distance = 0;
		lastHit = null;
	}

	@Override
	public void render(Gfx g, Font font, boolean preview) {
		List<String[]> lines = new ArrayList<>();
		long secs = ticks / 20;
		if (showTime.get()) lines.add(new String[] {"Time ", secs >= 3600 ? secs / 3600 + "h " + secs / 60 % 60 + "m" : secs / 60 + "m " + secs % 60 + "s"});
		if (showBlocks.get()) lines.add(new String[] {"Mined ", String.valueOf(blocks)});
		if (showKills.get()) lines.add(new String[] {"Kills ", String.valueOf(kills)});
		if (showDeaths.get()) lines.add(new String[] {"Deaths ", String.valueOf(deaths)});
		if (showDistance.get()) lines.add(new String[] {"Walked ", distance >= 1000 ? String.format("%.2f km", distance / 1000) : Math.round(distance) + " m"});
		if (lines.isEmpty()) lines.add(new String[] {"", "-"});
		int p = pad(), w = 0;
		for (String[] l : lines) w = Math.max(w, font.width(l[0]) + font.width(l[1]));
		box(g, w + p * 2, linesHeight(lines.size()));
		int y = p;
		for (String[] l : lines) {
			label(g, font, l[0], p, y);
			value(g, font, l[1], p + font.width(l[0]), y);
			y += 10;
		}
	}
}
