// glade: minecraft >=1.21.2
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

/** Cosmetics: the leaf badge in front of Glade players' names. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "finalizeRenderState", at = @At("HEAD"))
	private void glade$badge(Entity entity, EntityRenderState state, CallbackInfo ci) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().cosmetics;
		if (m != null && state.nameTag != null && entity instanceof Player p && m.showBadge(p)) {
			state.nameTag = Component.empty().append(m.badge()).append(state.nameTag);
		}
	}
}
