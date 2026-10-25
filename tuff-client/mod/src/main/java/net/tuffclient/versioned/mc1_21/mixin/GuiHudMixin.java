// glade: minecraft <1.21.6
package net.tuffclient.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.tuffclient.Mc;
import net.tuffclient.TuffClient;
import net.tuffclient.gui.Gfx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide Scoreboard and Glade's crosshair, before Fabric API had HUD elements (1.21.6). */
@Mixin(Gui.class)
public abstract class GuiHudMixin {
	@Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
	private void glade$hideScoreboard(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		if (TuffClient.modules() != null && TuffClient.modules().scoreboard.hidden()) ci.cancel();
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void glade$crosshair(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		var modules = TuffClient.modules();
		if (modules == null || !modules.crosshair.isEnabled()) return;
		ci.cancel();
		if (modules.crosshair.shows(Minecraft.getInstance()) && !Mc.hudHidden()) modules.crosshair.render(Gfx.of(graphics));
	}
}
