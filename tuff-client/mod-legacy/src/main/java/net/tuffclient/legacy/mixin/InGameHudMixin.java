package net.tuffclient.legacy.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.tuffclient.legacy.GladeLegacy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Glade's HUD, and Hide Scoreboard. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Inject(method = "render(F)V", at = @At("TAIL"))
	private void glade$hud(float tickDelta, CallbackInfo ci) {
		if (GladeLegacy.modules() != null) GladeLegacy.modules().renderHud(net.minecraft.client.MinecraftClient.getInstance());
	}

	@Inject(method = "renderScoreboardObjective", at = @At("HEAD"), cancellable = true)
	private void glade$hideScoreboard(ScoreboardObjective objective, Window window, CallbackInfo ci) {
		if (GladeLegacy.modules() != null && GladeLegacy.modules().hideScoreboard.isEnabled()) ci.cancel();
	}
}
