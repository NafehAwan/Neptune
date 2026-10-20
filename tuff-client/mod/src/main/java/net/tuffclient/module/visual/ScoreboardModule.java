package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;

/** The sidebar scoreboard: hide it, or drop its dark background. */
public final class ScoreboardModule extends Module {
	public final BoolSetting hide = add(new BoolSetting("hide", "Hide the scoreboard", false));
	public final BoolSetting background = add(new BoolSetting("background", "Background", false)).showIf(() -> !hide.get());

	public ScoreboardModule() {
		super("scoreboard", "Scoreboard", "Hide the sidebar scoreboard or remove its background.", Category.VISUAL, false);
	}

	public boolean hidden() {
		return isEnabled() && hide.get();
	}

	public boolean noBackground() {
		return isEnabled() && !background.get();
	}
}
