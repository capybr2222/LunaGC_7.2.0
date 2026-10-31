package emu.grasscutter.game.dungeons;

import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult;
import emu.grasscutter.game.dungeons.dungeon_results.BaseDungeonResult.DungeonEndReason;
import emu.grasscutter.game.dungeons.dungeon_results.TowerResult;
import emu.grasscutter.game.tower.TowerLevelRecord;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.server.packet.send.*;

public class TowerDungeonSettleListener implements DungeonSettleListener {

    @Override
    public void onDungeonSettle(DungeonManager dungeonManager, DungeonEndReason endReason) {
        var scene = dungeonManager.getScene();

        var dungeonData = dungeonManager.getDungeonData();

        if (endReason == DungeonEndReason.COMPLETED && isBetweenHalves(scene)) {
            return;
        }

        var players = scene.getPlayers();
        if (players.isEmpty()) {
            return;
        }
        var towerManager = players.get(0).getTowerManager();
        var stars = towerManager.getCurLevelStars();

        if (endReason == DungeonEndReason.COMPLETED) {
            towerManager.notifyCurLevelRecordChangeWhenDone(stars);
            var floorRecord =
                    towerManager
                            .getRecordMap()
                            .computeIfAbsent(
                                    towerManager.getCurrentFloorId(), TowerLevelRecord::new);
            scene.broadcastPacket(
                    new PacketTowerFloorRecordChangeNotify(
                            floorRecord, towerManager.canEnterScheduleFloor()));
        }

        var challenge = scene.getChallenge();
        var finishedTime = challenge != null ? challenge.getFinishedTime() : 0;
        var dungeonStats =
                new DungeonEndStats(scene.getKilledMonsterCount(), finishedTime, 0, endReason);

        var result =
                endReason == DungeonEndReason.QUIT
                        ? new BaseDungeonResult(dungeonData, dungeonStats)
                        : new TowerResult(dungeonData, dungeonStats, towerManager, challenge, stars);

        scene.broadcastPacket(new PacketDungeonSettleNotify(result));
    }

    private static boolean isBetweenHalves(Scene scene) {
        return scene.getLoadedGroups().stream()
                .anyMatch(
                        group -> {
                            var variables = scene.getScriptManager().getVariables(group.id);
                            return variables != null
                                    && Integer.valueOf(1).equals(variables.get("stage"));
                        });
    }
}
