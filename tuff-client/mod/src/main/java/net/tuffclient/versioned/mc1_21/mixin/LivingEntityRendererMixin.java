// glade: minecraft <1.21.2
package net.tuffclient.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nametags: your own in third person, none at all, or none under a server's own nametags. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("RETURN"), cancellable = true)
	private void glade$nametag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		var modules = TuffClient.modules();
		if (modules == null) return;
		boolean vanilla = cir.getReturnValue();
		boolean shown = modules.nametags.show(entity, vanilla);
		if (shown != vanilla) cir.setReturnValue(shown);
	}
}
