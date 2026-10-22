package net.gladebot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

/**
 * How the bot fights, one tick at a time, the way a Neth Pot player does.
 *
 * Hits: every swing is a crit (jump, hit on the way down, not sprinting) or a
 * sprint hit (with a W-tap after), never a sweep. Movement: full strafes that
 * switch direction, spacing at the edge of reach, stepping back when you're
 * charged and it isn't. Gamesense: it reads what you're doing ({@link Opponent})
 * and punishes it (rushes you while you pot, eat or mend; hits you out of the
 * air when you jump in for a crit), makes space before potting unless it's an
 * emergency, gaps in safe windows, double pops, and sometimes deivi pops:
 * fights on into a totem pop, then instantly throws two heals, strength and
 * speed and goes aggressive. It sees you {@link Tier#reaction} ticks late and
 * turns at most {@link Tier#aimSpeed} degrees a tick, like a person.
 */
public final class BotBrain {
	private enum Mode { READY, FIGHT, HEAL, BUFF, MEND, EAT, REFILL, POP, IDLE }

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private final BotPlayer bot;
	private final RandomSource random;
	private final ArrayDeque<Vec3> seen = new ArrayDeque<>();
	private final Opponent opp = new Opponent();
	private ServerPlayer target;
	private Mode mode = Mode.READY;
	private int modeTicks, busy, strafeDir = 1, strafeTicks, wtap, potsLeft, critTicks, aggro, retreat;
	private boolean critJump, drinking, swingIsCrit, swingPicked, deiviCommit, lastOffTotem;
	private int lastPopTick = -1000, age;
	private float yawWobble, pitchWobble;
	/** What to throw after a pop, in order. */
	private final List<Predicate<ItemStack>> popQueue = new ArrayList<>();

	// Stats, shown after a match and checked by the self test.
	public int hits, crits, sprintHits, sweeps, pots, totemSwaps, pops, doublePops, deiviPops, gaps, punishes, strafeFlips, mends;

	public BotBrain(BotPlayer bot, ServerPlayer target) {
		this.bot = bot;
		this.target = target;
		this.random = bot.getRandom();
	}

	public void startFight() {
		enter(Mode.FIGHT);
	}

	/** Stops fighting (the match is over). */
	public void stop() {
		target = null;
		enter(Mode.IDLE);
	}

	public String stats() {
		return "hits=" + hits + " crits=" + crits + " sprintHits=" + sprintHits + " sweeps=" + sweeps + " pots=" + pots + " gaps=" + gaps
				+ " pops=" + pops + " doublePops=" + doublePops + " deiviPops=" + deiviPops + " punishes=" + punishes + " strafeFlips=" + strafeFlips
				+ " mends=" + mends + " totemSwaps=" + totemSwaps;
	}

	void tick() {
		Tier tier = Settings.tier;
		age++;
		if (target == null || target.isRemoved() || !target.isAlive() || mode == Mode.IDLE) {
			move(0, 0, false, false);
			return;
		}
		if (busy > 0) busy--;
		if (aggro > 0) aggro--;
		seen.addLast(target.position());
		while (seen.size() > tier.reaction + 1) seen.removeFirst();
		Vec3 where = seen.peekFirst().add(0, target.getBbHeight() * 0.8, 0);
		double dist = bot.distanceTo(target);
		opp.update(bot, target);
		modeTicks++;
		watchPops(tier);
		offhand(tier, dist);
		switch (mode) {
			case READY -> ready(tier, where);
			case FIGHT -> fight(tier, where, dist);
			case HEAL -> heal(tier, where, dist);
			case BUFF -> buff(tier, where, dist);
			case MEND -> mend(tier, where, dist);
			case EAT -> eat(tier, where, dist);
			case REFILL -> refillMode(tier, where);
			case POP -> afterPop(tier, where);
			default -> move(0, 0, false, false);
		}
	}

