// glade: minecraft <1.21.11
package net.tuffclient.compat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/** The game camera: where it is, which way it faces and its field of view. */
public final class CameraView {
	private CameraView() {
	}

	public static Camera main() {
		return Minecraft.getInstance().gameRenderer.getMainCamera();
	}

	public static Vec3 eye(Camera cam) {
		return cam.getPosition();
	}

	public static Vector3fc forward(Camera cam) {
		return cam.getLookVector();
	}

	public static Vector3fc up(Camera cam) {
		return cam.getUpVector();
	}

	public static Vector3fc left(Camera cam) {
		return cam.getLeftVector();
	}

	/** Vertical field of view in degrees. */
	public static double fov(Camera cam) {
		return Minecraft.getInstance().options.fov().get();
	}
}
