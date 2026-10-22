package net.gladebot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Glade Bot: a Neth Pot practice bot for singleplayer.
 *
 * /bot start, /bot stop, /bot tier lt3|ht3|lt2, /bot mend on|off,
 * /bot kit save me|bot (your current inventory becomes that kit), /bot kit reset.
 * Press B for the settings screen.
 */
public final class GladeBot implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("gladebot");

	@Override
	public void onInitialize() {
		Settings.load();
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(Match::tick);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> Match.allowDeath(entity));
		ServerLifecycleEvents.SERVER_STOPPING.register(Match::stop);
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> tier = Commands.literal("tier");
		for (Tier t : Tier.values()) {
			tier.then(Commands.literal(t.name().toLowerCase()).executes(ctx -> {
				Settings.tier = t;
				Settings.save();
				return say(ctx, "Bot tier: " + t.label);
			}));
		}
		dispatcher.register(Commands.literal("bot")
				.executes(ctx -> say(ctx, "Glade Bot (" + Settings.tier.label + ", " + (Settings.mend ? "mend" : "no mend")
						+ "): /bot start, /bot stop, /bot tier lt3|ht3|lt2, /bot mend on|off, /bot kit save me|bot, /bot kit reset. Press B for settings."))
				.then(Commands.literal("start").executes(ctx -> say(ctx, Match.start(player(ctx)))))
				.then(Commands.literal("stop").executes(ctx -> {
					Match.stop(ctx.getSource().getServer());
					return say(ctx, "Match stopped.");
				}))
				.then(tier)
				.then(Commands.literal("mend")
						.then(Commands.literal("on").executes(ctx -> mend(ctx, true)))
						.then(Commands.literal("off").executes(ctx -> mend(ctx, false))))
				.then(Commands.literal("kit")
						.then(Commands.literal("save")
								.then(Commands.literal("me").executes(ctx -> saveKit(ctx, false)))
								.then(Commands.literal("bot").executes(ctx -> saveKit(ctx, true))))
						.then(Commands.literal("reset").executes(ctx -> {
							Settings.playerKit = null;
							Settings.botKit = null;
							Settings.save();
							return say(ctx, "Both kits are back to the default Neth Pot kit.");
						}))));
	}

	private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int mend(CommandContext<CommandSourceStack> ctx, boolean on) {
		Settings.mend = on;
		Settings.save();
		return say(ctx, on ? "Mend on: XP bottles in the kits, and the bot repairs its armour." : "Mend off: no XP bottles.");
	}

	private static int saveKit(CommandContext<CommandSourceStack> ctx, boolean bot) throws CommandSyntaxException {
		ServerPlayer p = player(ctx);
		var json = Kits.encode(ctx.getSource().getServer().registryAccess(), Kits.capture(p));
		if (bot) Settings.botKit = json;
		else Settings.playerKit = json;
		Settings.save();
		return say(ctx, "Saved your inventory as the " + (bot ? "bot's" : "your") + " kit.");
	}

	private static int say(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}
}
