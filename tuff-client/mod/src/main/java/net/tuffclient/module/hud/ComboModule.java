package net.tuffclient.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Hits in a row without being hit back; resets when you take damage or stop for 3 s. */
public final class ComboModule extends TextHudModule {
	private int combo;
	private long last;
	private int lastHurt;

	public ComboModule() {
		super("combo", "Combo", "Hits in a row before you get hit back.", "Combo", 0.006, 0.21, false);
	}

	public void onAttack(Entity target) {
		if (!(target instanceof LivingEntity)) return;
		long now = System.currentTimeMillis();
		combo = now - last > 3000 ? 1 : combo + 1;
		last = now;
	}

	@Override
	public void tick(Minecraft client) {
		if (client.player == null) return;
		int hurt = client.player.hurtTime;
		if (hurt > lastHurt) combo = 0;
		lastHurt = hurt;
		if (System.currentTimeMillis() - last > 3000) combo = 0;
	}

	@Override
	protected String value(Minecraft client, boolean preview) {
		return preview && combo == 0 ? "4" : String.valueOf(combo);
	}
}
