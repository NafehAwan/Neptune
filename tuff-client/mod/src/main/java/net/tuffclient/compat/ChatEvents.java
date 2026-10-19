// glade: minecraft >=1.21.5
package net.tuffclient.compat;

import java.io.File;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/** Click and hover actions for chat text (records since 1.21.5). */
public final class ChatEvents {
	private ChatEvents() {
	}

	public static ClickEvent copy(String text) {
		return new ClickEvent.CopyToClipboard(text);
	}

	public static ClickEvent openFile(File file) {
		return new ClickEvent.OpenFile(file);
	}

	public static ClickEvent runCommand(String command) {
		return new ClickEvent.RunCommand(command);
	}

	public static HoverEvent showText(Component text) {
		return new HoverEvent.ShowText(text);
	}
}
