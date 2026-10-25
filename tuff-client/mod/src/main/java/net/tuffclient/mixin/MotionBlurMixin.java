// glade: minecraft >=26.3
package net.tuffclient.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import net.tuffclient.module.visual.MotionBlurModule;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Motion Blur: blends frames after the game's own post effects, before the HUD. */
@Mixin(GameRenderer.class)
public abstract class MotionBlurMixin {
	@Shadow
	@Final
	private RenderTarget mainRenderTarget;

	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	@Inject(method = "applyPostEffects", at = @At("TAIL"))
	private void glade$motionBlur(CallbackInfo ci) {
		MotionBlurModule.apply(mainRenderTarget, resourcePool);
	}
}
