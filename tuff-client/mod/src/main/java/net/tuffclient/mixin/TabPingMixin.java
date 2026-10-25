// glade: minecraft >=26.1
package net.tuffclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.tuffclient.TuffClient;
import net.tuffclient.gui.Gfx;
import net.tuffclient.module.visual.TabPingModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tab Editor: a number in place of the signal bars. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabPingMixin {
	@Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true)
	private void glade$ping(GuiGraphicsExtractor graphics, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
		if (TuffClient.modules() == null || !TuffClient.modules().tabPing.numbers()) return;
		var font = Minecraft.getInstance().font;
		String ms = String.valueOf(info.getLatency());
		Gfx.of(graphics).text(font, ms, x + width - font.width(ms) - 1, y, TabPingModule.color(info.getLatency()), true);
		ci.cancel();
	}

}
