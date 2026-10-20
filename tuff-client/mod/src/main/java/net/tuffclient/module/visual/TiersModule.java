package net.tuffclient.module.visual;

import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.tuffclient.gui.Fonts;
import net.tuffclient.module.Category;
import net.tuffclient.module.Module;
import net.tuffclient.setting.BoolSetting;
import net.tuffclient.setting.ChoiceSetting;
import net.tuffclient.tiers.TierService;
import net.tuffclient.tiers.TierService.Rank;
import net.tuffclient.tiers.TierService.Site;

/**
 * Tiers from MCTiers and PvPTiers next to players' nametags: pick a gamemode
 * (or their best one) for each list, and which side of the name it goes on.
 */
public final class TiersModule extends Module {
	private static final String[] MODES = {"Off", "Best", "Vanilla", "Sword", "Axe", "Pot", "Neth Pot", "SMP", "UHC", "Mace"};
	private static final String[] MCTIERS_KEYS = {null, "*", "vanilla", "sword", "axe", "pot", "nethop", "smp", "uhc", "mace"};
	private static final String[] PVPTIERS_KEYS = {null, "*", "crystal", "sword", "axe", "pot", "neth_pot", "smp", "uhc", "mace"};

	public final ChoiceSetting mctiers = add(new ChoiceSetting("mctiers", "MCTiers gamemode", 1, MODES));
	public final ChoiceSetting mctiersSide = add(new ChoiceSetting("mctiers_side", "MCTiers side", 1, "Left", "Right")).showIf(() -> mctiers.get() != 0);
	public final ChoiceSetting pvptiers = add(new ChoiceSetting("pvptiers", "PvPTiers gamemode", 0, MODES));
	public final ChoiceSetting pvptiersSide = add(new ChoiceSetting("pvptiers_side", "PvPTiers side", 0, "Left", "Right")).showIf(() -> pvptiers.get() != 0);
	public final BoolSetting icons = add(new BoolSetting("icons", "Gamemode icons", true));
	public final BoolSetting retired = add(new BoolSetting("retired", "Show retired tiers", true));
	public final BoolSetting self = add(new BoolSetting("self", "On your own nametag", true));

	private static final Identifier ICON_FONT = Identifier.fromNamespaceAndPath("tuff", "tiers");

	public TiersModule() {
		super("tiers", "Tiers", "Shows MCTiers and PvPTiers ranks next to nametags, for the gamemode you pick.", Category.VISUAL, false);
	}

	/** The nametag with tiers added on the chosen sides. */
	public Component decorate(Player player, Component name) {
		if (!isEnabled()) return name;
		if (!self.get() && player == net.minecraft.client.Minecraft.getInstance().player) return name;
		MutableComponent left = Component.empty(), right = Component.empty();
		boolean anyLeft = false, anyRight = false;
		for (Site site : Site.values()) {
			ChoiceSetting mode = site == Site.MCTIERS ? mctiers : pvptiers;
			if (mode.get() == 0) continue;
			String key = (site == Site.MCTIERS ? MCTIERS_KEYS : PVPTIERS_KEYS)[mode.get()];
			Map<String, Rank> ranks = TierService.ranks(site, player.getScoreboardName(), player.getUUID());
			if (ranks == null || ranks.isEmpty()) continue;
			String shown = key;
			Rank rank;
			if (key.equals("*")) {
				shown = best(ranks);
				rank = shown == null ? null : ranks.get(shown);
			} else {
				rank = ranks.get(key);
			}
			if (rank == null || (rank.retired() && !retired.get())) continue;
			Component part = part(shown, rank);
			boolean onLeft = (site == Site.MCTIERS ? mctiersSide : pvptiersSide).get() == 0;
			if (onLeft) {
				if (anyLeft) left.append(" ");
				left.append(part);
				anyLeft = true;
			} else {
				if (anyRight) right.append(" ");
				right.append(part);
				anyRight = true;
			}
		}
		if (!anyLeft && !anyRight) return name;
		Component bar = Component.literal(" | ").withStyle(s -> s.withColor(0x8A8F8B));
		MutableComponent out = Component.empty();
		if (anyLeft) out.append(left).append(bar);
		out.append(name);
		if (anyRight) out.append(bar).append(right);
		return out;
	}

	/** The best gamemode: lowest tier, HT before LT, current before retired. */
	private String best(Map<String, Rank> ranks) {
		String best = null;
		int bestScore = Integer.MAX_VALUE;
		for (Map.Entry<String, Rank> e : ranks.entrySet()) {
			Rank r = e.getValue();
			if (r.retired() && !retired.get()) continue;
			int score = r.score() + (r.retired() ? 100 : 0);
			if (score < bestScore) {
				bestScore = score;
				best = e.getKey();
			}
		}
		return best;
	}

	private Component part(String mode, Rank rank) {
		MutableComponent c = Component.empty();
		if (icons.get()) c.append(Component.literal(String.valueOf(icon(mode))).withStyle(s -> Fonts.withFont(s.withColor(0xFFFFFF), ICON_FONT))).append(" ");
		int color = color(rank);
		c.append(Component.literal(rank.label()).withStyle(s -> s.withColor(color)));
		return c;
	}

	/** Glyphs in assets/tuff/font/tiers.json (art: textures/font/tiers.png). */
	private static char icon(String mode) {
		return switch (mode) {
			case "nethop", "neth_pot" -> '';
			case "sword" -> '';
			case "axe" -> '';
			case "pot" -> '';
			case "vanilla", "crystal" -> '';
			case "smp" -> '';
			case "uhc" -> '';
			case "mace" -> '';
			default -> '';
		};
	}

	/** Gold, silver and bronze for tiers 1 to 3, then blue and grey; HT a little brighter. */
	private static int color(Rank r) {
		if (r.retired()) return 0xA9C7E8;
		return switch (r.tier()) {
			case 1 -> r.high() ? 0xFFCF4A : 0xE8B33E;
			case 2 -> r.high() ? 0xE4E9EF : 0xB9C0C8;
			case 3 -> r.high() ? 0xF0A061 : 0xC98552;
			case 4 -> r.high() ? 0x7FB6F0 : 0x6A98C8;
			default -> r.high() ? 0xA6ADB4 : 0x8A9097;
		};
	}
}
