package net.tuffclient.mixin;

import net.minecraft.client.Camera;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Freelook: points the camera where freelook is looking. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
	private void glade$freelook(Camera self, float yaw, float pitch) {
		var freelook = TuffClient.modules() == null ? null : TuffClient.modules().freelook;
		if (freelook != null && freelook.active()) setRotation(freelook.yaw(), freelook.pitch());
		else setRotation(yaw, pitch);
	}
}
