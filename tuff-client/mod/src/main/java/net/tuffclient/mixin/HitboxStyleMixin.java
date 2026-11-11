package net.tuffclient.mixin;

import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.tuffclient.TuffClient;
import net.tuffclient.module.visual.HitboxesModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hitboxes: line width, box colour, the colour of the box you're aiming at, and
 * hiding the look-direction arrow and the red eye-height line. Vanilla F3+B is
 * left alone while the module is off.
 */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class HitboxStyleMixin {
	private static final String CUBOID = "Lnet/minecraft/gizmos/Gizmos;cuboid(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/gizmos/GizmoStyle;)Lnet/minecraft/gizmos/GizmoProperties;";
	private static final String ARROW = "Lnet/minecraft/gizmos/Gizmos;arrow(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;I)Lnet/minecraft/gizmos/GizmoProperties;";

	@Unique
	private Entity glade$entity;

	@Unique
	private static HitboxesModule glade$module() {
		var modules = TuffClient.modules();
		return modules != null && modules.hitboxes.isEnabled() ? modules.hitboxes : null;
	}

	@Inject(method = "showHitboxes", at = @At("HEAD"))
	private void glade$entity(Entity entity, float partialTick, boolean server, CallbackInfo ci) {
		glade$entity = entity;
	}

	/** The entity's box. */
	@Redirect(method = "showHitboxes", at = @At(value = "INVOKE", target = CUBOID, ordinal = 0))
	private GizmoProperties glade$box(AABB box, GizmoStyle style) {
		HitboxesModule m = glade$module();
		if (m == null) return Gizmos.cuboid(box, style);
		return Gizmos.cuboid(box, GizmoStyle.stroke(m.boxColor(glade$entity, style.stroke()), m.width.get().floatValue()));
	}

	/** The red line at eye height. */
	@Redirect(method = "showHitboxes", at = @At(value = "INVOKE", target = CUBOID, ordinal = 2))
	private GizmoProperties glade$eye(AABB box, GizmoStyle style) {
		HitboxesModule m = glade$module();
		if (m == null) return Gizmos.cuboid(box, style);
		if (!m.eyeLine.get()) return null;
		return Gizmos.cuboid(box, GizmoStyle.stroke(style.stroke(), m.width.get().floatValue()));
	}

	/** The blue arrow showing where the entity looks. */
	@Redirect(method = "showHitboxes", at = @At(value = "INVOKE", target = ARROW, ordinal = 0))
	private GizmoProperties glade$look(Vec3 from, Vec3 to, int color) {
		HitboxesModule m = glade$module();
		if (m != null && !m.lookLine.get()) return null;
		return Gizmos.arrow(from, to, color);
	}
}
