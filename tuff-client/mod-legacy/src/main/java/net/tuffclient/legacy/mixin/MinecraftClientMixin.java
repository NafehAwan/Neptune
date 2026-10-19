package net.tuffclient.legacy.mixin;

import net.minecraft.client.MinecraftClient;
import net.tuffclient.legacy.hud.Cps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Glade's tick and save-on-exit, and click counting for CPS and Keystrokes. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "doAttack()V", at = @At("HEAD"))
	private void glade$leftClick(CallbackInfo ci) {
		Cps.click(true);
	}

	@Inject(method = "tick()V", at = @At("TAIL"))
	private void glade$tick(CallbackInfo ci) {
		net.tuffclient.legacy.GladeLegacy.tick((MinecraftClient) (Object) this);
	}

	@Inject(method = "stop()V", at = @At("HEAD"))
	private void glade$stop(CallbackInfo ci) {
		net.tuffclient.legacy.GladeLegacy.stopping();
	}

	@Inject(method = "doUse()V", at = @At("HEAD"))
	private void glade$rightClick(CallbackInfo ci) {
		Cps.click(false);
	}
}
