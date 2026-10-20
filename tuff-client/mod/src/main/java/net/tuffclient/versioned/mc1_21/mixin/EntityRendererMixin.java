// glade: minecraft <1.21.2
package net.tuffclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Glade's nametag additions (badge, tiers) before render states existed: the name passed to renderNameTag. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V"), index = 1)
	private Component glade$nameTag(Entity entity, Component name, PoseStack pose, MultiBufferSource buffers, int light, float partialTick) {
		return net.tuffclient.NameTags.decorate(entity, name);
	}
}
