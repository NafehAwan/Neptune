// glade: minecraft >=1.21.2 <1.21.9
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
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void glade$badge(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		state.nameTag = net.tuffclient.NameTags.decorate(entity, state.nameTag);
	}
}
