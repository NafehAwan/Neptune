package net.tuffclient.module.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** How far away your last hit landed, in blocks (eyes to the target's hitbox). */
public final class ReachModule extends TextHudModule {
	private double reach = -1;
	private long at;

	private final net.tuffclient.setting.NumberSetting decimals = add(new net.tuffclient.setting.NumberSetting("decimals", "Decimal places", 2, 0, 3, 1));
	private final net.tuffclient.setting.NumberSetting keep = add(new net.tuffclient.setting.NumberSetting("keep", "Keep showing for (seconds)", 3, 1, 10, 1));

	public ReachModule() {
		super("reach", "Reach", "How far your last hit landed, in blocks.", "Reach", 0.006, 0.17, false);
	}

	public void onAttack(Player player, Entity target) {
		Vec3 eye = player.getEyePosition();
		AABB box = target.getBoundingBox();
		double dx = Math.max(box.minX - eye.x, Math.max(0, eye.x - box.maxX));
		double dy = Math.max(box.minY - eye.y, Math.max(0, eye.y - box.maxY));
		double dz = Math.max(box.minZ - eye.z, Math.max(0, eye.z - box.maxZ));
		reach = Math.sqrt(dx * dx + dy * dy + dz * dz);
		at = System.currentTimeMillis();
	}

	@Override
	protected String value(Minecraft client, boolean preview) {
		String fmt = "%." + decimals.get().intValue() + "f";
		if (preview && reach < 0) return String.format(fmt, 2.94);
		// fades back to a dash after a few seconds without hitting
		return reach < 0 || System.currentTimeMillis() - at > keep.get() * 1000 ? "-" : String.format(fmt, reach);
	}
}
