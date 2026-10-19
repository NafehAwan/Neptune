// glade: minecraft >=1.21.9
package net.tuffclient.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.tuffclient.TuffClient;
import net.tuffclient.module.visual.CosmeticsModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cosmetics: the Glade cape, for players without a cape of their own. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	private static final net.minecraft.core.ClientAsset.ResourceTexture CAPE = new net.minecraft.core.ClientAsset.ResourceTexture(CosmeticsModule.CAPE_TEXTURE);

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void glade$cape(CallbackInfoReturnable<PlayerSkin> cir) {
		var m = TuffClient.modules() == null ? null : TuffClient.modules().cosmetics;
		PlayerSkin skin = cir.getReturnValue();
		if (m == null || skin == null || skin.cape() != null || !m.showCape((AbstractClientPlayer) (Object) this)) return;
		cir.setReturnValue(new PlayerSkin(skin.body(), CAPE, CAPE, skin.model(), skin.secure()));
	}
}
