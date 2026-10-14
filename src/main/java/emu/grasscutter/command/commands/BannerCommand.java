package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.gacha.GachaSystem;
import emu.grasscutter.game.player.Player;
import java.util.*;
import java.util.stream.Collectors;

@Command(
        label = "banner",
        usage = {"<version>", "list", "reset"},
        permission = "player.banner",
        permissionTargeted = "player.banner.others")
public final class BannerCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        GachaSystem gacha = targetPlayer.getServer().getGachaSystem();
        if (args.isEmpty()) {
            String focus = targetPlayer.getGachaInfo().getBannerFocus();
            CommandHandler.sendMessage(
                    sender,
                    "Showing banners from: "
                            + (focus == null ? gacha.getLatestVersion() + " (latest)" : focus));
            this.sendUsageMessage(sender);
            return;
        }

        String arg = args.get(0).toLowerCase();
        switch (arg) {
            case "list" -> {
                Map<String, List<String>> byVersion = new LinkedHashMap<>();
                for (String v : gacha.getVersions()) {
                    String major = v.substring(0, v.lastIndexOf('.'));
                    byVersion.computeIfAbsent(major, k -> new ArrayList<>()).add(v.substring(v.lastIndexOf('.') + 1));
                }
                String text =
                        byVersion.entrySet().stream()
                                .map(e -> e.getKey() + " (" + String.join(",", e.getValue()) + ")")
                                .collect(Collectors.joining(", "));
                CommandHandler.sendMessage(sender, "Versions (phases): " + text);
                CommandHandler.sendMessage(sender, "Use /banner 7.1 for a whole version or /banner 7.1.1 for one phase.");
            }
            case "reset", "clear" -> {
                targetPlayer.getGachaInfo().setBannerFocus(null);
                targetPlayer.save();
                CommandHandler.sendMessage(
                        sender,
                        "Showing the latest banners (" + gacha.getLatestVersion() + ") again. Reopen the wish screen.");
            }
            default -> {
                if (!arg.matches("\\d+(\\.\\d+){1,2}")) {
                    this.sendUsageMessage(sender);
                    return;
                }
                long count = gacha.countBannersFromVersion(arg);
                if (count == 0) {
                    CommandHandler.sendMessage(sender, "No banners from " + arg + ". Use /banner list to see the versions.");
                    return;
                }
                targetPlayer.getGachaInfo().setBannerFocus(arg);
                targetPlayer.save();
                CommandHandler.sendMessage(
                        sender, "Showing " + count + " banner(s) from " + arg + ". Reopen the wish screen.");
            }
        }
    }
}
