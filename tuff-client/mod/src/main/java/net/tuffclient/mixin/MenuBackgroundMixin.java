// glade: minecraft >=26.1
package net.tuffclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menu Background: skips the dark tint and the blur behind screens while in a world. */
@Mixin(Screen.class)
public abstract class MenuBackgroundMixin {
	@Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
	private void glade$tint(CallbackInfo ci) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().menuBackground;
		if (m != null && m.isEnabled() && m.tint.get() && Minecraft.getInstance().level != null) ci.cancel();
	}

	@Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
	private void glade$blur(CallbackInfo ci) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().menuBackground;
		if (m != null && m.isEnabled() && m.blur.get() && Minecraft.getInstance().level != null) ci.cancel();
	}
}
