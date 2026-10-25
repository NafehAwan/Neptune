package net.tuffclient.module.visual;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;

/** Potions get the enchantment glint, so they stand out in your hotbar. */
public final class ShinyPotsModule extends Module {
	private final BoolSetting splash = add(new BoolSetting("splash", "Splash potions", true));
	private final BoolSetting drink = add(new BoolSetting("drink", "Drinkable potions", true));
	private final BoolSetting lingering = add(new BoolSetting("lingering", "Lingering potions", false));

	public ShinyPotsModule() {
		super("shiny_pots", "Shiny Pots", "Potions get the enchantment glint so they stand out.", Category.VISUAL, false);
		requires("ItemStackMixin");
	}

	public boolean shines(ItemStack s) {
		return splash.get() && s.is(Items.SPLASH_POTION) || drink.get() && s.is(Items.POTION) || lingering.get() && s.is(Items.LINGERING_POTION);
	}
}
