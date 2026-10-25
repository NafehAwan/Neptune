// glade: minecraft <1.21.2
package net.tuffclient.compat;

import com.mojang.blaze3d.platform.NativeImage;

/** Writes a pixel as ARGB (NativeImage took ABGR before 1.21.2). */
public final class Images {
	private Images() {
	}

	public static void setPixel(NativeImage image, int x, int y, int argb) {
		image.setPixelRGBA(x, y, (argb & 0xFF00FF00) | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16);
	}

	/** Reads a pixel as ARGB. */
	public static int getPixel(NativeImage image, int x, int y) {
		int abgr = image.getPixelRGBA(x, y);
		return (abgr & 0xFF00FF00) | (abgr >> 16 & 0xFF) | (abgr & 0xFF) << 16;
	}
}
