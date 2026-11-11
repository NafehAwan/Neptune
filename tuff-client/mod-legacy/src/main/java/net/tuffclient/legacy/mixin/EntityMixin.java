package net.tuffclient.legacy.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.tuffclient.legacy.GladeLegacy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: mouse movement turns the camera, not the player. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "increaseTransforms(FF)V", at = @At("HEAD"), cancellable = true)
	private void glade$freelook(float yaw, float pitch, CallbackInfo ci) {
		if ((Object) this != MinecraftClient.getInstance().player || GladeLegacy.modules() == null) return;
		if (GladeLegacy.modules().freelook.active()) {
			GladeLegacy.modules().freelook.turn(yaw, pitch);
			ci.cancel();
		}
	}
}
