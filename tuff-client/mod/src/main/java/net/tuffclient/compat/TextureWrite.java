// glade: minecraft >=1.21.5
package net.tuffclient.compat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;

/** Copies an image into a texture the game already made (so its sampling settings stay). */
public final class TextureWrite {
	private TextureWrite() {
	}

	public static void write(AbstractTexture texture, NativeImage image) {
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), image);
	}
}
