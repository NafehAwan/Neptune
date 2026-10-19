// glade: minecraft >=26.1 <26.3
package net.tuffclient.mixin;

import net.minecraft.client.ClientClockManager;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time & Weather: the client's world clocks report the chosen time of day (the day count is kept). */
@Mixin(ClientClockManager.class)
public abstract class ClientClockInstanceMixin {
	@Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true)
	private void glade$time(CallbackInfoReturnable<Long> cir) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().timeWeather;
		if (m == null || !m.isEnabled() || !m.changeTime.get()) return;
		long t = cir.getReturnValueJ();
		cir.setReturnValue(t - Math.floorMod(t, 24000L) + m.ticks());
	}
}
