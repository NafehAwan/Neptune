// glade: minecraft >=1.21.2
package net.tuffclient.compat;

import com.mojang.blaze3d.platform.NativeImage;

/** Writes a pixel as ARGB (NativeImage took ABGR before 1.21.2). */
public final class Images {
	private Images() {
	}

	public static void setPixel(NativeImage image, int x, int y, int argb) {
		image.setPixel(x, y, argb);
	}

	/** Reads a pixel as ARGB. */
	public static int getPixel(NativeImage image, int x, int y) {
		return image.getPixel(x, y);
	}
}
