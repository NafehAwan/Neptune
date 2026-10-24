// glade: minecraft >=26.1
package net.tuffclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.tuffclient.TuffClient;
import net.tuffclient.module.util.CleanF3Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clean F3: hides the vanilla debug text (charts still show). */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
	@Shadow
	public abstract boolean showProfilerChart();

	@Shadow
	public abstract boolean showFpsCharts();

	@Shadow
	public abstract boolean showNetworkCharts();

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void glade$cleanF3(GuiGraphicsExtractor g, CallbackInfo ci) {
		CleanF3Module.charts = showProfilerChart() || showFpsCharts() || showNetworkCharts();
		if (TuffClient.modules() != null && TuffClient.modules().cleanF3.isEnabled() && !CleanF3Module.charts) ci.cancel();
	}
}
