// glade: minecraft >=1.21.2 <1.21.9
package net.tuffclient.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Block Outline: the colour of the outline on the targeted block. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	private static final String OUTLINE = "Lnet/minecraft/client/renderer/LevelRenderer;renderHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V";

	@ModifyArg(method = "renderBlockOutline", at = @At(value = "INVOKE", target = OUTLINE), index = 8)
	private int glade$outlineColor(int color) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().blockOutline;
		return m != null && m.isEnabled() ? m.color.argb() : color;
	}
}
