package net.tuffclient;

import net.tuffclient.gui.Gfx;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.tuffclient.module.Module;
import net.tuffclient.module.hud.ArmorModule;
import net.tuffclient.module.hud.ClockModule;
import net.tuffclient.module.hud.CoordinatesModule;
import net.tuffclient.module.hud.CpsModule;
import net.tuffclient.module.hud.EffectsModule;
import net.tuffclient.module.hud.FpsModule;
import net.tuffclient.module.hud.HudModule;
import net.tuffclient.module.hud.KeystrokesModule;
import net.tuffclient.module.hud.MemoryModule;
import net.tuffclient.module.hud.PingModule;
import net.tuffclient.module.hud.ServerModule;
import net.tuffclient.module.hud.SpeedModule;
import net.tuffclient.module.hud.BiomeLightModule;
import net.tuffclient.module.hud.CompassModule;
import net.tuffclient.module.hud.DayTimeModule;
import net.tuffclient.module.hud.DurabilityModule;
import net.tuffclient.module.hud.ItemCounterModule;
import net.tuffclient.module.hud.PackDisplayModule;
import net.tuffclient.module.hud.SessionStatsModule;
import net.tuffclient.module.util.AutoGGModule;
import net.tuffclient.module.util.AutoReconnectModule;
import net.tuffclient.module.util.ChatPlusModule;
import net.tuffclient.module.util.CleanF3Module;
import net.tuffclient.module.util.CrosshairModule;
import net.tuffclient.module.util.FreelookModule;
import net.tuffclient.module.util.ScreenshotToolsModule;
import net.tuffclient.module.util.WaypointsModule;
import net.tuffclient.module.visual.BlockOutlineModule;
import net.tuffclient.module.visual.CosmeticsModule;
import net.tuffclient.module.visual.CustomSkyModule;
import net.tuffclient.module.visual.HitColorModule;
import net.tuffclient.module.visual.NametagsModule;
import net.tuffclient.module.visual.ParticlesModule;
import net.tuffclient.module.util.FullbrightModule;
import net.tuffclient.module.visual.ScoreboardModule;
import net.tuffclient.module.util.InterfaceModule;
import net.tuffclient.module.util.TitleScreenModule;
import net.tuffclient.module.util.ToggleSprintModule;
import net.tuffclient.module.util.ZoomModule;