	private void enter(Mode next) {
		mode = next;
		modeTicks = 0;
		drinking = false;
		critJump = false;
		swingPicked = false;
		retreat = 0;
	}

	// ------------------------------------------------------------------ totem pops

	/** A totem that was in the offhand and is now gone popped: pot up (and go aggressive after a deivi pop). */
	private void watchPops(Tier tier) {
		boolean offTotem = bot.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
		if (lastOffTotem && bot.getOffhandItem().isEmpty() && mode != Mode.READY) {
			pops++;
			if (age - lastPopTick < 60) doublePops++;
			lastPopTick = age;
			popQueue.clear();
			popQueue.add(BotBrain::isHealing);
			popQueue.add(BotBrain::isHealing);
			if (deiviCommit) {
				deiviPops++;
				popQueue.add(BotBrain::isStrength);
				popQueue.add(BotBrain::isSpeed);
			}
			GladeBot.LOGGER.info("[gladebot] {} popped a totem{}", bot.getName().getString(), deiviCommit ? " (deivi pop)" : "");
			enter(Mode.POP);
		}
		lastOffTotem = offTotem;
	}

	/** Right after a pop: throw the queued pots at your feet, then rush if it was a deivi pop. */
	private void afterPop(Tier tier, Vec3 where) {
		turn(yawTo(where), 90, tier.aimSpeed * 3f);
		move(-0.5f, strafeDir, false, false);
		if (busy > 0) return;
		while (!popQueue.isEmpty() && hotbar(popQueue.get(0)) < 0 && !has(popQueue.get(0))) popQueue.remove(0);
		if (popQueue.isEmpty() || modeTicks > 40) {
			if (deiviCommit) aggro = 80;
			deiviCommit = false;
			enter(Mode.FIGHT);
			return;
		}
		Predicate<ItemStack> next = popQueue.get(0);
		int slot = hotbar(next);
		if (slot < 0) {
			refill(next, tier);
			return;
		}
		select(slot);
		if (bot.getXRot() < 75) return;
		use();
		pots++;
		popQueue.remove(0);
		busy = tier.potGap;
	}

	// ------------------------------------------------------------------ modes

	/** The countdown: face the player and splash buffs at your feet. */
	private void ready(Tier tier, Vec3 where) {
		move(0, 0, false, false);
		Predicate<ItemStack> buff = busy > 0 || bot.isUsingItem() ? null : neededBuff(true);
		if (buff == null) {
			if (!bot.isUsingItem()) aim(where, tier.aimSpeed, 0);
			return;
		}
		int slot = hotbar(buff);
		if (slot < 0) {
			refill(buff, tier);
			return;
		}
		select(slot);
		if (bot.getMainHandItem().is(Items.SPLASH_POTION)) {
			turn(bot.getYRot(), 90, tier.aimSpeed * 2.5f);
			if (bot.getXRot() < 75) return;
		}
		use();
		busy = tier.potGap + 1;
	}

