package net.tuffclient.legacy;

import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Glade for Minecraft 1.8.9 (Legacy Fabric). */
public final class GladeLegacy implements ClientModInitializer {
	public static final String MOD_ID = "tuff";
	public static final Logger LOGGER = LogManager.getLogger("Glade");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Glade loaded for Minecraft 1.8.9");
	}
}
