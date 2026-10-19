package net.tuffclient.module.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.tuffclient.TuffClient;
import net.tuffclient.gui.Ui;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ColorSetting;
import net.tuffclient.setting.NumberSetting;
import org.joml.Vector3fc;

/**
 * Saved places, shown on screen with their distance. Press B (by default) to
 * add one where you stand; a death waypoint is added where you die. Saved per
 * server (or singleplayer) and dimension in config/tuff/waypoints.json.
 */
public final class WaypointsModule extends Module {
	private static final int[] COLORS = {0xFF4FC79A, 0xFF5AA9F0, 0xFFE3B23C, 0xFFE8708A, 0xFFA07FE2, 0xFFDB8A57};
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tuff").resolve("waypoints.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final class Point {
		public String name;
		public int x, y, z;
		public int color;
		public boolean visible = true;
		public boolean death;
	}

	private final BoolSetting names = add(new BoolSetting("names", "Show names", true));
	private final BoolSetting distance = add(new BoolSetting("distance", "Show distance", true));
	private final BoolSetting deaths = add(new BoolSetting("deaths", "Add a waypoint where you die", true));
	private final NumberSetting maxDistance = add(new NumberSetting("max_distance", "Hide beyond (blocks, 0 = never)", 0, 0, 10000, 100));
	private final NumberSetting size = add(new NumberSetting("size", "Marker size", 6, 3, 12, 1));
	private final ColorSetting labelText = add(new ColorSetting("label_text", "Label text color", 0xFFFFFFFF, true));
	private final ColorSetting labelBackground = add(new ColorSetting("label_background", "Label background", 0x88101210, true));
	private final BoolSetting labelShadow = add(new BoolSetting("label_shadow", "Label text shadow", true));

	private Map<String, List<Point>> all;
	private boolean wasDead;

	public WaypointsModule() {
		super("waypoints", "Waypoints", "Save places and see them on screen with their distance. Press B to add one.", Category.UTILITY, true);
	}

	private Map<String, List<Point>> all() {
		if (all == null) {
			all = new HashMap<>();
			try {
				if (Files.isRegularFile(FILE)) {
					Map<String, List<Point>> read = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), new TypeToken<Map<String, List<Point>>>() { }.getType());
					if (read != null) all.putAll(read);
				}
			} catch (IOException | RuntimeException e) {
				TuffClient.LOGGER.warn("Couldn't read waypoints", e);
			}
		}
		return all;
	}

	public void saveWaypoints() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(all()), StandardCharsets.UTF_8);
		} catch (IOException e) {
			TuffClient.LOGGER.warn("Couldn't save waypoints", e);
		}
	}

	/** Server address (or singleplayer) plus dimension. */
	private static String key() {
		Minecraft mc = Minecraft.getInstance();
		String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
		String dim = mc.level == null ? "?" : mc.level.dimension().identifier().toString();
		return server + "|" + dim;
	}

	/** Waypoints in the current world and dimension. */
	public List<Point> here() {
		return all().computeIfAbsent(key(), k -> new ArrayList<>());
	}

	public void remove(Point p) {
		here().remove(p);
		saveWaypoints();
	}

	public String addKeyName() {
		return TuffClient.waypointKey() == null ? "B" : TuffClient.waypointKey().getTranslatedKeyMessage().getString();
	}

	/** Adds a waypoint where the player stands. */
	public void addHere() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return;
		Point pt = new Point();
		List<Point> list = here();
		pt.name = "Waypoint " + (list.stream().filter(w -> !w.death).count() + 1);
		pt.x = (int) Math.floor(p.getX());
		pt.y = (int) Math.floor(p.getY());
		pt.z = (int) Math.floor(p.getZ());
		pt.color = COLORS[list.size() % COLORS.length];
		list.add(pt);
		saveWaypoints();
		p.sendOverlayMessage(Component.literal("Added " + pt.name + " at " + pt.x + " " + pt.y + " " + pt.z));
	}

	@Override
	public void tick(Minecraft client) {
		LocalPlayer p = client.player;
		if (p == null) return;
		boolean dead = p.isDeadOrDying();
		if (dead && !wasDead && deaths.get()) {
			List<Point> list = here();
			list.removeIf(w -> w.death);
			Point pt = new Point();
			pt.name = "Death";
			pt.death = true;
			pt.x = (int) Math.floor(p.getX());
			pt.y = (int) Math.floor(p.getY());
			pt.z = (int) Math.floor(p.getZ());
			pt.color = 0xFFE55A55;
			list.add(pt);
			saveWaypoints();
		}
		wasDead = dead;
	}

	/** Draws every visible waypoint at its spot on screen. */
	public void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		Camera cam = mc.gameRenderer.mainCamera();
		Vec3 eye = cam.position();
		Vector3fc f = cam.forwardVector(), up = cam.upVector(), left = cam.leftVector();
		double tanHalf = Math.tan(Math.toRadians(cam.getFov()) / 2);
		int gw = g.guiWidth(), gh = g.guiHeight();
		Font font = mc.font;
		double max = maxDistance.get();
		for (Point pt : here()) {
			if (!pt.visible) continue;
			double rx = pt.x + 0.5 - eye.x, ry = pt.y + 1.2 - eye.y, rz = pt.z + 0.5 - eye.z;
			double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
			if (max > 0 && dist > max) continue;
			double cz = rx * f.x() + ry * f.y() + rz * f.z();
			if (cz < 0.05) continue;
			double cx = -(rx * left.x() + ry * left.y() + rz * left.z()), cy = rx * up.x() + ry * up.y() + rz * up.z();
			float sx = (float) (gw / 2.0 + cx / cz / tanHalf * gh / 2.0);
			float sy = (float) (gh / 2.0 - cy / cz / tanHalf * gh / 2.0);
			if (sx < -50 || sx > gw + 50 || sy < -50 || sy > gh + 50) continue;
			float s = size.get().floatValue();
			// a diamond marker
			g.pose().pushMatrix();
			g.pose().translate(sx, sy);
			g.pose().rotate((float) Math.toRadians(45));
			Ui.round(g, -s / 2 - 1, -s / 2 - 1, s + 2, s + 2, 1.5f, 0xCC000000);
			Ui.round(g, -s / 2, -s / 2, s, s, 1, pt.color | 0xFF000000);
			g.pose().popMatrix();
			String label = (names.get() ? pt.name : "") + (distance.get() ? (names.get() ? "  " : "") + Math.round(dist) + "m" : "");
			if (label.isEmpty()) continue;
			int lw = font.width(label);
			int lx = Math.round(sx - lw / 2f), ly = Math.round(sy + s + 3);
			if ((labelBackground.argb() >>> 24) > 0) Ui.round(g, lx - 3, ly - 2, lw + 6, 12, 3, labelBackground.argb());
			g.text(font, label, lx, ly, labelText.argb(), labelShadow.get());
		}
	}
}
