package net.tuffclient.legacy.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL11;

/** Clips drawing to a rectangle (GUI pixels). */
public final class Scissor {
	private Scissor() {
	}

	public static void push(float x, float y, float w, float h) {
		MinecraftClient mc = MinecraftClient.getInstance();
		int scale = new Window(mc).getScaleFactor();
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor((int) (x * scale), (int) (mc.height - (y + h) * scale), (int) (w * scale), (int) (h * scale));
	}

	public static void pop() {
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
	}
}
