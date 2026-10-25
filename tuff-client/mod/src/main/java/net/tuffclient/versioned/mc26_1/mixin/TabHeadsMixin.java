// glade: minecraft >=26.1 <26.2
package net.tuffclient.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.Connection;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Tab Editor: player heads on every server (the game shows them only with encryption, in online mode). */
@Mixin(PlayerTabOverlay.class)
public abstract class TabHeadsMixin {
	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;isEncrypted()Z"))
	private boolean glade$heads(Connection connection) {
		return connection.isEncrypted() || TuffClient.modules() != null && TuffClient.modules().tabPing.alwaysHeads();
	}
}
