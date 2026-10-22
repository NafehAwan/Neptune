package net.gladebot;

/**
 * How well the bot plays. The numbers are starting points, to be tuned from
 * clips of real players at each tier.
 */
public enum Tier {
	// HT3 is measured from a real HT3 mend fight: it pots late (one pot under 4 hearts,
	// a burst at 2.5 hearts, a pot every 3 ticks, looking straight down), keeps a gapple
	// in the offhand and swaps a totem in when low, mends whenever there's room, and
	// takes about half a second to pull more pots from its inventory.
	//          reaction aimSpeed jitter reach  charge crit  wtap  strafe healAt burstAt burst potGap mendAt refill totemAt deivi strafeMin strafeMax
	LT3("LT3", 3, 34f, 2.5f, 3.05, 0.93f, 0.65f, 0.65f, 1.0f, 8f, 5f, 3, 3, 0.48f, 9, 7f, 0.40f, 6, 22),
	HT3("HT3", 2, 44f, 1.6f, 3.15, 0.95f, 0.78f, 0.75f, 1.0f, 8f, 5f, 4, 2, 0.50f, 7, 8f, 0.55f, 5, 18),
	LT2("LT2", 1, 55f, 1.0f, 3.25, 0.97f, 0.88f, 0.85f, 1.0f, 7f, 5f, 4, 2, 0.55f, 5, 9f, 0.70f, 4, 15);

	public final String label;
	/** Ticks between the player moving and the bot reacting. */
	public final int reaction;
	/** Most degrees the bot can turn in one tick, and random aim wobble. */
	public final float aimSpeed, jitter;
	/** Centre-to-centre distance the bot attacks from. */
	public final double reach;
	/** Sword charge (0..1) the bot waits for before hitting. */
	public final float charge;
	public final float critChance, wtapChance, strafe;
	/** Health at which it throws one pot, and at which it throws a burst. */
	public final float healAt, burstAt;
	/** Most pots in a burst, and ticks between them. */
	public final int burst, potGap;
	/** Armour durability left (0..1, worst piece) at which it starts mending. */
	public final float mendAt;
	/** Ticks it takes to move a potion from the inventory to the hotbar. */
	public final int refill;
	/** Health at which it swaps a totem into the offhand (in place of the gapple). */
	public final float totemAt;
	/** Chance it goes for a deivi pop (fight on into a totem pop, then pot up and rush) when low with a totem. */
	public final float deivi;
	/** Ticks between strafe direction changes. */
	public final int strafeMin, strafeMax;

	Tier(String label, int reaction, float aimSpeed, float jitter, double reach, float charge, float critChance, float wtapChance, float strafe,
			float healAt, float burstAt, int burst, int potGap, float mendAt, int refill, float totemAt, float deivi, int strafeMin, int strafeMax) {
		this.label = label;
		this.reaction = reaction;
		this.aimSpeed = aimSpeed;
		this.jitter = jitter;
		this.reach = reach;
		this.charge = charge;
		this.critChance = critChance;
		this.wtapChance = wtapChance;
		this.strafe = strafe;
		this.healAt = healAt;
		this.burstAt = burstAt;
		this.burst = burst;
		this.potGap = potGap;
		this.mendAt = mendAt;
		this.refill = refill;
		this.totemAt = totemAt;
		this.deivi = deivi;
		this.strafeMin = strafeMin;
		this.strafeMax = strafeMax;
	}

	public static Tier parse(String s) {
		for (Tier t : values()) if (t.name().equalsIgnoreCase(s)) return t;
		return null;
	}
}
