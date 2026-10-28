// glade: minecraft >=1.21.6
package net.tuffclient.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.tuffclient.TuffClient;

/** Runs Glade's motion blur post effect (versions with persistent post targets). */
public final class MotionBlurChain {
	private static final String[] LEVELS = {"low", "mid", "high"};

	private MotionBlurChain() {
	}

	/** Called from the game renderer once the world is drawn. */
	private static long retryAt;

	public static void apply(RenderTarget main, GraphicsResourceAllocator pool) {
		var modules = TuffClient.modules();
		if (modules == null || !modules.motionBlur.isEnabled()) return;
		// if the effect failed to load, don't ask again every frame (it logs each time)
		if (System.currentTimeMillis() < retryAt) return;
		PostChain chain = chain(modules.motionBlur.level());
		if (chain == null) {
			retryAt = System.currentTimeMillis() + 10_000;
			return;
		}
		try {
			chain.process(main, pool);
		} catch (RuntimeException e) {
			TuffClient.LOGGER.warn("Motion blur failed; trying again in a bit", e);
			retryAt = System.currentTimeMillis() + 10_000;
		}
	}

	/** The loaded effect at a strength, or null if it failed to load. */
	public static PostChain chain(int level) {
		Identifier id = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "motion_blur_" + MotionBlurFormat.NAME + "_" + LEVELS[Math.max(0, Math.min(2, level))]);
		return Minecraft.getInstance().getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
	}
}