	private void fight(Tier tier, Vec3 where, double dist) {
		float hp = bot.getHealth();
		boolean threatened = dist < 3.6 && opp.charge > 0.7f && !opp.busy();
		boolean space = dist > 3.5 || opp.busy() || opp.retreating;

		// 1. low: pot (with space, or right away in an emergency), unless going for a deivi pop
		if (hp <= tier.healAt && has(BotBrain::isHealing) && !deiviCommit) {
			boolean totemReady = bot.getOffhandItem().is(Items.TOTEM_OF_UNDYING) || hotbar(s -> s.is(Items.TOTEM_OF_UNDYING)) >= 0;
			if (hp <= tier.totemAt && totemReady && opp.hp <= 16 && random.nextFloat() < tier.deivi) {
				// fight on into the pop, then pot up and rush
				deiviCommit = true;
				GladeBot.LOGGER.info("[gladebot] {} commits to a deivi pop", bot.getName().getString());
			} else {
				enter(Mode.HEAL);
				potsLeft = hp > tier.burstAt ? 1 : hp <= 3 ? tier.burst : Math.max(2, tier.burst - 1);
				// make space first unless it's an emergency or there already is some
				retreat = hp <= tier.burstAt || space ? 0 : 8 + random.nextInt(5);
				return;
			}
		}
		// 2. buffs running out, with room
		if ((dist > 5 || opp.busy()) && neededBuff(false) != null && !threatened) {
			enter(Mode.BUFF);
			return;
		}
		// 3. gap in a safe window
		if (bot.getOffhandItem().is(Items.GOLDEN_APPLE) && bot.getEffect(MobEffects.ABSORPTION) == null && hp > tier.healAt && !threatened
				&& (space || age - lastPopTick < 120) && aggro == 0) {
			enter(Mode.EAT);
			return;
		}
		// 4. mend when they're far or busy (not while they're low: keep the pressure on)
		if (Settings.mend && armorLeft() < tier.mendAt && has(s -> s.is(Items.EXPERIENCE_BOTTLE)) && hp > 10 && opp.hp > 10
				&& (dist > 6 || opp.busy() && dist > 4)) {
			enter(Mode.MEND);
			return;
		}
		// 5. hotbar almost out of pots: refill with room
		if (countHotbar(BotBrain::isHealing) <= 1 && has(BotBrain::isHealing) && (dist > 4.5 || opp.busy()) && !threatened) {
			enter(Mode.REFILL);
			return;
		}

		selectSword();
		aim(where, tier.aimSpeed, tier.jitter);
		boolean punish = opp.busy() || aggro > 0 || opp.hp < 8 || deiviCommit;

		// strafing: full A/D, switching direction, and a dodge when they're about to swing
		if (--strafeTicks <= 0 || (opp.charge > 0.95f && dist < 3.3 && random.nextFloat() < 0.08f)) {
			strafeTicks = tier.strafeMin + random.nextInt(Math.max(1, tier.strafeMax - tier.strafeMin));
			strafeDir = -strafeDir;
			strafeFlips++;
		}
		float charge = bot.getAttackStrengthScale(0.5f);
		float strafe = strafeDir * (dist < 4.5 ? tier.strafe : tier.strafe * 0.6f);
		float forward;
		boolean sprint;
		if (wtap > 0) {
			wtap--;
			forward = 0;
			sprint = false;
		} else if (punish) {
			forward = 1;
			sprint = true;
		} else if (charge < 0.6f && opp.charge > 0.8f && dist < 3.2) {
			// not ready and they are: back off out of their reach
			forward = -1;
			sprint = false;
		} else if (dist < 2.0) {
			forward = -0.6f;
			sprint = false;
		} else {
			forward = 1;
			sprint = true;
		}

		boolean inReach = dist <= tier.reach && bot.hasLineOfSight(target);
		boolean jump = false;
		boolean canHurt = target.invulnerableTime <= 10;

		// punish crits: they jump in, hit them out of the air with a sprint hit
		if (opp.critJump && inReach && charge >= 0.9f && canHurt && busy == 0) {
			bot.setSprinting(true);
			swing(true);
			move(1, strafe, true, false);
			return;
		}

		// pick this swing: crit or sprint hit
		if (!swingPicked && charge > 0.3f) {
			swingPicked = true;
			swingIsCrit = random.nextFloat() < tier.critChance;
		}
		if (swingIsCrit && swingPicked) {
			if (critJump) {
				critTicks++;
				if (bot.onGround() && critTicks > 3) critJump = false;
			} else if (bot.onGround() && charge >= 0.45f && dist < tier.reach + 1.5) {
				critJump = true;
				critTicks = 0;
				jump = true;
			}
			boolean falling = !bot.onGround() && bot.getDeltaMovement().y < 0 && bot.fallDistance > 0;
			if (falling && inReach && charge >= tier.charge && canHurt && busy == 0) {
				bot.setSprinting(false);
				swing(false);
				critJump = false;
				move(forward, strafe, false, false);
				return;
			}
			// no sprint in the air before a crit, so the hit isn't a sprint hit
			if (critJump) sprint = false;
		} else if (swingPicked && inReach && charge >= tier.charge && canHurt && busy == 0 && bot.isSprinting() && wtap == 0) {
			swing(true);
			if (random.nextFloat() < tier.wtapChance) wtap = 1 + random.nextInt(2);
			move(forward, strafe, sprint, false);
			return;
		}
		move(forward, strafe, sprint, jump);
	}

