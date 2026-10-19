package net.tuffclient.legacy.mixin;

import net.minecraft.client.MinecraftClient;
import net.tuffclient.legacy.hud.Cps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts clicks for CPS and Keystrokes. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "doAttack()V", at = @At("HEAD"))
	private void glade$leftClick(CallbackInfo ci) {
		Cps.click(true);
	}

	@Inject(method = "doUse()V", at = @At("HEAD"))
	private void glade$rightClick(CallbackInfo ci) {
		Cps.click(false);
	}
}
