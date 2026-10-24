// glade: minecraft >=26.2
package net.tuffclient.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Block Outline: the colour and thickness of the outline on the targeted block. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	private static final String OUTLINE = "Lnet/minecraft/client/renderer/LevelRenderer;submitHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IFZ)V";

	@ModifyArg(method = "submitBlockOutline", at = @At(value = "INVOKE", target = OUTLINE), index = 4)
	private int glade$outlineColor(int color) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().blockOutline;
		return m != null && m.isEnabled() ? m.color.argb() : color;
	}

	@ModifyArg(method = "submitBlockOutline", at = @At(value = "INVOKE", target = OUTLINE), index = 5)
	private float glade$outlineWidth(float width) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().blockOutline;
		return m != null && m.isEnabled() ? width * m.width.get().floatValue() / 2f : width;
	}
}
