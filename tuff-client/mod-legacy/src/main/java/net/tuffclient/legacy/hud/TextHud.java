package net.tuffclient.legacy.hud;

/** A one-line HUD element: an optional label and a value. */
public abstract class TextHud extends HudModule {
	private final String labelText;

	protected TextHud(String id, String name, String description, String labelText, double x, double y, boolean enabled) {
		super(id, name, description, x, y, enabled);
		this.labelText = labelText;
	}

	protected abstract String value(boolean preview);

	@Override
	public int width() {
		return pairWidth(labelText, value(true)) + 8;
	}

	@Override
	public int height() {
		return 16;
	}

	@Override
	protected void draw(boolean preview) {
		pair(labelText, value(preview), 4, 4);
	}
}
