package net.tuffclient.gui;

/**
 * Glade palette (ARGB), Grove Glass: soft paper, forest green and frosted
 * white panels. The accent follows the accent picked on the Theme page.
 * The HUD draws over the world, so it keeps its own light text colours.
 */
public final class Theme {
	public enum Accent {
		COPPER("Copper", 0xFFC9733E, 0xFFDB8A57, 0xFF9C5530),
		AMETHYST("Amethyst", 0xFF8A62D6, 0xFFA07FE2, 0xFF6E4AB0),
		MINT("Mint", 0xFF3BA57E, 0xFF4FC79A, 0xFF2E8B67),
		SKY("Sky", 0xFF3D8BD6, 0xFF5AA9F0, 0xFF2F6FAE),
		ROSE("Rose", 0xFFD25A75, 0xFFE8708A, 0xFFA8435A),
		GOLD("Gold", 0xFFC9971F, 0xFFE3B23C, 0xFF9C7A22),
		RED("Red", 0xFFD0413D, 0xFFE55A55, 0xFFA82020),
		FOREST("Forest", 0xFF2F6B4F, 0xFF3A7D5E, 0xFF24543E);

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

	public static final int BG = 0xFFF6F4EE;
	public static final int TEXT = 0xFF1F2A22;
	public static final int MUTED = 0xFF6B756C;
	public static final int FAINT = 0xFF879189;
	/** Text and icons drawn on the accent colour. */
	public static final int INK = 0xFFFFFFFF;
	public static final int ONLINE = 0xFF4ADE80;
	/** The title card's tagline. */
	public static final int TAGLINE = 0xFF47524A;
	public static final int OFFLINE = 0xFFC9A66B;
	public static final int DANGER = 0xFFC2463B;

	/** Frosted glass: translucent white with a brighter rim. */
	public static final int GLASS = 0x8CFFFFFF;
	public static final int GLASS_STRONG = 0xC8FFFFFF;
	public static final int GLASS_RIM = 0xC0FFFFFF;
	/** Hairlines and outlines on light surfaces. */
	public static final int LINE = 0x1A1F2A22;

	/** Mod menu surfaces. */
	public static final int PANEL = 0xD8F6F4EE;
	public static final int SIDEBAR = 0xC8FFFFFF;
	public static final int CARD = 0xA6FFFFFF;
	public static final int CARD_HOVER = 0xF0FFFFFF;
	/** Selected chip background. */
	public static final int CHIP = 0x242F6B4F;
	public static final int BADGE_NEW = 0xFF3FA35B;
	/** Switch track and icons when off. */
	public static final int OFF = 0xFFC9D0C8;
	public static final int ICON_OFF = 0xFFA3ADA5;

	/** HUD text over the world. */
	public static final int HUD_TEXT = 0xFFFFFFFF;
	public static final int HUD_MUTED = 0xFFB4BBB3;

	/** Accent colours; reassigned by {@link #apply}. Forest is the default. */
	public static int COPPER = Accent.FOREST.base;
	public static int COPPER_HI = Accent.FOREST.hi;
	public static int COPPER_LO = Accent.FOREST.lo;

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

	/**
	 * A small spring: state is {value, velocity}. Slightly underdamped, so it
	 * overshoots a touch and settles, which reads as lively rather than mechanical.
	 */
	public static float spring(float[] state, float target, float dt) {
		float step = 1 / 240f;
		for (float left = Math.min(dt, 0.1f); left > 0; left -= step) {
			float h = Math.min(step, left);
			float force = (target - state[0]) * 320 - state[1] * 22;
			state[1] += force * h;
			state[0] += state[1] * h;
		}
		return state[0];
	}

	/** Frame-rate independent easing towards a target. */
	public static float approach(float current, float target, float dt, float speed) {
		return current + (target - current) * (1 - (float) Math.exp(-speed * dt));
	}
}
