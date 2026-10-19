package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;

/** Dropped items lie flat on the ground instead of floating and spinning. */
public final class ItemPhysicsModule extends Module {
	public final BoolSetting randomTurn = add(new BoolSetting("random_turn", "Each item turned a different way", true));

	public ItemPhysicsModule() {
		super("item_physics", "Item Physics", "Dropped items lie flat on the ground instead of spinning.", Category.VISUAL, false);
		requires("ItemEntityRendererMixin");
	}
}
