// glade: minecraft >=26.3
package net.tuffclient.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity Distance: skips entities past the chosen distance. */
@Mixin(EntityRenderer.class)
public abstract class EntityDistanceMixin {
	@Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
	private void glade$distance(Entity entity, Frustum frustum, double x, double y, double z, float partialTick, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && TuffClient.modules() != null && TuffClient.modules().entityDistance.tooFar(entity)) cir.setReturnValue(false);
	}
}
