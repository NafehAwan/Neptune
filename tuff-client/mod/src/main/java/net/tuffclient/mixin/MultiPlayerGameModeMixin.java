package net.tuffclient.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.tuffclient.TuffClient;
import net.tuffclient.module.visual.ParticlesModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reach measures your hit and Particles adds hit particles. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("TAIL"))
	private void glade$attack(Player player, Entity target, CallbackInfo ci) {
		if (TuffClient.modules() == null) return;
		ParticlesModule.onAttack(target);
		TuffClient.modules().reach.onAttack(player, target);
	}

}
