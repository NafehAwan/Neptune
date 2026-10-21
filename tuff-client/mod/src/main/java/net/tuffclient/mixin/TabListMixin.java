package net.tuffclient.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cosmetics: the leaf badge next to Glade players in the tab list. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabListMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
	private void glade$badge(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		var modules = TuffClient.modules();
		if (modules == null) return;
		Component name = cir.getReturnValue();
		Component out = modules.cosmetics.decorateTab(info, name);
		if (out != name) cir.setReturnValue(out);
	}
}
