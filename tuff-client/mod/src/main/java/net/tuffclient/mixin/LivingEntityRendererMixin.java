// glade: minecraft >=1.21.2
package net.tuffclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nametags: your own in third person, or none at all. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true)
	private void glade$nametag(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().nametags;
		if (m == null || !m.isEnabled()) return;
		Minecraft mc = Minecraft.getInstance();
		if (m.hideAll.get()) cir.setReturnValue(false);
		else if (m.own.get() && entity == mc.player && !mc.options.getCameraType().isFirstPerson() && !net.tuffclient.Mc.hudHidden()) cir.setReturnValue(true);
	}
}
