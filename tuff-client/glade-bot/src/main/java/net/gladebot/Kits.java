package net.gladebot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Neth Pot kits: the default one, applying a kit to a player (or the bot), and
 * saving a player's current inventory as a kit.
 *
 * A kit is 41 slots: 0-8 hotbar, 9-35 inventory, 36-39 boots/legs/chest/helmet, 40 offhand.
 */
public final class Kits {
	public static final int SIZE = 41;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

	private Kits() {
	}

	/** The default Neth Pot kit (with or without mending XP). */
	public static ItemStack[] defaultKit(HolderLookup.Provider registries, boolean mend) {
		ItemStack[] kit = new ItemStack[SIZE];
		java.util.Arrays.fill(kit, ItemStack.EMPTY);
		kit[0] = enchant(registries, Items.NETHERITE_SWORD, Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1);
		kit[1] = potion(Items.POTION, Potions.STRONG_STRENGTH);
		kit[2] = potion(Items.POTION, Potions.STRONG_SWIFTNESS);
		for (int i = 3; i <= 7; i++) kit[i] = potion(Items.SPLASH_POTION, Potions.STRONG_HEALING);
		kit[8] = mend ? new ItemStack(Items.EXPERIENCE_BOTTLE, 64) : potion(Items.SPLASH_POTION, Potions.STRONG_HEALING);
		// the rest of the inventory: spare buffs, then healing
		kit[9] = potion(Items.POTION, Potions.STRONG_STRENGTH);
		kit[10] = potion(Items.POTION, Potions.STRONG_SWIFTNESS);
		kit[11] = potion(Items.POTION, Potions.LONG_FIRE_RESISTANCE);
		kit[12] = potion(Items.POTION, Potions.STRONG_STRENGTH);
		kit[13] = potion(Items.POTION, Potions.STRONG_SWIFTNESS);
		if (mend) kit[14] = new ItemStack(Items.EXPERIENCE_BOTTLE, 64);
		for (int i = mend ? 15 : 14; i <= 35; i++) kit[i] = potion(Items.SPLASH_POTION, Potions.STRONG_HEALING);
		kit[36] = armor(registries, Items.NETHERITE_BOOTS);
		kit[37] = armor(registries, Items.NETHERITE_LEGGINGS);
		kit[38] = armor(registries, Items.NETHERITE_CHESTPLATE);
		kit[39] = armor(registries, Items.NETHERITE_HELMET);
		return kit;
	}

	private static ItemStack armor(HolderLookup.Provider registries, Item item) {
		return enchant(registries, item, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1);
	}

	private static ItemStack potion(Item item, Holder<Potion> potion) {
		return PotionContents.createItemStack(item, potion);
	}

	@SuppressWarnings("unchecked")
	private static ItemStack enchant(HolderLookup.Provider registries, Item item, Object... pairs) {
		ItemStack stack = new ItemStack(item);
		var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
		for (int i = 0; i < pairs.length; i += 2) {
			stack.enchant(lookup.getOrThrow((ResourceKey<Enchantment>) pairs[i]), (Integer) pairs[i + 1]);
		}
		return stack;
	}

	/** The kit to give: the saved one, or the default. */
	public static ItemStack[] kitFor(HolderLookup.Provider registries, boolean bot) {
		JsonElement saved = bot ? Settings.botKit : Settings.playerKit;
		if (saved != null) {
			ItemStack[] kit = decode(registries, saved);
			if (kit != null) return kit;
		}
		return defaultKit(registries, Settings.mend);
	}

	/** Clears a player's inventory and gives them a kit. */
	public static void apply(ServerPlayer player, ItemStack[] kit) {
		var inv = player.getInventory();
		inv.clearContent();
		for (int i = 0; i < 36; i++) inv.setItem(i, kit[i].copy());
		for (int i = 0; i < 4; i++) player.setItemSlot(ARMOR[i], kit[36 + i].copy());
		player.setItemSlot(EquipmentSlot.OFFHAND, kit[40].copy());
		inv.setSelectedSlot(0);
		player.containerMenu.broadcastChanges();
	}

	/** A player's current inventory, as a kit. */
	public static ItemStack[] capture(ServerPlayer player) {
		ItemStack[] kit = new ItemStack[SIZE];
		var inv = player.getInventory();
		for (int i = 0; i < 36; i++) kit[i] = inv.getItem(i).copy();
		for (int i = 0; i < 4; i++) kit[36 + i] = player.getItemBySlot(ARMOR[i]).copy();
		kit[40] = player.getItemBySlot(EquipmentSlot.OFFHAND).copy();
		return kit;
	}

	public static JsonElement encode(HolderLookup.Provider registries, ItemStack[] kit) {
		RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
		JsonArray out = new JsonArray();
		for (int i = 0; i < kit.length; i++) {
			if (kit[i].isEmpty()) continue;
			JsonObject slot = new JsonObject();
			slot.addProperty("slot", i);
			slot.add("item", ItemStack.CODEC.encodeStart(ops, kit[i]).getOrThrow());
			out.add(slot);
		}
		return out;
	}

	public static ItemStack[] decode(HolderLookup.Provider registries, JsonElement json) {
		try {
			RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
			ItemStack[] kit = new ItemStack[SIZE];
			java.util.Arrays.fill(kit, ItemStack.EMPTY);
			for (JsonElement e : json.getAsJsonArray()) {
				JsonObject slot = e.getAsJsonObject();
				int i = slot.get("slot").getAsInt();
				if (i >= 0 && i < SIZE) kit[i] = ItemStack.CODEC.parse(ops, slot.get("item")).result().orElse(ItemStack.EMPTY);
			}
			return kit;
		} catch (RuntimeException e) {
			GladeBot.LOGGER.warn("Saved kit is broken, using the default: {}", e.toString());
			return null;
		}
	}
}
