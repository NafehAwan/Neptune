package net.tuffclient.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook turns the camera instead of the player; scrolling while zoomed zooms further. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void glade$freelook(LocalPlayer player, double dx, double dy) {
		var freelook = TuffClient.modules() == null ? null : TuffClient.modules().freelook;
		if (freelook != null && freelook.active()) freelook.turn(dx, dy);
		else player.turn(dx, dy);
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void glade$zoomScroll(long window, double x, double y, CallbackInfo ci) {
		if (TuffClient.modules() != null && net.minecraft.client.Minecraft.getInstance().gui.screen() == null && TuffClient.modules().zoom.onScroll(y)) ci.cancel();
	}
}
