package net.tuffclient.gui;

/**
 * Tuff palette (ARGB). The accent colours follow the accent picked on the
 * Theme page; everything else is the fixed tuff-stone base.
 */
public final class Theme {
	public enum Accent {
		COPPER("Copper", 0xFFD9824B, 0xFFF0A877, 0xFF9C5530),
		AMETHYST("Amethyst", 0xFFA67CF0, 0xFFC4A6F7, 0xFF6E4AB0),
		MINT("Mint", 0xFF4FC79A, 0xFF8FE3B0, 0xFF2E8B67),
		SKY("Sky", 0xFF5AA9F0, 0xFF8EC9FF, 0xFF2F6FAE),
		ROSE("Rose", 0xFFE8708A, 0xFFF4A3B5, 0xFFA8435A),
		GOLD("Gold", 0xFFE3B23C, 0xFFF2D06B, 0xFF9C7A22),
		RED("Red", 0xFFE53935, 0xFFFF5A5A, 0xFFA82020);

		public final String label;
		public final int base;
		public final int hi;
		public final int lo;

		Accent(String label, int base, int hi, int lo) {
			this.label = label;
			this.base = base;
			this.hi = hi;
			this.lo = lo;
		}
	}

	public static final int BG = 0xFF141614;
	public static final int SURFACE = 0xFF1E211F;
	public static final int SURFACE_2 = 0xFF262A27;
	public static final int SURFACE_3 = 0xFF2F3430;
	public static final int BORDER = 0xFF2C302D;
	public static final int BORDER_STRONG = 0xFF3A3F3B;
	public static final int TEXT = 0xFFE8E6E1;
	public static final int MUTED = 0xFF8B8F88;
	public static final int FAINT = 0xFF5F645D;
	public static final int INK = 0xFF1A0F08;
	public static final int ONLINE = 0xFF6FBF73;
	public static final int DANGER = 0xFFD9584B;
	/** Status bars on mod cards. */
	public static final int ENABLED = 0xFF2E9A55;
	public static final int ENABLED_HI = 0xFF39B565;
	public static final int DISABLED = 0xFFA8373A;
	public static final int DISABLED_HI = 0xFFC44448;

	/** Mod menu surfaces. */
	public static final int PANEL = 0xDC0F0F11;
	public static final int SIDEBAR = 0xE80B0B0D;
	public static final int CARD = 0xC01C1C20;
	public static final int CARD_HOVER = 0xD826262B;
	public static final int CHIP = 0xFF2A2A30;
	public static final int BADGE_NEW = 0xFF2FB34A;

	/** Accent colours; reassigned by {@link #apply}. Red is the default. */
	public static int COPPER = Accent.RED.base;
	public static int COPPER_HI = Accent.RED.hi;
	public static int COPPER_LO = Accent.RED.lo;

	private Theme() {
	}

	public static void apply(Accent accent) {
		COPPER = accent.base;
		COPPER_HI = accent.hi;
		COPPER_LO = accent.lo;
	}

	public static int alpha(int argb, float a) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, a)));
		return (alpha << 24) | (argb & 0xFFFFFF);
	}

	public static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		int al = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
		return (al << 24) | (r << 16) | (g << 8) | bl;
	}

	public static boolean inside(double mx, double my, double x1, double y1, double x2, double y2) {
		return mx >= x1 && mx < x2 && my >= y1 && my < y2;
	}

	/** Frame-rate independent easing towards a target. */
	public static float approach(float current, float target, float dt, float speed) {
		return current + (target - current) * (1 - (float) Math.exp(-speed * dt));
	}
}