	/** Back off and splash heal at your feet; make space first unless it's an emergency. */
	private void heal(Tier tier, Vec3 where, double dist) {
		if (retreat > 0) {
			retreat--;
			// turn and sprint away
			turn(yawTo(where) + 180, 0, tier.aimSpeed * 2f);
			move(1, strafeDir * 0.4f, true, false);
			if (bot.getHealth() <= tier.burstAt || dist > 5) retreat = 0;
			return;
		}
		turn(yawTo(where), 90, tier.aimSpeed * 2.5f);
		move(-1, strafeDir * 0.5f, false, false);
		if (modeTicks > 80) {
			enter(Mode.FIGHT);
			return;
		}
		if (busy > 0) return;
		if (potsLeft <= 0) {
			enter(Mode.FIGHT);
			return;
		}
		int slot = hotbar(BotBrain::isHealing);
		if (slot < 0) {
			if (!refill(BotBrain::isHealing, tier)) enter(Mode.FIGHT);
			return;
		}
		select(slot);
		if (bot.getXRot() >= 75) {
			use();
			pots++;
			potsLeft--;
			busy = tier.potGap;
		}
	}

	/** Re-buff strength or speed with room to spare: splash at your feet, or drink. */
	private void buff(Tier tier, Vec3 where, double dist) {
		Predicate<ItemStack> buff = neededBuff(false);
		boolean splash = buff != null && bot.getMainHandItem().is(Items.SPLASH_POTION) && buff.test(bot.getMainHandItem());
		turn(yawTo(where), splash ? 90 : 0, tier.aimSpeed * (splash ? 2.5f : 1));
		move(-0.6f, strafeDir * tier.strafe, false, false);
		if ((dist < 3.2 && !opp.busy()) || modeTicks > 80) {
			bot.stopUsingItem();
			enter(Mode.FIGHT);
			return;
		}
		if (drinking) {
			if (!bot.isUsingItem()) enter(Mode.FIGHT);
			return;
		}
		if (busy > 0) return;
		if (buff == null) {
			enter(Mode.FIGHT);
			return;
		}
		int slot = hotbar(buff);
		if (slot < 0) {
			if (!refill(buff, tier)) enter(Mode.FIGHT);
			return;
		}
		select(slot);
		if (bot.getMainHandItem().is(Items.SPLASH_POTION)) {
			if (bot.getXRot() < 75) return;
			use();
			pots++;
			busy = tier.potGap;
			return;
		}
		use();
		drinking = true;
	}

	/** Eat the offhand gapple, sword still in hand, strafing. */
	private void eat(Tier tier, Vec3 where, double dist) {
		selectSword();
		aim(where, tier.aimSpeed, tier.jitter);
		if (--strafeTicks <= 0) {
			strafeTicks = tier.strafeMin + random.nextInt(Math.max(1, tier.strafeMax - tier.strafeMin));
			strafeDir = -strafeDir;
			strafeFlips++;
		}
		move(dist < 3.5 ? -0.8f : 0, strafeDir * tier.strafe, false, false);
		boolean rushed = dist < 2.8 && opp.charge > 0.8f && bot.getHealth() <= tier.healAt + 4;
		if (bot.getHealth() <= tier.burstAt || rushed || modeTicks > 60) {
			bot.stopUsingItem();
			enter(Mode.FIGHT);
			return;
		}
		if (drinking) {
			if (!bot.isUsingItem()) {
				gaps++;
				enter(Mode.FIGHT);
			}
			return;
		}
		ItemStack apple = bot.getOffhandItem();
		if (!apple.is(Items.GOLDEN_APPLE)) {
			enter(Mode.FIGHT);
			return;
		}
		bot.gameMode.useItem(bot, bot.level(), apple, InteractionHand.OFF_HAND);
		drinking = true;
	}

