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
 * /bot start, /bot stop, /bot tier lt3|mt3|ht3, /bot mend on|off,
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
		ServerTickEvents.END_SERVER_TICK.register(AimTrainer::tick);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> Match.allowDeath(entity, source));
		ServerLifecycleEvents.SERVER_STOPPING.register(Match::stop);
		ServerLifecycleEvents.SERVER_STOPPING.register(AimTrainer::stop);
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> tier = Commands.literal("tier");
		for (Tier t : Tier.values()) {
			tier.then(Commands.literal(t.name().toLowerCase()).executes(ctx -> {
				Settings.tier = t;
				// every tier starts with jump reset on
				Settings.jumpReset = true;
				Settings.save();
				return say(ctx, "Bot tier: " + t.label);
			}));
		}
		LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
		set.then(toggle("jumpreset", v -> Settings.jumpReset = v, "Jump reset"));
		set.then(toggle("wtap", v -> Settings.wtap = v, "W-tap"));
		set.then(toggle("clumps", v -> Settings.clumps = v, "Clumps"));
		set.then(Commands.literal("fight")
				.then(Commands.literal("agro").executes(ctx -> mend(ctx, false)))
				.then(Commands.literal("mend").executes(ctx -> mend(ctx, true))));
		set.then(percent("crit", v -> Settings.critPct = v, "Crit chance", 100));
		set.then(percent("jumpresetchance", v -> Settings.jumpResetPct = v, "Jump reset chance", 100));
		set.then(percent("strafe", v -> Settings.strafePct = v, "Strafe", 100));
		set.then(percent("potat", v -> Settings.potAtHearts = v, "Pot at (hearts)", 10));
		set.then(Commands.literal("mendrate").then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 10)).executes(ctx -> {
			Settings.mendRate = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
			Settings.save();
			return say(ctx, "Mend rate: " + Settings.mendRate + " bottles a second");
		})));
		set.then(Commands.literal("miss").then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100)).executes(ctx -> {
			Settings.missPct = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
			Settings.save();
			return say(ctx, "Miss rate: " + Settings.missPct + "%");
		})));
		set.then(Commands.literal("reset").executes(ctx -> {
			Settings.resetTuning();
			Settings.save();
			return say(ctx, "Tuning back to the " + Settings.tier.label + " defaults.");
		}));
		LiteralArgumentBuilder<CommandSourceStack> deivi = Commands.literal("deivi");
		for (int i = 0; i < Settings.DEIVI.length; i++) {
			int level = i;
			deivi.then(Commands.literal(Settings.DEIVI[i].toLowerCase()).executes(ctx -> {
				Settings.deivi = level;
				Settings.save();
				return say(ctx, "Deivi pop: " + Settings.DEIVI[level]);
			}));
		}
		set.then(deivi);
		LiteralArgumentBuilder<CommandSourceStack> style = Commands.literal("style");
		for (Settings.Style st : Settings.Style.values()) {
			style.then(Commands.literal(st.name().toLowerCase()).executes(ctx -> {
				Settings.style = st;
				Settings.save();
				return say(ctx, "Playstyle: " + st.label);
			}));
		}
		set.then(style);
		dispatcher.register(Commands.literal("bot")
				.executes(ctx -> say(ctx, "Glade Bot (" + Settings.tier.label + ", " + (Settings.mend ? "mend" : "no mend")
						+ "): /bot start, /bot stop, /bot tier lt3|mt3|ht3, /bot mend on|off, /bot kit save me|bot, /bot kit reset. Press B for settings."))
				.then(Commands.literal("start").executes(ctx -> say(ctx, Match.start(player(ctx)))))
				.then(Commands.literal("selftest").executes(ctx -> say(ctx, Match.selfTest(ctx.getSource().getServer()))))
				.then(Commands.literal("aimselftest").executes(ctx -> say(ctx, AimTrainer.selfTest(ctx.getSource().getServer()))))
				.then(aim())
				.then(Commands.literal("stop").executes(ctx -> {
					Match.stop(ctx.getSource().getServer());
					return say(ctx, "Match stopped.");
				}))
				.then(tier)
				.then(set)
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

	private static LiteralArgumentBuilder<CommandSourceStack> toggle(String name, java.util.function.Consumer<Boolean> set, String label) {
		return Commands.literal(name)
				.then(Commands.literal("on").executes(ctx -> {
					set.accept(true);
					Settings.save();
					return say(ctx, label + ": on");
				}))
				.then(Commands.literal("off").executes(ctx -> {
					set.accept(false);
					Settings.save();
					return say(ctx, label + ": off");
				}));
	}

	/** /bot aim tracking [easy|medium|hard] [seconds], /bot aim stop */
	private static LiteralArgumentBuilder<CommandSourceStack> aim() {
		var tracking = Commands.literal("tracking").executes(ctx -> say(ctx, AimTrainer.start(player(ctx), AimTrainer.Level.values()[Settings.aimLevel], 30)));
		for (AimTrainer.Level l : AimTrainer.Level.values()) {
			tracking.then(Commands.literal(l.name().toLowerCase())
					.executes(ctx -> {
						Settings.aimLevel = l.ordinal();
						Settings.save();
						return say(ctx, AimTrainer.start(player(ctx), l, 30));
					})
					.then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(10, 300)).executes(ctx -> {
						Settings.aimLevel = l.ordinal();
						Settings.save();
						return say(ctx, AimTrainer.start(player(ctx), l, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds")));
					})));
		}
		return Commands.literal("aim").then(tracking).then(Commands.literal("stop").executes(ctx -> {
			AimTrainer.stop(ctx.getSource().getServer());
			return say(ctx, "Aim training stopped.");
		}));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> percent(String name, java.util.function.IntConsumer set, String label, int max) {
		return Commands.literal(name)
				.then(Commands.literal("default").executes(ctx -> {
					set.accept(-1);
					Settings.save();
					return say(ctx, label + ": tier default");
				}))
				.then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, max)).executes(ctx -> {
					int v = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value");
					set.accept(v);
					Settings.save();
					return say(ctx, label + ": " + v);
				}));
	}

	private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int mend(CommandContext<CommandSourceStack> ctx, boolean on) {
		Settings.mend = on;
		Settings.save();
		return say(ctx, on ? "Fight: Mend (XP in the kits, defensive opening, the bot mends its armour)" : "Fight: Agro (no XP: a durability war, few gaps, strafe and crit)");
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
