package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.NumberSetting;

/** Your own nametag in third person, hiding nametags, and their background. */
public final class NametagsModule extends Module {
	public final BoolSetting own = add(new BoolSetting("own", "Show your own nametag in third person", true));
	public final BoolSetting hideAll = add(new BoolSetting("hide_all", "Hide all nametags", false));
	public final NumberSetting background = add(new NumberSetting("background", "Background opacity (%)", 25, 0, 100, 5));

	public NametagsModule() {
		super("nametags", "Nametags", "See your own nametag, hide nametags, or change their background.", Category.VISUAL, false);
	}
}
