package net.tuffclient.module.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.tuffclient.TuffClient;
import net.tuffclient.compat.Messages;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;
import net.tuffclient.setting.KeybindSetting;
import net.tuffclient.setting.NumberSetting;

/**
 * Clips: the Glade launcher records the game window in the background (like a
 * screen recorder, in its own low-priority process so the game keeps its FPS)
 * and keeps the last moments. When you kill a player (or die, or press the
 * clip key) it saves them as a video. The game tells the launcher through
 * "glade-event" lines in its log.
 */
public final class ClipsModule extends Module {
	public final KeybindSetting key = add(new KeybindSetting("key", "Save clip key", TuffClient::clipKey));
	public final BoolSetting onKill = add(new BoolSetting("on_kill", "Save when you kill a player", true));
	public final BoolSetting onDeath = add(new BoolSetting("on_death", "Save when you die", false));
	public final NumberSetting length = add(new NumberSetting("length", "Clip length (seconds)", 15, 5, 60, 5));
	public final NumberSetting after = add(new NumberSetting("after", "Keep recording after the kill (seconds)", 2, 0, 5, 1));
	public final ChoiceSetting fps = add(new ChoiceSetting("fps", "Frame rate", 1, "30 FPS", "60 FPS"));
	public final ChoiceSetting resolution = add(new ChoiceSetting("resolution", "Resolution", 0, "Window", "1080p", "720p"));
	public final ChoiceSetting quality = add(new ChoiceSetting("quality", "Quality", 1, "Light", "Balanced", "Best"));
	public final BoolSetting audio = add(new BoolSetting("audio", "Record game sound", true));
	public final BoolSetting cursor = add(new BoolSetting("cursor", "Show the mouse cursor", false));

	private String sentConfig = "";
	private Player lastHit;
	private int lastHitAge;
	private boolean wasDead;
	private static String sentPresence = "";

	public ClipsModule() {
		super("clips", "Clips", "Saves the last seconds as a video when you kill a player, like a screen recorder. Needs ffmpeg.", Category.UTILITY, false);
	}

	/** Called when you hit something (see MultiPlayerGameModeMixin). */
	public void onAttack(Entity target) {
		if (target instanceof Player p) {
			lastHit = p;
			lastHitAge = 0;
		}
	}

	/** Every tick, on or off (Glade's tick calls it; disabled modules don't tick). */
	public void update(Minecraft client) {
		// tell the launcher how to record whenever it changes (and when this turns off)
		String config = isEnabled()
				? String.format("on len=%d after=%d fps=%d res=%s q=%s audio=%d cursor=%d", length.get().intValue(), after.get().intValue(),
						fps.get() == 0 ? 30 : 60, new String[] {"window", "1080", "720"}[resolution.get()], new String[] {"light", "balanced", "best"}[quality.get()],
						audio.get() ? 1 : 0, cursor.get() ? 1 : 0)
				: "off";
		if (!config.equals(sentConfig)) {
			sentConfig = config;
			event("clips-config", config);
		}
		LocalPlayer p = client.player;
		if (!isEnabled() || p == null) return;
		if (lastHit != null) {
			if (lastHit.isDeadOrDying()) {
				if (onKill.get()) save("kill", lastHit.getName().getString());
				lastHit = null;
			} else if (++lastHitAge > 100 || lastHit.isRemoved()) {
				lastHit = null;
			}
		}
		boolean dead = p.isDeadOrDying();
		if (dead && !wasDead && onDeath.get()) save("death", "");
		wasDead = dead;
	}

	/** Asks the launcher to save a clip (kill, death or key). */
	public void save(String reason, String detail) {
		if (!isEnabled()) {
			LocalPlayer p = Minecraft.getInstance().player;
			if (p != null && reason.equals("key")) Messages.overlay(p, Component.literal("Turn on Clips in the Glade menu to save clips"));
			return;
		}
		event("clip", reason + (detail.isEmpty() ? "" : " " + detail.replaceAll("[^A-Za-z0-9_]", "")));
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null) Messages.overlay(p, Component.literal("● Saving clip (" + length.get().intValue() + " s)"));
	}

	/** Discord: where you are playing, shown by the launcher. */
	public static void presence(Minecraft client) {
		String where;
		if (client.level == null) where = "In the menus";
		else if (client.hasSingleplayerServer()) where = "Singleplayer";
		else if (client.getCurrentServer() != null) where = "On " + client.getCurrentServer().ip;
		else where = "Multiplayer";
		if (!where.equals(sentPresence)) {
			sentPresence = where;
			event("presence", where);
		}
	}

	private static void event(String kind, String data) {
		TuffClient.LOGGER.info("glade-event {} {}", kind, data);
	}
}
