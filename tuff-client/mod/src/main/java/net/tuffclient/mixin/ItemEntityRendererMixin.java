package net.tuffclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Item Physics: dropped items lie flat instead of bobbing and spinning. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

	private static boolean on() {
		return TuffClient.modules() != null && TuffClient.modules().itemPhysics.isEnabled();
	}

	@Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
	private void glade$noBob(PoseStack pose, float x, float y, float z) {
		if (on()) pose.translate(x, 0.04f, z);
		else pose.translate(x, y, z);
	}

	@Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotate(Lcom/mojang/math/Axis;F)V"))
	private void glade$flat(PoseStack pose, Axis axis, float angle, @Local(argsOnly = true) ItemEntityRenderState state) {
		if (!on()) {
			pose.rotate(axis, angle);
			return;
		}
		pose.rotateDegrees(Axis.XP, 90);
		if (TuffClient.modules().itemPhysics.randomTurn.get()) pose.rotateDegrees(Axis.ZP, state.bobOffset * 57.3f);
	}
}
