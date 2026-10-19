// glade: minecraft >=26.3
package net.tuffclient.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Sky: tints the sky colour after the game works it out. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void glade$sky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().customSky;
		if (m != null && m.tintSky() && state.skyColor != null) state.skyColor = m.sky(state.skyColor);
	}
}
