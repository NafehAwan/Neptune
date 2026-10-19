// glade: minecraft >=26.2
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
		return Minecraft.getInstance().gameRenderer.mainCamera();
	}

	public static Vec3 eye(Camera cam) {
		return cam.position();
	}

	public static Vector3fc forward(Camera cam) {
		return cam.forwardVector();
	}

	public static Vector3fc up(Camera cam) {
		return cam.upVector();
	}

	public static Vector3fc left(Camera cam) {
		return cam.leftVector();
	}

	/** Vertical field of view in degrees. */
	public static double fov(Camera cam) {
		return cam.getFov();
	}
}
