package com.milkdromeda.ledger.command;

import com.milkdromeda.ledger.Ledger;
import com.milkdromeda.ledger.gun.GunBlueprint;
import com.milkdromeda.ledger.gun.GunItem;
import com.milkdromeda.ledger.gun.GunPart;
import com.milkdromeda.ledger.gun.GunPreset;
import com.milkdromeda.ledger.gun.PartRegistry;
import com.milkdromeda.ledger.gun.PartSection;
import com.milkdromeda.ledger.menu.GunBenchMenu;
import com.milkdromeda.ledger.watch.WatchRecord;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** Everything you can type. The important one is {@code /ledger bench}. */
public final class LedgerCommands {

    private static final SuggestionProvider<CommandSourceStack> PRESETS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    GunPreset.all().stream().map(GunPreset::id).toList(), builder);

    private static final SuggestionProvider<CommandSourceStack> PARTS =
            (context, builder) -> SharedSuggestionProvider.suggest(PartRegistry.allIds(), builder);

    private LedgerCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ledger")
                    .then(Commands.literal("bench").executes(context -> openBench(context.getSource())))
                    .then(Commands.literal("menu").executes(context -> openBench(context.getSource())))
                    .then(Commands.literal("record").executes(context -> showRecord(context.getSource())))
                    .then(Commands.literal("parts").executes(context -> listParts(context.getSource())))
                    .then(Commands.literal("random").executes(context -> giveRandom(context.getSource())))
                    .then(Commands.literal("gun")
                            .then(Commands.argument("preset", StringArgumentType.word())
                                    .suggests(PRESETS)
                                    .executes(context -> givePreset(context.getSource(),
                                            StringArgumentType.getString(context, "preset")))))
                    .then(Commands.literal("part")
                            .then(Commands.argument("id", StringArgumentType.word())
                                    .suggests(PARTS)
                                    .executes(context -> fitPart(context.getSource(),
                                            StringArgumentType.getString(context, "id")))))
                    .executes(context -> openBench(context.getSource()));

            dispatcher.register(root);

            // Short aliases, because the bench is the thing people actually want.
            for (String alias : new String[] {"gunbench", "guns", "bench"}) {
                dispatcher.register(Commands.literal(alias)
                        .executes(context -> openBench(context.getSource())));
            }
        });
    }

    // ------------------------------------------------------------ subcommands

    private static int openBench(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GunBlueprint bench = Ledger.benches().of(player);
        player.openMenu(new SimpleMenuProvider(
                (syncId, inventory, who) -> new GunBenchMenu(syncId, inventory, bench),
                Component.literal("Gun Bench").withStyle(ChatFormatting.GOLD)));
        return 1;
    }

    private static int givePreset(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GunPreset preset = GunPreset.byId(id);
        if (preset == null) {
            source.sendFailure(Component.literal("No preset called " + id + "."));
            return 0;
        }
        GunBlueprint blueprint = preset.toBlueprint();
        Ledger.benches().set(player, blueprint);
        give(player, GunItem.merge(blueprint));
        source.sendSuccess(() -> Component.literal("Merged " + blueprint.displayName() + ".")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int fitPart(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GunPart part = PartRegistry.byId(id);
        if (part == null) {
            source.sendFailure(Component.literal("No part called " + id + "."));
            return 0;
        }
        GunBlueprint bench = Ledger.benches().of(player);
        bench.set(part);
        bench.setCustomName(null);
        source.sendSuccess(() -> Component.literal(
                part.section().displayName() + " → " + part.name()).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int giveRandom(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GunBlueprint blueprint = GunBlueprint.random();
        Ledger.benches().set(player, blueprint);
        give(player, GunItem.merge(blueprint));
        source.sendSuccess(() -> Component.literal("Rolled " + blueprint.displayName() + ".")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int listParts(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(PartRegistry.total() + " parts:")
                .withStyle(ChatFormatting.GOLD), false);
        for (PartSection section : PartSection.values()) {
            String ids = String.join(", ", PartRegistry.of(section).stream().map(GunPart::id).toList());
            source.sendSuccess(() -> Component.literal(section.displayName() + " » " + ids)
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int showRecord(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        WatchRecord record = Ledger.watch().of(player);

        source.sendSuccess(() -> Component.literal("Earth has been keeping notes.")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        for (Map.Entry<String, Long> entry : record.summary().entrySet()) {
            source.sendSuccess(() -> Component.literal("  " + entry.getKey() + ": " + entry.getValue())
                    .withStyle(ChatFormatting.GRAY), false);
        }
        source.sendSuccess(() -> Component.literal("  total interactions: " + record.totalInteractions())
                .withStyle(ChatFormatting.WHITE), false);
        return 1;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
