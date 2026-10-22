package net.tuffclient.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.NumberSetting;

/** More FPS in busy places: mobs, items and the like stop rendering past a distance. */
public final class EntityDistanceModule extends Module {
	public final NumberSetting distance = add(new NumberSetting("distance", "Render entities within (blocks)", 48, 8, 128, 8));
	public final BoolSetting players = add(new BoolSetting("players", "Players too", false));

	public EntityDistanceModule() {
		super("entity_distance", "Entity Distance", "Stops drawing far away mobs and items for more FPS.", Category.VISUAL, false);
	}

	/** Whether this entity is too far to draw. */
	public boolean tooFar(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		boolean own = isEnabled();
		var boost = net.tuffclient.TuffClient.modules() == null ? null : net.tuffclient.TuffClient.modules().fpsBoost;
		if (!own && (boost == null || !boost.hidesFarEntities())) return false;
		if (mc.player == null || entity == mc.player) return false;
		if (entity instanceof Player && (!own || !players.get())) return false;
		double d = own ? distance.get() : 48;
		return mc.player.distanceToSqr(entity) > d * d;
	}
}
