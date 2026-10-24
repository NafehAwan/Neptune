package net.tuffclient.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time & Weather: your own weather, on your screen only. */
@Mixin(Level.class)
public abstract class LevelMixin {
	private static int weather(Object level) {
		if (!(level instanceof ClientLevel) || TuffClient.modules() == null) return -1;
		var m = TuffClient.modules().timeWeather;
		return m.isEnabled() && m.weather.get() < 3 ? m.weather.get() : -1;
	}

	@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
	private void glade$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
		int w = weather(this);
		if (w >= 0) cir.setReturnValue(w == 0 ? 0f : 1f);
	}

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
	private void glade$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
		int w = weather(this);
		if (w >= 0) cir.setReturnValue(w == 2 ? 1f : 0f);
	}
}
