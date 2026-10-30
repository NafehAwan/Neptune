package net.tuffclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low Fire: draws the burning overlay lower on the screen. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Inject(method = "renderFire", at = @At("HEAD"))
	private static void glade$lowFireStart(PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
		pose.pushPose();
		var m = TuffClient.modules() == null ? null : TuffClient.modules().lowFire;
		if (m != null && m.isEnabled()) pose.translate(0, (float) -m.height.get() / 100f * 0.6f, 0);
	}

	@Inject(method = "renderFire", at = @At("RETURN"))
	private static void glade$lowFireEnd(PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
		pose.popPose();
	}
}
