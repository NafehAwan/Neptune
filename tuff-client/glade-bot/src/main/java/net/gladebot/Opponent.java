package net.gladebot;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * What the bot reads off the player it's fighting, every tick: are they
 * potting, eating, mending (free hits: punish), jumping in for a crit, running,
 * how charged their sword is and how much health they have.
 */
final class Opponent {
	Vec3 velocity = Vec3.ZERO;
	private Vec3 last;
	boolean eating, potting, mending, critJump, retreating, approaching;
	float charge, hp;
	/** Ticks since they were last busy (potting, eating or mending). */
	int sinceBusy = 100;

	void update(BotPlayer bot, ServerPlayer p) {
		Vec3 pos = p.position();
		velocity = last == null ? Vec3.ZERO : pos.subtract(last);
		last = pos;
		ItemStack using = p.isUsingItem() ? p.getUseItem() : ItemStack.EMPTY;
		eating = using.is(Items.GOLDEN_APPLE) || using.is(Items.ENCHANTED_GOLDEN_APPLE) || using.is(Items.POTION);
		boolean lookingDown = p.getXRot() > 60;
		boolean potInHand = p.getMainHandItem().is(Items.SPLASH_POTION);
		boolean xpInHand = p.getMainHandItem().is(Items.EXPERIENCE_BOTTLE);
		boolean thrownPot = false, thrownXp = false;
		for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(4), e -> e instanceof Projectile pr && pr.getOwner() == p)) {
			String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
			if (type.contains("potion")) thrownPot = true;
			if (type.contains("experience_bottle")) thrownXp = true;
		}
		potting = thrownPot || lookingDown && potInHand;
		mending = thrownXp || lookingDown && xpInHand;
		Vec3 toBot = bot.position().subtract(pos);
		double d = toBot.horizontalDistance();
		double along = d < 1e-4 ? 0 : (velocity.x * toBot.x + velocity.z * toBot.z) / d;
		approaching = along > 0.05;
		retreating = along < -0.08;
		charge = p.getAttackStrengthScale(0.5f);
		// in the air, coming in, sword charged: going for a crit (a jump reset hop isn't)
		critJump = !p.onGround() && d < 4.5 && approaching && charge > 0.8f;
		hp = p.getHealth() + p.getAbsorptionAmount();
		sinceBusy = busy() ? 0 : sinceBusy + 1;
	}

	/** Doing something that leaves them open. */
	boolean busy() {
		return eating || potting || mending;
	}
}
