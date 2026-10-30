// glade: minecraft >=1.21.11 <26.1
package net.tuffclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.tuffclient.TuffClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Damage Tint on armor: worn armor (every piece, the inner leggings layer and
 * trims too) flashes with the body when it's hit. Vanilla armor ignores the
 * damage flash, so while it shows the armor is drawn the way the body is.
 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class ArmorTintMixin {
	private static final String LAYERS = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V";
	private static final String SUBMIT = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V";

	/** The damage-flash overlay for the armor being drawn, or -1 to leave it as vanilla draws it. */
	@Unique
	private int glade$overlay = -1;

	@Inject(method = LAYERS, at = @At("HEAD"))
	private void glade$start(EquipmentClientInfo.LayerType layerType, ResourceKey<?> asset, Model<?> model, Object state, ItemStack stack, PoseStack pose,
			SubmitNodeCollector collector, int light, Identifier playerTexture, int outline, int order, CallbackInfo ci) {
		glade$overlay = -1;
		var modules = TuffClient.modules();
		if (modules == null || !modules.hitColor.isEnabled() || !modules.hitColor.armor.get()) return;
		if (state instanceof LivingEntityRenderState living && living.hasRedOverlay) glade$overlay = LivingEntityRenderer.getOverlayCoords(living, 0f);
	}

	/** The armor layer itself. */
	@Redirect(method = LAYERS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
	private RenderType glade$armorType(Identifier texture) {
		return glade$overlay >= 0 ? RenderTypes.entityCutoutNoCull(texture) : RenderTypes.armorCutoutNoCull(texture);
	}

	/** Trims (decal trims stay as they are, they sit right on the armor). */
	@Redirect(method = LAYERS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Sheets;armorTrimsSheet(Z)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
	private RenderType glade$trimType(boolean decal) {
		return glade$overlay >= 0 && !decal ? RenderTypes.entityCutoutNoCull(Sheets.ARMOR_TRIMS_SHEET) : Sheets.armorTrimsSheet(decal);
	}

	@ModifyArg(method = LAYERS, at = @At(value = "INVOKE", target = SUBMIT), index = 5)
	private int glade$flash(int overlay) {
		return glade$overlay >= 0 ? glade$overlay : overlay;
	}
}
