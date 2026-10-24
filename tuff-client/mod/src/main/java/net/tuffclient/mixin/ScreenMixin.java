// glade: minecraft >=26.1
package net.tuffclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Auto Reconnect: the countdown on the disconnected screen, (a click cancels it). */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void glade$reconnectCountdown(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if ((Object) this instanceof DisconnectedScreen && TuffClient.modules() != null && TuffClient.modules().autoReconnect.isEnabled()) {
			TuffClient.modules().autoReconnect.renderOn((Screen) (Object) this, net.tuffclient.gui.Gfx.of(g));
		}
	}
}
