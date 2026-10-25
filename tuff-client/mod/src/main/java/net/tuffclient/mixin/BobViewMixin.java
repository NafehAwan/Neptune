// glade: minecraft >=26.1
package net.tuffclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.tuffclient.module.visual.MinimalBobbingModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minimal Bobbing: view bobbing without the tilt. */
@Mixin(GameRenderer.class)
public abstract class BobViewMixin {
	@Inject(method = "bobView", at = @At("HEAD"))
	private void glade$bobStart(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		MinimalBobbingModule.start(pose);
	}

	@Inject(method = "bobView", at = @At("RETURN"))
	private void glade$bobEnd(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		MinimalBobbingModule.end(pose);
	}
}
