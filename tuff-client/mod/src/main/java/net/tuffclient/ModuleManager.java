package net.tuffclient;

import net.tuffclient.gui.Gfx;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.tuffclient.module.Module;
import net.tuffclient.module.hud.ArmorModule;
import net.tuffclient.module.hud.DayTimeModule;
import net.tuffclient.module.hud.FpsModule;
import net.tuffclient.module.hud.HudModule;
import net.tuffclient.module.hud.KeystrokesModule;
import net.tuffclient.module.hud.PingModule;
import net.tuffclient.module.hud.ReachModule;
import net.tuffclient.module.util.CrosshairModule;
import net.tuffclient.module.util.FreelookModule;
import net.tuffclient.module.util.FullbrightModule;
import net.tuffclient.module.visual.BlockOutlineModule;
import net.tuffclient.module.visual.ClearGlassModule;
import net.tuffclient.module.visual.HandViewModule;
import net.tuffclient.module.visual.HitColorModule;
import net.tuffclient.module.visual.HitboxesModule;
import net.tuffclient.module.visual.ItemPhysicsModule;
import net.tuffclient.module.visual.LowFireModule;
import net.tuffclient.module.visual.MenuBackgroundModule;
import net.tuffclient.module.visual.MinimalBobbingModule;
import net.tuffclient.module.visual.ParticlesModule;
import net.tuffclient.module.visual.SmallShieldModule;
import net.tuffclient.module.visual.TiersModule;

public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	private final List<HudModule> hud = new ArrayList<>();

	// HUD
	public final FpsModule fps = add(new FpsModule());
	public final KeystrokesModule keystrokes = add(new KeystrokesModule());
	public final ArmorModule armor = add(new ArmorModule());
	public final PingModule ping = add(new PingModule());
	public final DayTimeModule dayTime = add(new DayTimeModule());
	public final ReachModule reach = add(new ReachModule());
	// PvP and visuals
	public final CrosshairModule crosshair = add(new CrosshairModule());
	public final HitboxesModule hitboxes = add(new HitboxesModule());
	public final HitColorModule hitColor = add(new HitColorModule());
	public final BlockOutlineModule blockOutline = add(new BlockOutlineModule());
	public final ParticlesModule particles = add(new ParticlesModule());
	public final SmallShieldModule smallShield = add(new SmallShieldModule());
	public final HandViewModule handView = add(new HandViewModule());
	public final LowFireModule lowFire = add(new LowFireModule());
	public final MinimalBobbingModule minimalBobbing = add(new MinimalBobbingModule());
	public final ClearGlassModule clearGlass = add(new ClearGlassModule());
	public final ItemPhysicsModule itemPhysics = add(new ItemPhysicsModule());
	public final TiersModule tiers = add(new TiersModule());
	public final MenuBackgroundModule menuBackground = add(new MenuBackgroundModule());
	public final FullbrightModule fullbright = add(new FullbrightModule());
	// Utility
	public final FreelookModule freelook = add(new FreelookModule());
	public final net.tuffclient.module.util.KeybindsModule keybinds = add(new net.tuffclient.module.util.KeybindsModule());

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
		// the HUD layout editor draws the elements itself
		if (client.player == null || net.tuffclient.Mc.hudHidden() || net.tuffclient.gui.HudLayoutScreen.open()) return;
		for (HudModule m : hud) {
			if (m.isEnabled()) renderModule(g, client.font, m, false);
		}
	}
}
