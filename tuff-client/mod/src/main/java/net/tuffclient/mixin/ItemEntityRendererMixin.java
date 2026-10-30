package net.tuffclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.tuffclient.TuffClient;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Physics: dropped items lie flat instead of bobbing and spinning. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V";

	@Unique
	private float glade$turn;

	private static boolean on() {
		return TuffClient.modules() != null && TuffClient.modules().itemPhysics.isEnabled();
	}

	@Inject(method = SUBMIT, at = @At("HEAD"))
	private void glade$start(ItemEntityRenderState state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
			net.minecraft.client.renderer.state.CameraRenderState camera, CallbackInfo ci) {
		glade$turn = state.bobOffset * 57.3f;
	}

	@Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
	private void glade$noBob(PoseStack pose, float x, float y, float z) {
		if (on()) pose.translate(x, 0.04f, z);
		else pose.translate(x, y, z);
	}

	@Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
	private void glade$flat(PoseStack pose, Quaternionfc spin) {
		if (!on()) {
			pose.mulPose(spin);
			return;
		}
		pose.mulPose(Axis.XP.rotationDegrees(90));
		if (TuffClient.modules().itemPhysics.randomTurn.get()) pose.mulPose(Axis.ZP.rotationDegrees(glade$turn));
	}
}
