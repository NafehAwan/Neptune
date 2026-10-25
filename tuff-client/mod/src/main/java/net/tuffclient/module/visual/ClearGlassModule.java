package net.tuffclient.module.visual;

import java.util.ArrayList;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.tuffclient.TuffClient;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;

/**
 * Glass without the streaks: a small resource pack inside Glade (clean glass
 * and stained glass, frame only), switched on and off with this module.
 */
public final class ClearGlassModule extends Module {
	public ClearGlassModule() {
		super("clear_glass", "Clear Glass", "Glass and stained glass without the streaks. Reloads textures when switched.", Category.VISUAL, false);
	}

	/** Adds the pack to the game's list (off until the module turns it on). */
	public static void register() {
		FabricLoader.getInstance().getModContainer(TuffClient.MOD_ID).ifPresent(mod -> ResourceManagerHelper.registerBuiltinResourcePack(
				Identifier.fromNamespaceAndPath(TuffClient.MOD_ID, "clear_glass"), mod, Component.literal("Glade Clear Glass"), ResourcePackActivationType.NORMAL));
	}

	@Override
	protected void onEnable() {
		setPack(true);
	}

	@Override
	protected void onDisable() {
		setPack(false);
	}

	private static void setPack(boolean on) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) return;
		var repo = mc.getResourcePackRepository();
		repo.reload();
		String id = repo.getAvailableIds().stream().filter(s -> s.endsWith("clear_glass")).findFirst().orElse(null);
		if (id == null) {
			TuffClient.LOGGER.warn("Glade's Clear Glass pack isn't in the pack list");
			return;
		}
		var ids = new ArrayList<>(repo.getSelectedIds());
		if (on == ids.contains(id)) return;
		if (on) ids.add(id);
		else ids.remove(id);
		repo.setSelected(ids);
		mc.options.updateResourcePacks(repo);
	}
}
