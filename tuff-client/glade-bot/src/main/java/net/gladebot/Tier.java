package net.gladebot;

/**
 * How well the bot plays. The numbers are starting points, to be tuned from
 * clips of real players at each tier.
 */
public enum Tier {
	// LT3 is the old LT2 with the settings tuned in game (crit 83%, jump reset 35%,
	// strafe 89%, pots at 3 hearts); MT3 and HT3 step up from it. All of them pot late
	// (a burst at 2 hearts), keep a gapple in the offhand and swap a totem in when low,
	// strafe in long runs (half a second to a second and a half each way, like a person).
	//          reaction aimSpeed jitter reach  charge crit  wtap  strafe healAt burstAt burst potGap mendAt refill totemAt deivi strafeMin strafeMax jumpReset
	LT3("LT3", 1, 55f, 1.0f, 3.25, 0.97f, 0.83f, 0.85f, 0.89f, 6f, 4f, 4, 2, 0.55f, 5, 9f, 0.70f, 10, 28, 0.35f),
	MT3("MT3", 1, 62f, 0.8f, 3.30, 0.97f, 0.87f, 0.88f, 0.93f, 6f, 4f, 4, 2, 0.55f, 4, 9f, 0.75f, 9, 25, 0.50f),
	HT3("HT3", 0, 70f, 0.6f, 3.35, 0.98f, 0.90f, 0.90f, 0.96f, 6f, 4f, 4, 2, 0.55f, 3, 10f, 0.80f, 8, 22, 0.65f);

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
	/** Chance it jump resets when hit. */
	public final float jumpReset;

	Tier(String label, int reaction, float aimSpeed, float jitter, double reach, float charge, float critChance, float wtapChance, float strafe,
			float healAt, float burstAt, int burst, int potGap, float mendAt, int refill, float totemAt, float deivi, int strafeMin, int strafeMax, float jumpReset) {
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
		this.jumpReset = jumpReset;
	}

	public static Tier parse(String s) {
		// saved settings from before the tiers were renamed: LT2 is now LT3
		if ("LT2".equalsIgnoreCase(s)) return LT3;
		for (Tier t : values()) if (t.name().equalsIgnoreCase(s)) return t;
		return null;
	}
}
