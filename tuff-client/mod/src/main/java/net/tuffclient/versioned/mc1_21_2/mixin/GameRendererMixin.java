// glade: minecraft >=1.21.2 <26.1
package net.tuffclient.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: the world's field of view (not the hand's). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void glade$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (!useFovSetting || TuffClient.modules() == null) return;
		float multiplier = TuffClient.modules().zoom.fovMultiplier();
		if (multiplier < 0.999f) cir.setReturnValue(cir.getReturnValueF() * multiplier);
	}
}
