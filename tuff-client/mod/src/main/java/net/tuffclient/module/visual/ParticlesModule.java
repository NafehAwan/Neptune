package net.tuffclient.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.tuffclient.TuffClient;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.NumberSetting;

/** More (or fewer) hit particles: crits on every hit, sharpness sparkles, a multiplier. */
public final class ParticlesModule extends Module {
	private final NumberSetting multiplier = add(new NumberSetting("multiplier", "Hit particle multiplier", 2, 1, 6, 1));
	private final BoolSetting alwaysCrit = add(new BoolSetting("always_crit", "Crit particles on every hit", true));
	private final BoolSetting sharpness = add(new BoolSetting("sharpness", "Sparkles on every hit", false));

	public ParticlesModule() {
		super("particles", "Particles", "Extra hit particles: crits on every hit and a multiplier.", Category.VISUAL, false);
	}

	/** Called when the player attacks an entity (see MultiPlayerGameModeMixin). */
	public static void onAttack(Entity target) {
		ParticlesModule m = TuffClient.modules().particles;
		if (!m.isEnabled() || !(target instanceof LivingEntity)) return;
		var engine = Minecraft.getInstance().particleEngine;
		int n = m.multiplier.get().intValue();
		for (int i = 0; i < n; i++) {
			if (m.alwaysCrit.get()) engine.createTrackingEmitter(target, ParticleTypes.CRIT);
			if (m.sharpness.get()) engine.createTrackingEmitter(target, ParticleTypes.ENCHANTED_HIT);
		}
	}
}
