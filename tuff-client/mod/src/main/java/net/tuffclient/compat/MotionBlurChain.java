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
	public static void apply(RenderTarget main, GraphicsResourceAllocator pool) {
		var modules = TuffClient.modules();
		if (modules == null || !modules.motionBlur.isEnabled()) return;
		PostChain chain = chain(modules.motionBlur.level());
		if (chain != null) chain.process(main, pool);
	}

	/** The loaded effect at a strength, or null if it failed to load. */
	public static PostChain chain(int level) {
		Identifier id = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "motion_blur_" + MotionBlurFormat.NAME + "_" + LEVELS[Math.max(0, Math.min(2, level))]);
		return Minecraft.getInstance().getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
	}
}
