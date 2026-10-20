// glade: minecraft >=26.2
package net.tuffclient.mixin;

import net.minecraft.client.Options;
import net.minecraft.client.gui.Hud;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Scoreboard: no background behind the sidebar when it's turned off. */
@Mixin(Hud.class)
public abstract class ScoreboardMixin {
	@Redirect(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundColor(F)I"))
	private int glade$background(Options options, float opacity) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().scoreboard;
		return m != null && m.noBackground() ? 0 : options.getBackgroundColor(opacity);
	}
}
