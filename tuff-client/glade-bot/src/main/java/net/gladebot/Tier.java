package net.gladebot;

/**
 * How well the bot plays. The numbers are starting points, to be tuned from
 * clips of real players at each tier.
 */
public enum Tier {
	//          reaction aimSpeed jitter reach  charge critChance wtapChance strafe healAt doublePotAt mendAt refill
	LT3("LT3", 5, 22f, 4.0f, 2.95, 0.92f, 0.35f, 0.40f, 0.55f, 8f, 5f, 0.45f, 8),
	HT3("HT3", 4, 30f, 3.0f, 3.05, 0.95f, 0.50f, 0.60f, 0.70f, 9f, 6f, 0.50f, 6),
	LT2("LT2", 3, 38f, 2.0f, 3.15, 0.97f, 0.65f, 0.75f, 0.85f, 10f, 7f, 0.55f, 4);

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
	/** Health at which it pots, and at which it throws two. */
	public final float healAt, doublePotAt;
	/** Armour durability left (0..1) at which it backs off to mend. */
	public final float mendAt;
	/** Ticks it takes to move a potion from the inventory to the hotbar. */
	public final int refill;

	Tier(String label, int reaction, float aimSpeed, float jitter, double reach, float charge, float critChance, float wtapChance, float strafe,
			float healAt, float doublePotAt, float mendAt, int refill) {
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
		this.doublePotAt = doublePotAt;
		this.mendAt = mendAt;
		this.refill = refill;
	}

	public static Tier parse(String s) {
		for (Tier t : values()) if (t.name().equalsIgnoreCase(s)) return t;
		return null;
	}
}
