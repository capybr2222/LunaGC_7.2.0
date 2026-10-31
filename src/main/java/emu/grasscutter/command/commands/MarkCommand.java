package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import java.util.List;

@Command(label = "mark", usage = {"<label>"}, permission = "server.mark")
public final class MarkCommand implements CommandHandler {

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        var label = args.isEmpty() ? "(no label)" : String.join(" ", args);
        Grasscutter.getLogger().info("========== MARK: {} ==========", label);
        CommandHandler.sendMessage(sender, "Marked: " + label);
    }
}