	/** Back off and throw XP bottles down to repair armour. */
	private void mend(Tier tier, Vec3 where, double dist) {
		turn(yawTo(where), 90, tier.aimSpeed * 2.5f);
		move(-1, strafeDir * 0.4f, false, false);
		boolean coming = dist < 4.5 && !opp.busy() || opp.approaching && dist < 5.5;
		if (coming || armorLeft() > 0.97f || modeTicks > 300) {
			enter(Mode.FIGHT);
			return;
		}
		if (busy > 0) return;
		int slot = hotbar(s -> s.is(Items.EXPERIENCE_BOTTLE));
		if (slot < 0) {
			if (!refill(s -> s.is(Items.EXPERIENCE_BOTTLE), tier)) enter(Mode.FIGHT);
			return;
		}
		select(slot);
		if (bot.getXRot() >= 75) {
			use();
			mends++;
			busy = 1;
		}
	}

	/** Open the inventory and pull 2-3 pots onto the hotbar. */
	private void refillMode(Tier tier, Vec3 where) {
		turn(yawTo(where), 0, tier.aimSpeed);
		move(-0.5f, strafeDir * 0.5f, false, false);
		if (busy > 0) return;
		if (modeTicks > 1 && (countHotbar(BotBrain::isHealing) >= 3 || !refill(BotBrain::isHealing, tier))) {
			enter(Mode.FIGHT);
			return;
		}
		if (modeTicks == 1) refill(BotBrain::isHealing, tier);
		busy = Math.max(2, tier.refill / 3);
	}

	// ------------------------------------------------------------------ hands

	/** One swing: counted as a crit, sprint hit or (never on purpose) sweep, by the game's own rules. */
	private void swing(boolean sprintHit) {
		boolean crit = bot.fallDistance > 0 && !bot.onGround() && !bot.isSprinting();
		boolean sprinting = bot.isSprinting();
		if (crit) crits++;
		else if (sprinting) sprintHits++;
		else sweeps++;
		if (opp.busy() || opp.critJump) punishes++;
		bot.attack(target);
		bot.swing(InteractionHand.MAIN_HAND);
		bot.resetAttackStrengthTicker();
		hits++;
		swingPicked = false;
	}

	private void use() {
		ItemStack stack = bot.getMainHandItem();
		if (!stack.isEmpty()) bot.gameMode.useItem(bot, bot.level(), stack, InteractionHand.MAIN_HAND);
	}

	private void select(int slot) {
		bot.getInventory().setSelectedSlot(slot);
	}

	private void selectSword() {
		int slot = hotbar(s -> s.is(ItemTags.SWORDS));
		if (slot >= 0) select(slot);
	}

	private int hotbar(Predicate<ItemStack> what) {
		var inv = bot.getInventory();
		for (int i = 0; i < 9; i++) if (what.test(inv.getItem(i))) return i;
		return -1;
	}

	private int countHotbar(Predicate<ItemStack> what) {
		var inv = bot.getInventory();
		int n = 0;
		for (int i = 0; i < 9; i++) if (what.test(inv.getItem(i))) n++;
		return n;
	}

	private boolean has(Predicate<ItemStack> what) {
		var inv = bot.getInventory();
		for (int i = 0; i < 36; i++) if (what.test(inv.getItem(i))) return true;
		return false;
	}

