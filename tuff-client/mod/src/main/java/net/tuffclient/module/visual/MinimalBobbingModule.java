package net.tuffclient.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import net.tuffclient.TuffClient;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * View bobbing without the tilt: the camera still moves with your steps, but
 * it no longer rolls and pitches. The game's bobbing runs as usual, then only
 * the movement part of what it did is kept.
 */
public final class MinimalBobbingModule extends Module {
	private static final Matrix4f before = new Matrix4f();
	private static final Matrix3f normal = new Matrix3f();
	private static boolean saved;

	public MinimalBobbingModule() {
		super("minimal_bobbing", "Minimal Bobbing", "Keeps view bobbing but removes the camera tilt.", Category.VISUAL, false);
		requires("BobViewMixin");
	}

	private static boolean on() {
		return TuffClient.modules() != null && TuffClient.modules().minimalBobbing.isEnabled();
	}

	/** Before the game bobs the view. */
	public static void start(PoseStack pose) {
		saved = on();
		if (!saved) return;
		before.set(pose.last().pose());
		normal.set(pose.last().normal());
	}

	/** After: keep the bob's offset, drop its rotation. */
	public static void end(PoseStack pose) {
		if (!saved) return;
		saved = false;
		Matrix4f after = pose.last().pose();
		Vector3f offset = new Matrix4f(before).invert().mul(after).getTranslation(new Vector3f());
		after.set(before).translate(offset);
		pose.last().normal().set(normal);
	}
}
