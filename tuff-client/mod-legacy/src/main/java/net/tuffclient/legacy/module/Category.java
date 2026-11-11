package net.tuffclient.legacy.module;

public enum Category {
	HUD("HUD"), UTILITY("Utility"), VISUAL("Visual");

	public final String label;

	Category(String label) {
		this.label = label;
	}
}