public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	private final List<HudModule> hud = new ArrayList<>();

	public final InterfaceModule ui = add(new InterfaceModule());
	public final TitleScreenModule titleScreen = add(new TitleScreenModule());
	public final ZoomModule zoom = add(new ZoomModule());
	public final FullbrightModule fullbright = add(new FullbrightModule());
	public final CrosshairModule crosshair = add(new CrosshairModule());
	public final ScoreboardModule scoreboard = add(new ScoreboardModule());
	public final net.tuffclient.module.visual.MenuBackgroundModule menuBackground = add(new net.tuffclient.module.visual.MenuBackgroundModule());
	public final net.tuffclient.module.util.ClipsModule clips = add(new net.tuffclient.module.util.ClipsModule());
	public final net.tuffclient.module.hud.ReachModule reach = add(new net.tuffclient.module.hud.ReachModule());
	public final net.tuffclient.module.hud.ComboModule combo = add(new net.tuffclient.module.hud.ComboModule());
	public final net.tuffclient.module.hud.HudProfilesModule hudProfiles = add(new net.tuffclient.module.hud.HudProfilesModule());
	public final net.tuffclient.module.visual.EntityDistanceModule entityDistance = add(new net.tuffclient.module.visual.EntityDistanceModule());
	public final net.tuffclient.module.visual.TabPingModule tabPing = add(new net.tuffclient.module.visual.TabPingModule());
	public final net.tuffclient.module.visual.TiersModule tiers = add(new net.tuffclient.module.visual.TiersModule());
	public final WaypointsModule waypoints = add(new WaypointsModule());
	public final FreelookModule freelook = add(new FreelookModule());
	public final ChatPlusModule chatPlus = add(new ChatPlusModule());
	public final AutoGGModule autoGG = add(new AutoGGModule());
	public final AutoReconnectModule autoReconnect = add(new AutoReconnectModule());
	public final CleanF3Module cleanF3 = add(new CleanF3Module());
	public final ScreenshotToolsModule screenshotTools = add(new ScreenshotToolsModule());
	public final HitColorModule hitColor = add(new HitColorModule());
	public final BlockOutlineModule blockOutline = add(new BlockOutlineModule());
	public final ParticlesModule particles = add(new ParticlesModule());
	public final CustomSkyModule customSky = add(new CustomSkyModule());
	public final NametagsModule nametags = add(new NametagsModule());
	public final CosmeticsModule cosmetics = add(new CosmeticsModule());
	public final net.tuffclient.module.visual.HitSoundsModule hitSounds = add(new net.tuffclient.module.visual.HitSoundsModule());
	public final net.tuffclient.module.visual.ItemPhysicsModule itemPhysics = add(new net.tuffclient.module.visual.ItemPhysicsModule());
	public final net.tuffclient.module.visual.OldAnimationsModule oldAnimations = add(new net.tuffclient.module.visual.OldAnimationsModule());
	public final net.tuffclient.module.visual.LowFireModule lowFire = add(new net.tuffclient.module.visual.LowFireModule());
	public final net.tuffclient.module.visual.SmallShieldModule smallShield = add(new net.tuffclient.module.visual.SmallShieldModule());
	public final net.tuffclient.module.visual.HandViewModule handView = add(new net.tuffclient.module.visual.HandViewModule());
	public final net.tuffclient.module.visual.TimeWeatherModule timeWeather = add(new net.tuffclient.module.visual.TimeWeatherModule());

	public ModuleManager() {
		add(new ToggleSprintModule());
		add(new FpsModule());
		add(new CpsModule());
		add(new KeystrokesModule());
		add(new ArmorModule());
		add(new CoordinatesModule());
		add(new PingModule());
		add(new EffectsModule());
		add(new ClockModule());
		add(new ServerModule());
		add(new SpeedModule());
		add(new MemoryModule());
		add(new CompassModule());
		add(new BiomeLightModule());
		add(new ItemCounterModule());
		add(new DurabilityModule());
		add(new SessionStatsModule());
		add(new PackDisplayModule());
		add(new DayTimeModule());
	}

	private <M extends Module> M add(M module) {
		modules.add(module);
		if (module instanceof HudModule h) hud.add(h);
		return module;
	}

	public List<Module> all() {
		return modules;
	}

	public List<HudModule> hud() {
		return hud;
	}

	/** Modules shown as cards in the menu. */
	public List<Module> visible() {
		return modules.stream().filter(m -> !m.hidden()).toList();
	}

	public void tick(Minecraft client) {
		for (Module m : modules) {
			if (m.isEnabled()) m.tick(client);
		}
	}

	/** Screen-space top-left of a HUD module, kept fully on screen. */
	public static int[] position(HudModule m, int screenW, int screenH) {
		double s = m.scale.get();
		int w = (int) Math.ceil(m.width() * s);
		int h = (int) Math.ceil(m.height() * s);
		int px = (int) Math.round(m.x * screenW);
		int py = (int) Math.round(m.y * screenH);
		px = Math.max(0, Math.min(screenW - w, px));
		py = Math.max(0, Math.min(screenH - h, py));
		return new int[] {px, py, w, h};
	}

	public static void renderModule(Gfx g, Font font, HudModule m, boolean preview) {
		int[] p = position(m, g.guiWidth(), g.guiHeight());
		float s = m.scale.get().floatValue();
		g.push();
		g.translate(p[0], p[1]);
		g.scale(s);
		m.render(g, font, preview);
		g.pop();
	}

	public void renderHud(Gfx g) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		if (!net.tuffclient.Mc.hudHidden()) {
			if (waypoints.isEnabled()) waypoints.render(g);
			for (HudModule m : hud) {
				if (m.isEnabled()) renderModule(g, client.font, m, false);
			}
			if (screenshotTools.isEnabled()) screenshotTools.render(g);
		}
		// F3 goes over the HUD, like the vanilla debug screen
		if (cleanF3.isEnabled()) cleanF3.render(g);
	}
}