	/** Moves a matching item from the inventory to the hotbar (it takes a moment, like a person). */
	private boolean refill(Predicate<ItemStack> what, Tier tier) {
		var inv = bot.getInventory();
		int from = -1;
		for (int i = 9; i < 36 && from < 0; i++) if (what.test(inv.getItem(i))) from = i;
		if (from < 0) return false;
		int to = -1;
		for (int i = 1; i < 9 && to < 0; i++) if (inv.getItem(i).isEmpty()) to = i;
		if (to < 0) {
			// swap out something that isn't the sword, a totem or what we're after
			for (int i = 8; i >= 1 && to < 0; i--) {
				ItemStack s = inv.getItem(i);
				if (!s.is(ItemTags.SWORDS) && !s.is(Items.TOTEM_OF_UNDYING) && !what.test(s)) to = i;
			}
		}
		if (to < 0) return false;
		ItemStack a = inv.getItem(from), b = inv.getItem(to);
		inv.setItem(to, a);
		inv.setItem(from, b);
		busy = tier.refill;
		return true;
	}

	/**
	 * The offhand, as an HT3 player runs it: a gapple normally; a totem
	 * swapped in from the hotbar when low; a second totem staged on the hotbar
	 * when it's getting close, so after a pop the next one is in within a few
	 * ticks (a double pop); the gapple back once safe.
	 */
	private void offhand(Tier tier, double dist) {
		if (busy > 0 || bot.isUsingItem() || mode == Mode.READY || mode == Mode.POP) return;
		ItemStack off = bot.getOffhandItem();
		float hp = bot.getHealth();
		boolean danger = hp <= tier.totemAt && dist < 6 || deiviCommit;
		if (danger) {
			if (!off.is(Items.TOTEM_OF_UNDYING)) {
				int slot = hotbar(s -> s.is(Items.TOTEM_OF_UNDYING));
				if (slot >= 0) {
					swapOffhand(slot);
					busy = 2;
				} else {
					refill(s -> s.is(Items.TOTEM_OF_UNDYING), tier);
				}
				return;
			}
			// stage the next totem for a double pop
			if (hotbar(s -> s.is(Items.TOTEM_OF_UNDYING)) < 0 && has(s -> s.is(Items.TOTEM_OF_UNDYING))) refill(s -> s.is(Items.TOTEM_OF_UNDYING), tier);
			return;
		}
		boolean safe = hp > tier.totemAt + 4 || dist > 6;
		if (safe && !off.is(Items.GOLDEN_APPLE)) {
			int slot = hotbar(s -> s.is(Items.GOLDEN_APPLE));
			if (slot >= 0) {
				swapOffhand(slot);
				busy = 2;
				return;
			}
			var inv = bot.getInventory();
			for (int i = 9; i < 36; i++) {
				ItemStack apples = inv.getItem(i);
				if (apples.is(Items.GOLDEN_APPLE)) {
					inv.setItem(i, off);
					bot.setItemSlot(EquipmentSlot.OFFHAND, apples);
					busy = tier.refill;
					return;
				}
			}
			if (off.isEmpty()) {
				int slot2 = hotbar(s -> s.is(Items.TOTEM_OF_UNDYING));
				if (slot2 >= 0) {
					swapOffhand(slot2);
					busy = 2;
					return;
				}
			}
		}
		// a totem ready on the hotbar for the next swap
		if (safe && !off.is(Items.TOTEM_OF_UNDYING) && hotbar(s -> s.is(Items.TOTEM_OF_UNDYING)) < 0 && has(s -> s.is(Items.TOTEM_OF_UNDYING))) {
			refill(s -> s.is(Items.TOTEM_OF_UNDYING), tier);
		}
	}

	/** Selects a hotbar slot and swaps it with the offhand (the F key). */
	private void swapOffhand(int slot) {
		var inv = bot.getInventory();
		if (inv.getItem(slot).is(Items.TOTEM_OF_UNDYING)) totemSwaps++;
		select(slot);
		ItemStack hand = inv.getItem(slot), off = bot.getOffhandItem();
		inv.setItem(slot, off);
		bot.setItemSlot(EquipmentSlot.OFFHAND, hand);
		lastOffTotem = hand.is(Items.TOTEM_OF_UNDYING);
	}

