package net.tuffclient.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.tuffclient.TuffClient;
import net.tuffclient.module.hud.SessionStatsModule;
import net.tuffclient.module.visual.ParticlesModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Session Stats counts blocks and kills, and Particles adds hit particles. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("TAIL"))
	private void glade$attack(Player player, Entity target, CallbackInfo ci) {
		if (TuffClient.modules() == null) return;
		SessionStatsModule.onAttack(target);
		ParticlesModule.onAttack(target);
		net.tuffclient.module.visual.HitSoundsModule.onAttack(target);
	}

	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void glade$broke(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) SessionStatsModule.onBlockBroken();
	}
}
