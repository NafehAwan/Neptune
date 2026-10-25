package net.tuffclient.module.visual;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.tuffclient.TuffClient;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.ChoiceSetting;

/**
 * Motion blur: each frame is blended with the frames before it (kept in a
 * persistent target of a small post effect shipped with Glade). Runs after
 * the world and before the HUD, so the HUD stays sharp.
 */
public final class MotionBlurModule extends Module {
	private static final String[] LEVELS = {"low", "mid", "high"};
	private final ChoiceSetting strength = add(new ChoiceSetting("strength", "Strength", 1, "Low", "Medium", "High"));

	public MotionBlurModule() {
		super("motion_blur", "Motion Blur", "Smooth blur when you turn, like Lunar's motion blur.", Category.VISUAL, false);
		requires("MotionBlurMixin");
	}

	/** Called from the game renderer once the world is drawn. */
	public static void apply(RenderTarget main, GraphicsResourceAllocator pool) {
		var modules = TuffClient.modules();
		if (modules == null || !modules.motionBlur.isEnabled() || net.tuffclient.compat.MotionBlurFormat.NAME == null) return;
		PostChain chain = chain();
		if (chain != null) chain.process(main, pool);
	}

	/** The loaded blur effect for the current strength, or null if this version has none or it failed to load. */
	public static PostChain chain() {
		var modules = TuffClient.modules();
		if (modules == null || net.tuffclient.compat.MotionBlurFormat.NAME == null) return null;
		Identifier id = Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "motion_blur_" + net.tuffclient.compat.MotionBlurFormat.NAME + "_" + LEVELS[modules.motionBlur.strength.get()]);
		return Minecraft.getInstance().getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
	}
}
