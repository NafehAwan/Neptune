// glade: minecraft >=1.21.6 <1.21.9
package net.tuffclient.compat;

/** Which of Glade's motion blur shader files fits this Minecraft version (their format changed twice). */
public final class MotionBlurFormat {
	private MotionBlurFormat() {
	}

	public static final String NAME = "v1";

	/** Whether Glade's blur effect at this strength loads. */
	public static boolean loaded(int level) {
		return MotionBlurChain.chain(level) != null;
	}
}
