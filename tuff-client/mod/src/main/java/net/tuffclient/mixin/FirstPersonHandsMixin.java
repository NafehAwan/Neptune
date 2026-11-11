package net.tuffclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hand & Item View (move and size each hand) and Small Shield. Each arm is
 * drawn by one call, so ours wraps it in its own pose.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonHandsMixin {
	private static final String ARM = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

	@Inject(method = ARM, at = @At("HEAD"))
	private void glade$armStart(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip,
			PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		pose.pushPose();
		var modules = TuffClient.modules();
		if (modules == null) return;
		boolean main = hand == InteractionHand.MAIN_HAND;
		var view = modules.handView;
		if (view.isEnabled()) {
			float side = main ? 1 : -1;
			pose.translate((float) (double) (main ? view.mainX.get() : view.offX.get()) * 0.5f * side,
					(float) (double) (main ? view.mainY.get() : view.offY.get()) * 0.5f,
					(float) -(main ? view.mainZ.get() : view.offZ.get()) * 0.5f);
			float s = (float) (double) (main ? view.mainScale.get() : view.offScale.get()) / 100f;
			pose.scale(s, s, s);
		}
		var shield = modules.smallShield;
		if (shield.isEnabled() && (stack.is(Items.SHIELD) || shield.totems.get() && stack.is(Items.TOTEM_OF_UNDYING))) {
			pose.translate(0, (float) -shield.lower.get() / 100f, 0);
			float s = (float) (shield.scale.get() / 100f);
			pose.scale(s, s, s);
		}
	}

	@Inject(method = ARM, at = @At("RETURN"))
	private void glade$armEnd(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip,
			PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		pose.popPose();
	}
}
