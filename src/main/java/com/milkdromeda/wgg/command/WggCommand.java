package com.milkdromeda.wgg.command;

import com.milkdromeda.wgg.WeirdGunGamePlugin;
import com.milkdromeda.wgg.combat.Knife;
import com.milkdromeda.wgg.combat.KnifeItem;
import com.milkdromeda.wgg.gun.GunBlueprint;
import com.milkdromeda.wgg.gun.GunItem;
import com.milkdromeda.wgg.gun.GunPreset;
import com.milkdromeda.wgg.gun.PartRegistry;
import com.milkdromeda.wgg.menu.WorkbenchMenu;
import com.milkdromeda.wgg.tournament.Tournament;
import com.milkdromeda.wgg.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WggCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT = List.of(
            "bench", "gun", "give", "knife", "random", "tournament", "parts", "reload", "help");
    private static final List<String> TOURNAMENT = List.of("start", "join", "leave", "stop", "status");

    private final WeirdGunGamePlugin plugin;

    public WggCommand(WeirdGunGamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "bench", "menu", "build" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    new WorkbenchMenu(plugin, player).open();
                }
            }
            case "gun", "give" -> giveGun(sender, args);
            case "knife" -> giveKnife(sender, args);
            case "random" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    GunBlueprint blueprint = GunBlueprint.random();
                    plugin.benches().set(player, blueprint);
                    handItem(player, GunItem.build(blueprint));
                    player.sendMessage(Text.msg("<gray>Rolled <white>" + blueprint.displayName() + "</white>.</gray>"));
                }
            }
            case "tournament", "t" -> tournament(sender, args);
            case "parts" -> listParts(sender);
            case "reload" -> {
                if (!sender.hasPermission("wgg.admin")) {
                    sender.sendMessage(Text.msg("<#f43f5e>You do not have permission for that."));
                    return true;
                }
                plugin.reloadWggConfig();
                sender.sendMessage(Text.msg("<#4ade80>Config reloaded."));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    // ------------------------------------------------------------- subcommands

    private void giveGun(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Text.msg("<gray>Usage: <white>/wgg gun <preset|part ids></white>"));
            player.sendMessage(Text.msg("<gray>Presets: <white>"
                    + String.join(", ", GunPreset.all().stream().map(GunPreset::id).toList()) + "</white>"));
            return;
        }

        GunPreset preset = GunPreset.byId(args[1]);
        GunBlueprint blueprint;
        if (preset != null) {
            blueprint = preset.toBlueprint();
        } else {
            // Treat the remaining arguments as raw part ids, in any order.
            blueprint = new GunBlueprint();
            List<String> unknown = new ArrayList<>();
            for (int i = 1; i < args.length; i++) {
                var part = PartRegistry.byId(args[i]);
                if (part == null) {
                    unknown.add(args[i]);
                } else {
                    blueprint.set(part);
                }
            }
            if (!unknown.isEmpty()) {
                player.sendMessage(Text.msg("<#f43f5e>Unknown part ids: <white>"
                        + String.join(", ", unknown) + "</white>"));
                return;
            }
        }

        handItem(player, GunItem.build(blueprint));
        player.sendMessage(Text.msg("<gray>Built <white>" + blueprint.displayName() + "</white>.</gray>"));
    }

    private void giveKnife(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Text.msg("<gray>Knives: <white>"
                    + String.join(", ", Knife.all().stream().map(Knife::id).toList()) + "</white>"));
            return;
        }
        Knife knife = Knife.byId(args[1]);
        if (knife == null) {
            player.sendMessage(Text.msg("<#f43f5e>No knife called <white>" + args[1] + "</white>."));
            return;
        }
        handItem(player, KnifeItem.build(knife));
        player.sendMessage(Text.msg("<gray>Took the <white>" + knife.name() + "</white>.</gray>"));
    }

    private void tournament(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Text.msg("<gray>Usage: <white>/wgg tournament <start|join|leave|stop|status></white>"));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return;
                }
                if (!player.hasPermission("wgg.tournament.start")) {
                    player.sendMessage(Text.msg("<#f43f5e>You do not have permission to start a tournament."));
                    return;
                }
                int rounds = plugin.wggConfig().tournamentRounds();
                if (args.length >= 3) {
                    try {
                        rounds = Math.max(1, Math.min(20, Integer.parseInt(args[2])));
                    } catch (NumberFormatException ignored) {
                        player.sendMessage(Text.msg("<#f43f5e>Round count must be a number."));
                        return;
                    }
                }
                report(sender, plugin.tournaments().start(player, rounds));
            }
            case "join" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    report(sender, plugin.tournaments().join(player));
                }
            }
            case "leave" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    report(sender, plugin.tournaments().leave(player));
                }
            }
            case "stop" -> {
                if (!sender.hasPermission("wgg.tournament.start")) {
                    sender.sendMessage(Text.msg("<#f43f5e>You do not have permission for that."));
                    return;
                }
                report(sender, plugin.tournaments().stop());
            }
            case "status" -> {
                if (!plugin.tournaments().isRunning()) {
                    sender.sendMessage(Text.msg("<gray>No tournament is running.</gray>"));
                    return;
                }
                Tournament active = plugin.tournaments().active();
                sender.sendMessage(Text.msg("<gray>State <white>" + active.state()
                        + "</white> • Round <white>" + active.round() + "/" + active.totalRounds()
                        + "</white> • Fighters <white>" + active.participants().size()
                        + "</white> • Standing <white>" + active.livingParticipants().size() + "</white></gray>"));
            }
            default -> sender.sendMessage(Text.msg("<gray>Usage: <white>/wgg tournament "
                    + "<start|join|leave|stop|status></white>"));
        }
    }

    private void listParts(CommandSender sender) {
        sender.sendMessage(Text.msg("<gray>" + PartRegistry.total() + " parts across 6 sections:</gray>"));
        for (var section : com.milkdromeda.wgg.gun.PartSection.values()) {
            String ids = String.join(", ", PartRegistry.of(section).stream()
                    .map(com.milkdromeda.wgg.gun.GunPart::id).toList());
            sender.sendMessage(Text.mm(section.color() + section.displayName() + " <dark_gray>» </dark_gray><gray>"
                    + ids + "</gray>"));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Text.mm("<gradient:#ff8a3d:#ff4fd8><bold>Weird Gun Game</bold></gradient>"));
        sender.sendMessage(Text.mm("<dark_gray> ▸ </dark_gray><white>/wgg bench</white> <gray>— open the gun bench"));
        sender.sendMessage(Text.mm("<dark_gray> ▸ </dark_gray><white>/wgg gun <preset></white> <gray>— grab a ready-made gun"));
        sender.sendMessage(Text.mm("<dark_gray> ▸ </dark_gray><white>/wgg gun <part ids...></white> <gray>— build from part ids"));
        sender.sendMessage(Text.mm("<dark_gray> ▸ </dark_gray><white>/wgg knife <id></white> <gray>— grab a knife"));
        sender.sendMessage(Text.mm("<dark_gray> ▸ </dark_gray><white>/wgg random</white> <gray>— roll a completely random gun"));
        sender.sendMessage(Text.mm("<dark_gray> ▸ </dark_gray><white>/wgg parts</white> <gray>— list every part id"));
        sender.sendMessage(Text.mm("<dark_gray> ▸ </dark_gray><white>/wgg tournament start [rounds]</white> <gray>— fight the Superbox"));
    }

    // ---------------------------------------------------------------- helpers

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Text.msg("<#f43f5e>That one is for players only."));
        return null;
    }

    private void report(CommandSender sender, String error) {
        if (error != null) {
            sender.sendMessage(Text.msg("<#f43f5e>" + error));
        }
    }

    private void handItem(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    // ------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(ROOT, args[0]);
        }
        if (args.length >= 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "gun", "give" -> {
                    List<String> options = new ArrayList<>(GunPreset.all().stream().map(GunPreset::id).toList());
                    options.addAll(PartRegistry.allIds());
                    yield filter(options, args[args.length - 1]);
                }
                case "knife" -> filter(Knife.all().stream().map(Knife::id).toList(), args[1]);
                case "tournament", "t" -> args.length == 2 ? filter(TOURNAMENT, args[1]) : List.of();
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
