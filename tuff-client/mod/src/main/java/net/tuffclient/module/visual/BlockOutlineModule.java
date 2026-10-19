package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.ColorSetting;
import net.tuffclient.setting.NumberSetting;

/** The outline around the block you're looking at: its colour and thickness. */
public final class BlockOutlineModule extends Module {
	public final ColorSetting color = add(new ColorSetting("color", "Outline color", 0xCC4FC79A, true));
	public final NumberSetting width = add(new NumberSetting("width", "Thickness", 2, 1, 8, 0.5));

	public BlockOutlineModule() {
		super("block_outline", "Block Outline", "Color and thickness of the outline on the block you look at.", Category.VISUAL, false);
	}
}
