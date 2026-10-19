// glade: minecraft >=26.1
package net.tuffclient.compat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** A short message above the hotbar. */
public final class Messages {
	private Messages() {
	}

	/** A line in chat that only you see. */
	public static void chat(LocalPlayer player, Component text) {
		player.sendSystemMessage(text);
	}

	public static void overlay(LocalPlayer player, Component text) {
		player.sendOverlayMessage(text);
	}
}
