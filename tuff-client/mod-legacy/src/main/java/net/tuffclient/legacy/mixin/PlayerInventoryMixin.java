package net.tuffclient.legacy.mixin;

import net.minecraft.entity.player.PlayerInventory;
import net.tuffclient.legacy.GladeLegacy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scrolling while zooming zooms further instead of changing the hotbar slot. */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
	@Inject(method = "scrollInHotbar(I)V", at = @At("HEAD"), cancellable = true)
	private void glade$zoomScroll(int direction, CallbackInfo ci) {
		if (GladeLegacy.modules() != null && GladeLegacy.modules().zoom.onScroll(direction)) ci.cancel();
	}
}
