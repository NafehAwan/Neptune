package net.tuffclient.legacy.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.tuffclient.legacy.GladeLegacy;
import net.tuffclient.legacy.util.FreelookModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom (a narrower field of view) and Freelook (the camera turns on its own). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Unique
	private float glade$yaw, glade$pitch, glade$prevYaw, glade$prevPitch;

	@Inject(method = "getFov(FZ)F", at = @At("RETURN"), cancellable = true)
	private void glade$zoom(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
		if (GladeLegacy.modules() == null) return;
		float divisor = GladeLegacy.modules().zoom.divisor();
		if (divisor > 1.001f) cir.setReturnValue(cir.getReturnValue() / divisor);
	}

	@Inject(method = "transformCamera(F)V", at = @At("HEAD"))
	private void glade$freelookStart(float tickDelta, CallbackInfo ci) {
		Entity e = MinecraftClient.getInstance().getCameraEntity();
		FreelookModule f = GladeLegacy.modules() == null ? null : GladeLegacy.modules().freelook;
		if (e == null || f == null || !f.active()) return;
		glade$yaw = e.yaw;
		glade$pitch = e.pitch;
		glade$prevYaw = e.prevYaw;
		glade$prevPitch = e.prevPitch;
		e.yaw = e.prevYaw = f.yaw;
		e.pitch = e.prevPitch = f.pitch;
	}

	@Inject(method = "transformCamera(F)V", at = @At("RETURN"))
	private void glade$freelookEnd(float tickDelta, CallbackInfo ci) {
		Entity e = MinecraftClient.getInstance().getCameraEntity();
		FreelookModule f = GladeLegacy.modules() == null ? null : GladeLegacy.modules().freelook;
		if (e == null || f == null || !f.active()) return;
		e.yaw = glade$yaw;
		e.pitch = glade$pitch;
		e.prevYaw = glade$prevYaw;
		e.prevPitch = glade$prevPitch;
	}
}
