package net.tuffclient.gui;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import net.tuffclient.compat.CameraView;
import org.joml.Vector3fc;

/** Turns a spot in the world into a spot on the HUD (for waypoints and labels over things). */
public final class Projector {
	private final Vec3 eye;
	private final Vector3fc forward, up, left;
	private final double tanHalf;
	private final int gw, gh;

	public Projector(Gfx g) {
		Camera cam = CameraView.main();
		eye = CameraView.eye(cam);
		forward = CameraView.forward(cam);
		up = CameraView.up(cam);
		left = CameraView.left(cam);
		tanHalf = Math.tan(Math.toRadians(CameraView.fov(cam)) / 2);
		gw = g.guiWidth();
		gh = g.guiHeight();
	}

	/** Distance from the camera to a point. */
	public double distance(double x, double y, double z) {
		double rx = x - eye.x, ry = y - eye.y, rz = z - eye.z;
		return Math.sqrt(rx * rx + ry * ry + rz * rz);
	}

	/** Screen position {x, y} of a world point, or null when it's behind the camera. */
	public float[] project(double x, double y, double z) {
		double rx = x - eye.x, ry = y - eye.y, rz = z - eye.z;
		double cz = rx * forward.x() + ry * forward.y() + rz * forward.z();
		if (cz < 0.05) return null;
		double cx = -(rx * left.x() + ry * left.y() + rz * left.z()), cy = rx * up.x() + ry * up.y() + rz * up.z();
		float sx = (float) (gw / 2.0 + cx / cz / tanHalf * gh / 2.0);
		float sy = (float) (gh / 2.0 - cy / cz / tanHalf * gh / 2.0);
		return new float[] {sx, sy};
	}
}
