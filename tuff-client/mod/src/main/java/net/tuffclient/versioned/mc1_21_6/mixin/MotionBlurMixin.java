// glade: minecraft >=1.21.6 <26.2
package net.tuffclient.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Motion Blur: blends frames right after the world is drawn (before the game's own effects and the HUD). */
@Mixin(GameRenderer.class)
public abstract class MotionBlurMixin {
	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
	private void glade$motionBlur(CallbackInfo ci) {
		net.tuffclient.compat.MotionBlurChain.apply(Minecraft.getInstance().getMainRenderTarget(), resourcePool);
	}
}