	// ------------------------------------------------------------------ what to throw

	static boolean isHealing(ItemStack s) {
		if (!s.is(Items.SPLASH_POTION)) return false;
		PotionContents c = s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		return c.is(Potions.HEALING) || c.is(Potions.STRONG_HEALING);
	}

	static boolean isStrength(ItemStack s) {
		if (!s.is(Items.POTION) && !s.is(Items.SPLASH_POTION)) return false;
		PotionContents c = s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		return c.is(Potions.STRENGTH) || c.is(Potions.STRONG_STRENGTH) || c.is(Potions.LONG_STRENGTH);
	}

	static boolean isSpeed(ItemStack s) {
		if (!s.is(Items.POTION) && !s.is(Items.SPLASH_POTION)) return false;
		PotionContents c = s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		return c.is(Potions.SWIFTNESS) || c.is(Potions.STRONG_SWIFTNESS) || c.is(Potions.LONG_SWIFTNESS);
	}

	static boolean isFireRes(ItemStack s) {
		if (!s.is(Items.POTION) && !s.is(Items.SPLASH_POTION)) return false;
		PotionContents c = s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		return c.is(Potions.FIRE_RESISTANCE) || c.is(Potions.LONG_FIRE_RESISTANCE);
	}

	/** The buff to use next (strength, then speed, and fire resistance before the fight), or null. */
	private Predicate<ItemStack> neededBuff(boolean start) {
		MobEffectInstance strength = bot.getEffect(MobEffects.STRENGTH);
		if ((strength == null || strength.getDuration() < 100) && has(BotBrain::isStrength)) return BotBrain::isStrength;
		MobEffectInstance speed = bot.getEffect(MobEffects.SPEED);
		if ((speed == null || speed.getDuration() < 100) && has(BotBrain::isSpeed)) return BotBrain::isSpeed;
		if (start && bot.getEffect(MobEffects.FIRE_RESISTANCE) == null && has(BotBrain::isFireRes)) return BotBrain::isFireRes;
		return null;
	}

	/** The worst armour piece's durability left, 0..1. */
	private float armorLeft() {
		float worst = 1;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack s = bot.getItemBySlot(slot);
			if (s.isEmpty() || s.getMaxDamage() <= 0) continue;
			worst = Math.min(worst, 1 - s.getDamageValue() / (float) s.getMaxDamage());
		}
		return worst;
	}

	// ------------------------------------------------------------------ looking and moving

	private float yawTo(Vec3 p) {
		Vec3 eye = bot.getEyePosition();
		return (float) (Mth.atan2(p.z - eye.z, p.x - eye.x) * Mth.RAD_TO_DEG) - 90f;
	}

	private void aim(Vec3 p, float speed, float jitter) {
		Vec3 eye = bot.getEyePosition();
		double dx = p.x - eye.x, dy = p.y - eye.y, dz = p.z - eye.z;
		float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
		if (jitter > 0 && random.nextInt(5) == 0) {
			yawWobble = (random.nextFloat() - 0.5f) * 2 * jitter;
			pitchWobble = (random.nextFloat() - 0.5f) * jitter;
		}
		turn(yawTo(p) + yawWobble, pitch + pitchWobble, speed);
	}

	private void turn(float yaw, float pitch, float speed) {
		float y = bot.getYRot() + Mth.clamp(Mth.wrapDegrees(yaw - bot.getYRot()), -speed, speed);
		float p = Mth.clamp(bot.getXRot() + Mth.clamp(pitch - bot.getXRot(), -speed, speed), -90, 90);
		bot.setYRot(y);
		bot.setXRot(p);
		bot.setYHeadRot(y);
	}

	private void move(float forward, float strafe, boolean sprint, boolean jump) {
		bot.zza = forward;
		bot.xxa = strafe;
		bot.setSprinting(sprint && forward > 0.5f);
		bot.setJumping(jump);
	}
}
