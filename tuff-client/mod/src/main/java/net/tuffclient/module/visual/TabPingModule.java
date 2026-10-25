package net.tuffclient.module.visual;

import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;

/** The player list (Tab): ping as a number, and player heads on every server. */
public final class TabPingModule extends Module {
	public final BoolSetting pingNumber = add(new BoolSetting("ping_number", "Ping as a number", true));
	public final BoolSetting heads = add(new BoolSetting("heads", "Always show player heads", true)).showIf(TabPingModule::headsSupported);
	private static Boolean headsSupported;

	public TabPingModule() {
		super("tab_ping", "Tab Editor", "Ping as a colored number and player heads on every server, in the Tab list.", Category.VISUAL, false);
		requires("TabPingMixin");
	}

	public boolean numbers() {
		return isEnabled() && pingNumber.get();
	}

	/** Whether this Minecraft version has the heads hook. */
	private static boolean headsSupported() {
		if (headsSupported == null) headsSupported = TabPingModule.class.getClassLoader().getResource("net/tuffclient/mixin/TabHeadsMixin.class") != null;
		return headsSupported;
	}

	public boolean alwaysHeads() {
		return isEnabled() && heads.get();
	}

	public static int color(int ms) {
		return ms <= 0 ? 0xFFAAAAAA : ms <= 75 ? 0xFF55FF55 : ms <= 150 ? 0xFFFFFF55 : ms <= 300 ? 0xFFFFAA00 : 0xFFFF5555;
	}
}
