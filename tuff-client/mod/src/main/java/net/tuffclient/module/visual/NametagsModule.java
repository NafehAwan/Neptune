package net.tuffclient.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.Team;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.NumberSetting;

/** Your own nametag in third person, hiding nametags, and their background. */
public final class NametagsModule extends Module {
	public final BoolSetting own = add(new BoolSetting("own", "Show your own nametag in third person", true));
	public final BoolSetting hideAll = add(new BoolSetting("hide_all", "Hide all nametags", false));
	public final BoolSetting serverTags = add(new BoolSetting("server_tags", "Hide nametags under a server's own nametags", true));
	public final NumberSetting background = add(new NumberSetting("background", "Background opacity (%)", 25, 0, 100, 5));

	public NametagsModule() {
		super("nametags", "Nametags", "See your own nametag, hide nametags, or change their background.", Category.VISUAL, false);
	}

	/** Whether an entity's nametag shows, given what vanilla decided. */
	public boolean show(LivingEntity entity, boolean vanilla) {
		Minecraft mc = Minecraft.getInstance();
		if (isEnabled() && hideAll.get()) return false;
		boolean shown = vanilla;
		if (!shown && isEnabled() && own.get() && entity == mc.player && !mc.options.getCameraType().isFirstPerson()
				&& !net.tuffclient.Mc.hudHidden() && !entity.isInvisible() && !hiddenByTeam(entity)) shown = true;
		// servers that draw their own nametag (a passenger, or a floating name right above the head) would overlap ours
		if (shown && entity instanceof Player && (!isEnabled() || serverTags.get()) && hasServerTag(entity)) return false;
		return shown;
	}

	private static boolean hiddenByTeam(Entity entity) {
		var team = entity.getTeam();
		if (team == null) return false;
		var v = team.getNameTagVisibility();
		return v == Team.Visibility.NEVER || v == Team.Visibility.HIDE_FOR_OWN_TEAM;
	}

	private static boolean hasServerTag(Entity entity) {
		if (entity.isVehicle()) return true;
		var level = entity.level();
		if (level == null) return false;
		AABB box = entity.getBoundingBox();
		AABB above = new AABB(box.minX - 0.3, box.maxY - 0.2, box.minZ - 0.3, box.maxX + 0.3, box.maxY + 1.4, box.maxZ + 0.3);
		for (Entity e : level.getEntities(entity, above)) {
			if (e instanceof Display.TextDisplay) return true;
			if (e instanceof ArmorStand stand && stand.isInvisible() && stand.isCustomNameVisible()) return true;
		}
		return false;
	}
}
