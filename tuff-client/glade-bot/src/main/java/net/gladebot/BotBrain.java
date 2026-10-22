package net.gladebot;

import java.util.ArrayDeque;
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
 * How the bot fights, one tick at a time, the way a Neth Pot player does:
 * keep pressure with sprint hits and W-taps, strafe, go for crits, back off
 * and splash heal when low, re-drink strength and speed, and mend armour
 * with XP when there's room. It sees you {@link Tier#reaction} ticks late
 * and turns at most {@link Tier#aimSpeed} degrees a tick, like a person.
 */
public final class BotBrain {
	private enum Mode { READY, FIGHT, HEAL, BUFF, MEND, IDLE }

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private final BotPlayer bot;
	private final RandomSource random;
	private final ArrayDeque<Vec3> seen = new ArrayDeque<>();
	private ServerPlayer target;
	private Mode mode = Mode.READY;
	private int modeTicks, busy, strafeDir = 1, strafeTicks, wtap, potsLeft, hitDelay, critTicks;
	private boolean critJump, drinking;
	private float yawWobble, pitchWobble;
	/** Hits landed and potions thrown (shown after a match, and used by tests). */
	public int hits, pots;

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

	void tick() {
		Tier tier = Settings.tier;
		if (target == null || target.isRemoved() || !target.isAlive() || mode == Mode.IDLE) {
			move(0, 0, false, false);
			return;
		}
		if (busy > 0) busy--;
		seen.addLast(target.position());
		while (seen.size() > tier.reaction + 1) seen.removeFirst();
		Vec3 where = seen.peekFirst().add(0, target.getBbHeight() * 0.8, 0);
		double dist = bot.distanceTo(target);
		modeTicks++;
		keepTotem(tier);
		switch (mode) {
			case READY -> ready(tier, where);
			case FIGHT -> fight(tier, where, dist);
			case HEAL -> heal(tier, where);
			case BUFF -> buff(tier, where, dist);
			case MEND -> mend(tier, where, dist);
			default -> move(0, 0, false, false);
		}
	}

	private void enter(Mode next) {
		mode = next;
		modeTicks = 0;
		drinking = false;
		critJump = false;
	}

	// ------------------------------------------------------------------ modes

	/** The countdown: face the player and drink buffs. */
	private void ready(Tier tier, Vec3 where) {
		aim(where, tier.aimSpeed, 0);
		move(0, 0, false, false);
		if (busy > 0 || bot.isUsingItem()) return;
		Predicate<ItemStack> buff = neededBuff();
		if (buff == null) return;
		int slot = hotbar(buff);
		if (slot < 0) {
			refill(buff, tier);
			return;
		}
		select(slot);
		use();
		busy = 2;
	}

	private void fight(Tier tier, Vec3 where, double dist) {
		if (bot.getHealth() <= tier.healAt && has(BotBrain::isHealing)) {
			enter(Mode.HEAL);
			potsLeft = bot.getHealth() <= tier.doublePotAt ? 2 : 1;
			return;
		}
		if (dist > 6 && neededBuff() != null) {
			enter(Mode.BUFF);
			return;
		}
		if (Settings.mend && dist > 7 && armorLeft() < tier.mendAt && has(s -> s.is(Items.EXPERIENCE_BOTTLE))) {
			enter(Mode.MEND);
			return;
		}
		selectSword();
		aim(where, tier.aimSpeed, tier.jitter);

		if (--strafeTicks <= 0) {
			strafeTicks = 8 + random.nextInt(22);
			if (random.nextFloat() < 0.6f) strafeDir = -strafeDir;
		}
		float strafe = strafeDir * tier.strafe;
		float forward;
		boolean sprint;
		if (wtap > 0) {
			wtap--;
			forward = 0;
			sprint = false;
		} else if (dist > 3.4) {
			forward = 1;
			sprint = true;
		} else if (dist < 2.2) {
			forward = -0.4f;
			sprint = false;
		} else {
			forward = 0.7f;
			sprint = true;
		}

		float charge = bot.getAttackStrengthScale(0.5f);
		boolean inReach = dist <= tier.reach && bot.hasLineOfSight(target);
		boolean jump = false;
		// crits: jump as the sword fills up, hit on the way down
		if (critJump) {
			critTicks++;
			if (bot.onGround() && critTicks > 3) critJump = false;
		} else if (bot.onGround() && dist < tier.reach + 1 && charge > 0.6f && random.nextFloat() < tier.critChance * 0.2f) {
			critJump = true;
			critTicks = 0;
			jump = true;
		}
		if (inReach && charge >= tier.charge && busy == 0) {
			boolean falling = !bot.onGround() && bot.getDeltaMovement().y < 0;
			if (!critJump || falling) {
				if (hitDelay > 0) {
					hitDelay--;
				} else {
					bot.setSprinting(sprint && !critJump);
					attack();
					critJump = false;
					hitDelay = random.nextInt(tier == Tier.LT2 ? 2 : 3);
					if (random.nextFloat() < tier.wtapChance) wtap = 1 + random.nextInt(2);
				}
			}
		}
		move(forward, strafe, sprint && !critJump, jump);
	}

	/** Back off looking down, and splash heal at your feet. */
	private void heal(Tier tier, Vec3 where) {
		turn(yawTo(where), 90, tier.aimSpeed * 2.5f);
		move(-1, strafeDir * 0.5f, false, false);
		if (modeTicks > 60) {
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
			busy = tier == Tier.LT3 ? 4 : 3;
		}
	}

	/** Drink strength or speed with room to spare. */
	private void buff(Tier tier, Vec3 where, double dist) {
		turn(yawTo(where), 0, tier.aimSpeed);
		move(-0.6f, strafeDir * tier.strafe, false, false);
		if (dist < 3.5 || modeTicks > 80) {
			bot.stopUsingItem();
			enter(Mode.FIGHT);
			return;
		}
		if (drinking) {
			if (!bot.isUsingItem()) enter(Mode.FIGHT);
			return;
		}
		if (busy > 0) return;
		Predicate<ItemStack> buff = neededBuff();
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
		use();
		drinking = true;
	}

	/** Back off and throw XP bottles down to repair armour. */
	private void mend(Tier tier, Vec3 where, double dist) {
		turn(yawTo(where), 90, tier.aimSpeed * 2.5f);
		move(-1, strafeDir * 0.4f, false, false);
		if (dist < 4.5 || armorLeft() > 0.92f || modeTicks > 100) {
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
			busy = 1;
		}
	}

	// ------------------------------------------------------------------ hands

	private void attack() {
		bot.attack(target);
		bot.swing(InteractionHand.MAIN_HAND);
		bot.resetAttackStrengthTicker();
		hits++;
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
		if (to < 0) to = 8;
		ItemStack a = inv.getItem(from), b = inv.getItem(to);
		inv.setItem(to, a);
		inv.setItem(from, b);
		busy = tier.refill;
		return true;
	}

	/** Puts a spare totem in the offhand when it's empty. */
	private void keepTotem(Tier tier) {
		if (!bot.getOffhandItem().isEmpty() || busy > 0) return;
		var inv = bot.getInventory();
		for (int i = 0; i < 36; i++) {
			if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
				bot.setItemSlot(EquipmentSlot.OFFHAND, inv.getItem(i));
				inv.setItem(i, ItemStack.EMPTY);
				busy = tier.refill;
				return;
			}
		}
	}

	// ------------------------------------------------------------------ what to drink

	private static boolean isHealing(ItemStack s) {
		if (!s.is(Items.SPLASH_POTION)) return false;
		PotionContents c = s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		return c.is(Potions.HEALING) || c.is(Potions.STRONG_HEALING);
	}

	private static boolean isStrength(ItemStack s) {
		if (!s.is(Items.POTION)) return false;
		PotionContents c = s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		return c.is(Potions.STRENGTH) || c.is(Potions.STRONG_STRENGTH) || c.is(Potions.LONG_STRENGTH);
	}

	private static boolean isSpeed(ItemStack s) {
		if (!s.is(Items.POTION)) return false;
		PotionContents c = s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		return c.is(Potions.SWIFTNESS) || c.is(Potions.STRONG_SWIFTNESS) || c.is(Potions.LONG_SWIFTNESS);
	}

	/** The buff to drink next (strength, then speed), or null. */
	private Predicate<ItemStack> neededBuff() {
		MobEffectInstance strength = bot.getEffect(MobEffects.STRENGTH);
		if ((strength == null || strength.getDuration() < 100) && has(BotBrain::isStrength)) return BotBrain::isStrength;
		MobEffectInstance speed = bot.getEffect(MobEffects.SPEED);
		if ((speed == null || speed.getDuration() < 100) && has(BotBrain::isSpeed)) return BotBrain::isSpeed;
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
