package net.tuffclient.mixin;

import net.minecraft.client.Options;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nametags: the background opacity behind nametags. */
@Mixin(Options.class)
public abstract class OptionsMixin {
	@Inject(method = "getBackgroundOpacity", at = @At("RETURN"), cancellable = true)
	private void glade$nametagBackground(float fallback, CallbackInfoReturnable<Float> cir) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().nametags;
		if (m != null && m.isEnabled()) cir.setReturnValue(m.background.get().floatValue() / 100f);
	}
}
