// glade: minecraft <1.21.2
package net.tuffclient.mixin;

import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Scoreboard: no background behind the sidebar when it's turned off. */
@Mixin(Gui.class)
public abstract class ScoreboardMixin {
	// 1.21.1 draws the sidebar inside a lambda (guiGraphics.drawManaged)
	@Redirect(method = "method_55440", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(F)I"))
	private int glade$background(Options options, float opacity) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().scoreboard;
		return m != null && m.noBackground() ? 0 : options.getBackgroundColor(opacity);
	}

	/** Hides the red score numbers down the right side. */
	@org.spongepowered.asm.mixin.injection.Redirect(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Objective;numberFormatOrDefault(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/numbers/NumberFormat;"), require = 0)
	private net.minecraft.network.chat.numbers.NumberFormat glade$numbers(net.minecraft.world.scores.Objective objective, net.minecraft.network.chat.numbers.NumberFormat fallback) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().scoreboard;
		return m != null && m.hideNumbers() ? net.minecraft.network.chat.numbers.BlankFormat.INSTANCE : objective.numberFormatOrDefault(fallback);
	}
}
