package net.tuffclient.mixin;

import net.minecraft.client.Camera;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the zoom module's field-of-view multiplier. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void tuff$zoom(CallbackInfoReturnable<Float> cir) {
		if (TuffClient.modules() == null) return;
		float multiplier = TuffClient.modules().zoom.fovMultiplier();
		if (multiplier < 0.999f) cir.setReturnValue(cir.getReturnValueF() * multiplier);
	}
}
