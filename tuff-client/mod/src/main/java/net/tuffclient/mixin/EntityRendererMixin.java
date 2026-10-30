package net.tuffclient.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Glade's nametag additions: the cosmetics badge and tiers (see NameTags). */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "finalizeRenderState", at = @At("HEAD"))
	private void glade$badge(Entity entity, EntityRenderState state, CallbackInfo ci) {
		state.nameTag = net.tuffclient.NameTags.decorate(entity, state.nameTag);
	}
}
