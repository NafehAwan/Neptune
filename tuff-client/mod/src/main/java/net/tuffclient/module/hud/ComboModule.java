package net.tuffclient.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Hits in a row without being hit back; resets when you take damage or stop for 3 s. */
public final class ComboModule extends TextHudModule {
	private int combo;
	private long last;
	private int lastHurt;

	private final net.tuffclient.setting.BoolSetting hideZero = add(new net.tuffclient.setting.BoolSetting("hide_zero", "Hide when there's no combo", false));
	private final net.tuffclient.setting.NumberSetting resetAfter = add(new net.tuffclient.setting.NumberSetting("reset", "Reset after (seconds)", 3, 1, 10, 1));

	public ComboModule() {
		super("combo", "Combo", "Hits in a row before you get hit back.", "Combo", 0.006, 0.21, false);
	}

	public void onAttack(Entity target) {
		if (!(target instanceof LivingEntity)) return;
		long now = System.currentTimeMillis();
		combo = now - last > resetAfter.get() * 1000 ? 1 : combo + 1;
		last = now;
	}

	@Override
	public void tick(Minecraft client) {
		if (client.player == null) return;
		int hurt = client.player.hurtTime;
		if (hurt > lastHurt) combo = 0;
		lastHurt = hurt;
		if (System.currentTimeMillis() - last > resetAfter.get() * 1000) combo = 0;
	}

	@Override
	protected String value(Minecraft client, boolean preview) {
		if (preview && combo == 0) return "4";
		return combo == 0 && hideZero.get() ? null : String.valueOf(combo);
	}
}
