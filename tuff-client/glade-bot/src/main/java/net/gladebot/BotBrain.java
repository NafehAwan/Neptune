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
	private boolean critJump, drinking, swingIsCrit, swingPicked, deiviCommit;
	private int lastTotems = -1;
	/** Armour taken off while mending so the XP goes to the pieces that need it (put back when mending stops). */
	private final java.util.Map<EquipmentSlot, Integer> stashed = new java.util.EnumMap<>(EquipmentSlot.class);
	private int lastPopTick = -1000, age, lastDeivi = -100000, styleTicks;
	private boolean oppWasEating, jumpNow;
	/** XP bottles it allows itself this mend session, and ticks it's paused for (tests). */
	private int mendBudget, paused;
	/** How long a mend fight opens defensively. */
	private static final int OPENING = 600;
	/** The current playstyle (changes as the fight goes, see {@link #updateStyle}). */
	private Settings.Style play = Settings.Style.AGGRESSIVE;
	private float yawWobble, pitchWobble;
	/** What to throw after a pop, in order. */
	private final List<Predicate<ItemStack>> popQueue = new ArrayList<>();

	// Stats, shown after a match and checked by the self test.
	public int hits, crits, sprintHits, sweeps, pots, totemSwaps, pops, doublePops, deiviPops, gaps, punishes, strafeFlips, mends, jumpResets, styleSwitches;

	public BotBrain(BotPlayer bot, ServerPlayer target) {
		this.bot = bot;
		this.target = target;
		this.random = bot.getRandom();
	}

	public void startFight() {
		enter(Mode.FIGHT);
	}

	/** Stands still for a while (used by the self test to give the other bot room to mend). */
	public void pause(int ticks) {
		paused = ticks;
	}

	/** Stops fighting (the match is over). */
	public void stop() {
		target = null;
		enter(Mode.IDLE);
	}

	public String stats() {
		return "hits=" + hits + " crits=" + crits + " sprintHits=" + sprintHits + " sweeps=" + sweeps + " pots=" + pots + " gaps=" + gaps
				+ " pops=" + pops + " doublePops=" + doublePops + " deiviPops=" + deiviPops + " punishes=" + punishes + " strafeFlips=" + strafeFlips
				+ " mends=" + mends + " totemSwaps=" + totemSwaps + " jumpResets=" + jumpResets + " styleSwitches=" + styleSwitches;
	}

	void tick() {
		Tier tier = Settings.tier;
		age++;
		if (target == null || target.isRemoved() || !target.isAlive() || mode == Mode.IDLE) {
			move(0, 0, false, false);
			return;
		}
		if (paused > 0) {
			paused--;
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
		updateStyle();
		// jump reset: jump the moment you're hit, to take less knockback
		if (bot.hurtTime > 0 && bot.hurtTime == bot.hurtDuration && bot.onGround() && mode != Mode.READY
				&& random.nextFloat() < Settings.jumpResetChance(tier)) {
			jumpNow = true;
		}
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
		if (jumpNow) {
			bot.setJumping(true);
			jumpResets++;
			jumpNow = false;
		}
	}

	/**
	 * Playstyle changes as the fight goes: crits while you're gapping, strafing
	 * when you stop, and otherwise (on Random) a switch between aggressive,
	 * crit-heavy and strafe-heavy every 5-15 seconds.
	 */
	private void updateStyle() {
		Settings.Style before = play;
		boolean eating = opp.eating;
		if (eating) {
			play = Settings.Style.CRIT;
			styleTicks = 40;
		} else if (oppWasEating) {
			play = Settings.Style.STRAFE;
			styleTicks = 60 + random.nextInt(60);
		} else if (Settings.mend && age < OPENING && (Settings.style == Settings.Style.RANDOM || Settings.style == Settings.Style.DEFENSIVE)) {
			// a mend fight opens defensively: hold distance, counter-hit, mend when it can
			play = Settings.Style.DEFENSIVE;
			styleTicks = 0;
		} else if (--styleTicks <= 0) {
			if (Settings.style == Settings.Style.RANDOM) {
				Settings.Style[] pick = {Settings.Style.AGGRESSIVE, Settings.Style.CRIT, Settings.Style.STRAFE};
				play = pick[random.nextInt(pick.length)];
				if (!Settings.mend) {
					// agro is a durability war: ahead on durability, push with crits; behind, strafe and don't trade
					float mine = averageArmor(), theirs = opp.dura;
					if (theirs < mine - 0.05f) play = random.nextBoolean() ? Settings.Style.CRIT : Settings.Style.AGGRESSIVE;
					else if (mine < theirs - 0.05f) play = Settings.Style.STRAFE;
				}
				styleTicks = 100 + random.nextInt(200);
			} else {
				play = Settings.style;
				styleTicks = 200;
			}
		}
		oppWasEating = eating;
		if (play != before) styleSwitches++;
	}

	private void enter(Mode next) {
		unstash();
		mode = next;
		modeTicks = 0;
		drinking = false;
		critJump = false;
		swingPicked = false;
		retreat = 0;
	}

	// ------------------------------------------------------------------ totem pops

	/** One totem fewer than last tick means one popped: pot up (and go aggressive after a deivi pop). */
	private void watchPops(Tier tier) {
		int totems = countAll(s -> s.is(Items.TOTEM_OF_UNDYING));
		boolean popped = lastTotems >= 0 && totems < lastTotems && mode != Mode.READY;
		lastTotems = totems;
		if (popped) {
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
	}

	private int countAll(Predicate<ItemStack> what) {
		var inv = bot.getInventory();
		int n = 0;
		for (int i = 0; i < 36; i++) if (what.test(inv.getItem(i))) n += inv.getItem(i).getCount();
		if (what.test(bot.getOffhandItem())) n += bot.getOffhandItem().getCount();
		return n;
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
		if (hp <= Settings.healAt(tier) && has(BotBrain::isHealing) && !deiviCommit) {
			boolean totemReady = bot.getOffhandItem().is(Items.TOTEM_OF_UNDYING) || hotbar(s -> s.is(Items.TOTEM_OF_UNDYING)) >= 0;
			float deiviChance = Settings.deivi == 0 ? 0 : Settings.deivi == 1 ? tier.deivi * 0.5f : tier.deivi;
			int cooldown = Settings.deivi == 2 ? 400 : 1200;
			float oppLow = Settings.deivi == 2 ? 16 : 12;
			if (hp <= tier.totemAt && totemReady && opp.hp <= oppLow && age - lastDeivi > cooldown && random.nextFloat() < deiviChance) {
				// fight on into the pop, then pot up and rush
				deiviCommit = true;
				lastDeivi = age;
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
		// agro: gaps only when it needs the health (after a pop, or getting low), so it doesn't hand out free crits
		boolean wantsGap = Settings.mend || hp <= 12 || age - lastPopTick < 120;
		if (wantsGap && bot.getOffhandItem().is(Items.GOLDEN_APPLE) && bot.getEffect(MobEffects.ABSORPTION) == null && hp > Settings.healAt(tier) && !threatened
				&& (space || age - lastPopTick < 120) && aggro == 0) {
			enter(Mode.EAT);
			return;
		}
		// 4. mend when they're far or busy (not while they're low: keep the pressure on)
		boolean armourLow = armorLeft() < tier.mendAt || averageArmor() < tier.mendAt + 0.1f;
		if (Settings.mend && armourLow && has(s -> s.is(Items.EXPERIENCE_BOTTLE)) && hp > 10 && opp.hp > 10
				&& (dist > 6 || opp.busy() && dist > 4 || play == Settings.Style.DEFENSIVE && dist > 5)) {
			enter(Mode.MEND);
			mendBudget = mendBudget();
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

		boolean strafeStyle = play == Settings.Style.STRAFE;
		// strafing: full A/D, switching direction, and a dodge when they're about to swing
		if (--strafeTicks <= 0 || (opp.charge > 0.95f && dist < 3.3 && random.nextFloat() < (strafeStyle ? 0.2f : 0.08f))) {
			int min = strafeStyle ? Math.max(3, tier.strafeMin / 2) : tier.strafeMin, max = strafeStyle ? Math.max(min + 1, tier.strafeMax / 2) : tier.strafeMax;
			strafeTicks = min + random.nextInt(Math.max(1, max - min));
			strafeDir = -strafeDir;
			strafeFlips++;
		}
		float charge = bot.getAttackStrengthScale(0.5f);
		float strafe = strafeDir * (dist < 4.5 ? Settings.strafe(tier) : Settings.strafe(tier) * 0.6f);
		float forward;
		boolean sprint;
		if (wtap > 0) {
			wtap--;
			forward = 0;
			sprint = false;
		} else if (punish || play == Settings.Style.AGGRESSIVE) {
			forward = 1;
			sprint = true;
		} else if (play == Settings.Style.DEFENSIVE) {
			// hold the edge of reach: back off unless charged and they come in, drift in when far
			if (dist < tier.reach + 0.3 && (charge < 0.9f || opp.charge > 0.8f && !opp.approaching)) {
				forward = -0.6f;
				sprint = false;
			} else if (dist > 4.2) {
				forward = 0.6f;
				sprint = false;
			} else {
				forward = charge > 0.9f && opp.approaching ? 1 : 0;
				sprint = forward > 0;
			}
		} else if (strafeStyle && dist < 3.4 && dist > 2.2) {
			// circle at the edge of reach instead of pressing in
			forward = charge > 0.85f ? 1 : 0.3f;
			sprint = charge > 0.85f;
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

		boolean fallingNow = !bot.onGround() && bot.getDeltaMovement().y < 0 && bot.fallDistance > 0;
		// already falling (a crit jump or a jump reset) with the sword ready: crit
		if (fallingNow && inReach && charge >= tier.charge && canHurt && busy == 0) {
			bot.setSprinting(false);
			swing(false);
			critJump = false;
			move(forward, strafe, false, false);
			return;
		}
		// punish crits: they jump in, hit them out of the air with a sprint hit
		if (opp.critJump && !critJump && inReach && charge >= 0.9f && canHurt && busy == 0) {
			bot.setSprinting(true);
			swing(true);
			move(1, strafe, true, false);
			return;
		}

		// pick this swing: crit or sprint hit
		if (!swingPicked && charge > 0.3f) {
			swingPicked = true;
			float base = Settings.crit(tier);
			if (!Settings.mend && Settings.critPct < 0) base = Math.min(0.95f, base + 0.1f);
			float critShare = Settings.critPct >= 0 ? base : switch (play) {
				case CRIT -> 0.95f;
				case AGGRESSIVE -> base * 0.75f;
				case STRAFE -> base * 0.9f;
				case DEFENSIVE -> base * 0.85f;
				default -> base;
			};
			// they're eating: free crits
			if (opp.eating) critShare = 1;
			swingIsCrit = random.nextFloat() < critShare;
		}
		if (swingIsCrit && swingPicked) {
			if (critJump) {
				critTicks++;
				if (bot.onGround() && critTicks > 3) critJump = false;
			} else if (bot.onGround() && charge >= 0.45f && dist < tier.reach + 0.6) {
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
			// no sprint in the air before a crit, so the hit isn't a sprint hit;
			// keep moving in so they're in reach on the way down
			if (critJump) {
				sprint = false;
				if (dist > 2.4) forward = 1;
			}
		} else if (swingPicked && inReach && charge >= tier.charge && canHurt && busy == 0 && bot.isSprinting() && wtap == 0) {
			swing(true);
			if (Settings.wtap && random.nextFloat() < tier.wtapChance) wtap = 1 + random.nextInt(2);
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
		move(-0.6f, strafeDir * Settings.strafe(tier), false, false);
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
		move(dist < 3.5 ? -0.8f : 0, strafeDir * Settings.strafe(tier), false, false);
		boolean rushed = dist < 2.8 && opp.charge > 0.8f && bot.getHealth() <= Settings.healAt(tier) + 4;
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
		if (coming || armorLeft() > 0.97f || mendBudget <= 0 || modeTicks > 400) {
			GladeBot.LOGGER.info("[gladebot] {} mended: {}", bot.getName().getString(), armorReport());
			enter(Mode.FIGHT);
			return;
		}
		if (busy > 0) return;
		balanceArmor();
		int slot = hotbar(s -> s.is(Items.EXPERIENCE_BOTTLE));
		if (slot < 0) {
			if (!refill(s -> s.is(Items.EXPERIENCE_BOTTLE), tier)) enter(Mode.FIGHT);
			return;
		}
		select(slot);
		if (bot.getXRot() >= 75) {
			use();
			mends++;
			mendBudget--;
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

	private static float left(ItemStack s) {
		return s.isEmpty() || s.getMaxDamage() <= 0 ? 1 : 1 - s.getDamageValue() / (float) s.getMaxDamage();
	}

	/**
	 * Mending puts each XP orb on a random damaged piece, so bigger pieces fall
	 * behind. While mending, a piece that's well ahead of the worst one comes
	 * off into the inventory (so the XP goes elsewhere) and goes back on as soon
	 * as the others catch up or mending stops.
	 */
	private void balanceArmor() {
		var inv = bot.getInventory();
		// put back pieces the others have caught up with
		for (var it = stashed.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			ItemStack piece = inv.getItem(e.getValue());
			if (left(piece) <= armorLeft() + 0.03f || armorLeft() > 0.9f) {
				bot.setItemSlot(e.getKey(), piece);
				inv.setItem(e.getValue(), ItemStack.EMPTY);
				it.remove();
			}
		}
		float worst = armorLeft();
		for (EquipmentSlot slot : ARMOR) {
			ItemStack piece = bot.getItemBySlot(slot);
			if (piece.isEmpty() || left(piece) < worst + 0.08f) continue;
			int free = -1;
			for (int i = 9; i < 36 && free < 0; i++) if (inv.getItem(i).isEmpty()) free = i;
			if (free < 0) return;
			inv.setItem(free, piece);
			bot.setItemSlot(slot, ItemStack.EMPTY);
			stashed.put(slot, free);
		}
	}

	/** Puts every piece taken off for mending back on. */
	private void unstash() {
		if (stashed.isEmpty()) return;
		var inv = bot.getInventory();
		for (var e : stashed.entrySet()) {
			ItemStack piece = inv.getItem(e.getValue());
			if (!piece.isEmpty()) {
				bot.setItemSlot(e.getKey(), piece);
				inv.setItem(e.getValue(), ItemStack.EMPTY);
			}
		}
		stashed.clear();
	}

	/** Average durability left over the armour pieces, 0..1. */
	private float averageArmor() {
		float sum = 0;
		int n = 0;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack s = bot.getItemBySlot(slot);
			if (s.isEmpty() || s.getMaxDamage() <= 0) continue;
			sum += 1 - s.getDamageValue() / (float) s.getMaxDamage();
			n++;
		}
		return n == 0 ? 1 : sum / n;
	}

	/**
	 * Bottles to use this session: what the armour needs (a bottle gives about
	 * 7 XP, and Mending fixes 2 durability per XP on a random damaged piece, so
	 * every piece comes back together), but at most a third of what's left so
	 * there's XP for later in the fight.
	 */
	private int mendBudget() {
		int missing = 0;
		for (EquipmentSlot slot : ARMOR) missing += bot.getItemBySlot(slot).getDamageValue();
		int need = (int) Math.ceil(missing / 14.0);
		int bottles = 0;
		var inv = bot.getInventory();
		for (int i = 0; i < 36; i++) if (inv.getItem(i).is(Items.EXPERIENCE_BOTTLE)) bottles += inv.getItem(i).getCount();
		return Math.max(1, Math.min(need, Math.max(6, bottles / 3)));
	}

	/** Each piece's durability left, for the log. */
	public String armorReport() {
		StringBuilder b = new StringBuilder();
		for (EquipmentSlot slot : ARMOR) {
			ItemStack s = piece(slot);
			if (s.isEmpty() || s.getMaxDamage() <= 0) continue;
			b.append(slot.getName()).append('=').append(Math.round(100 * (1 - s.getDamageValue() / (float) s.getMaxDamage()))).append("% ");
		}
		return b.toString().trim();
	}

	/** An armour piece, worn or taken off for mending. */
	private ItemStack piece(EquipmentSlot slot) {
		Integer at = stashed.get(slot);
		return at != null ? bot.getInventory().getItem(at) : bot.getItemBySlot(slot);
	}

	/** The worst worn armour piece's durability left, 0..1. */
	private float armorLeft() {
		float worst = 1;
		for (EquipmentSlot slot : ARMOR) {
			if (stashed.containsKey(slot)) continue;
			ItemStack s = bot.getItemBySlot(slot);
			if (s.isEmpty() || s.getMaxDamage() <= 0) continue;
			worst = Math.min(worst, left(s));
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
