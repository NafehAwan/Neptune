package net.tuffclient.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.tuffclient.hook.GladeOverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Repaints the damage flash (the top half of the overlay texture) for Hit Color. */
@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin implements GladeOverlayTexture {
	@Shadow
	@Final
	private DynamicTexture texture;

	@Override
	public void glade$recolor(int argb) {
		NativeImage pixels = texture.getPixels();
		if (pixels == null) return;
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) pixels.setPixel(x, y, argb);
		}
		texture.upload();
	}
}
