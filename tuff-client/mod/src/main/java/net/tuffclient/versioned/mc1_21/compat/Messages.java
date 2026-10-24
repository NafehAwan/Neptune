// glade: minecraft <26.1
package net.tuffclient.compat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** A short message above the hotbar. */
public final class Messages {
	private Messages() {
	}

	/** A line in chat that only you see. */
	public static void chat(LocalPlayer player, Component text) {
		net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(text);
	}

	public static void overlay(LocalPlayer player, Component text) {
		player.displayClientMessage(text, true);
	}
}
