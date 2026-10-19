package net.tuffclient.legacy.module;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.tuffclient.legacy.hud.*;
import net.tuffclient.legacy.util.FreelookModule;
import net.tuffclient.legacy.util.SimpleModules;
import net.tuffclient.legacy.util.ZoomModule;

/** Every Glade module for 1.8.9, in menu order. */
public final class ModuleManager {
	private final List<Module> modules = new ArrayList<Module>();
	public final ZoomModule zoom = add(new ZoomModule());
	public final FreelookModule freelook = add(new FreelookModule());
	public final SimpleModules.HideScoreboard hideScoreboard;

	public ModuleManager() {
		add(new FpsHud());
		add(new CpsHud());
		add(new KeystrokesHud());
		add(new CoordinatesHud());
		add(new ArmorHud());
		add(new EffectsHud());
		add(new ClockHud());
		add(new PingHud());
		add(new SpeedHud());
		add(new MemoryHud());
		add(new SimpleModules.ToggleSprint());
		add(new SimpleModules.Fullbright());
		add(new SimpleModules.TimeChanger());
		hideScoreboard = add(new SimpleModules.HideScoreboard());
	}

	private <M extends Module> M add(M m) {
		modules.add(m);
		return m;
	}

	public List<Module> all() {
		return modules;
	}

	public List<HudModule> hud() {
		List<HudModule> list = new ArrayList<HudModule>();
		for (Module m : modules) if (m instanceof HudModule) list.add((HudModule) m);
		return list;
	}

	public void tick(MinecraftClient client) {
		for (Module m : modules) if (m.isEnabled()) m.tick(client);
	}

	public void renderHud(MinecraftClient mc) {
		if (mc.options.hudHidden || mc.options.debugEnabled) return;
		Window window = new Window(mc);
		for (HudModule h : hud()) if (h.isEnabled()) h.render(window, false);
	}
}
