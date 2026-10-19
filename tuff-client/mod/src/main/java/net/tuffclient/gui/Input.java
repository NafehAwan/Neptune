package net.tuffclient.gui;

/** Mouse and keyboard events in Glade's own form, the same on every Minecraft version. */
public final class Input {
	private Input() {
	}

	/** A mouse button at a position (GUI pixels). */
	public record Click(double x, double y, int button) {
	}

	/** A key press. {@code key} uses the game's key codes (InputConstants). */
	public record Key(int key, int scancode, int modifiers, boolean isEscape) {
	}

	/** A typed character. */
	public record Typed(int codepoint, boolean isAllowedChatCharacter) {
		public String codepointAsString() {
			return Character.toString(codepoint);
		}
	}
}
