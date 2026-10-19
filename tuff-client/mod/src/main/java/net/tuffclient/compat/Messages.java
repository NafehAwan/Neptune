// glade: minecraft >=26.1
package net.tuffclient.compat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** A short message above the hotbar. */
public final class Messages {
	private Messages() {
	}

	public static void overlay(LocalPlayer player, Component text) {
		player.sendOverlayMessage(text);
	}
}
