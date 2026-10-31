package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BeyondBattlePassScheduleData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto._BeyondBattlePassAllDataNotifyOuterClass._BeyondBattlePassAllDataNotify;
import emu.grasscutter.net.proto._BeyondBattlePassMissionOuterClass._BeyondBattlePassMission;
import emu.grasscutter.net.proto._BeyondBattlePassScheduleOuterClass._BeyondBattlePassSchedule;
import emu.grasscutter.net.proto._BeyondBattlePassUnlockStatusOuterClass._BeyondBattlePassUnlockStatus;

public class PacketBeyondBattlePassAllDataNotify extends BasePacket {

    public PacketBeyondBattlePassAllDataNotify(Player player) {
        super(PacketOpcodes.BeyondBattlePassAllDataNotify);

        var battlePass = player.getBattlePassManager();

        var schedule =
                _BeyondBattlePassSchedule.newBuilder()
                        .setScheduleId(BeyondBattlePassScheduleData.currentId())
                        .setLevel(battlePass.getLevel())
                        .setPoint(battlePass.getPoint())
                        .setBeginTime(0)
                        .setEndTime(Integer.MAX_VALUE)
                        .setUnlockStatus(_BeyondBattlePassUnlockStatus.BEYOND_BATTLE_PASS_FREE);

        var proto =
                _BeyondBattlePassAllDataNotify.newBuilder()
                        .setHaveCurBeyondSchedule(true)
                        .setCurSchedule(schedule);

        GameData.getBeyondBattlePassMissionDataMap().values().stream()
                .filter(mission -> !mission.isDisuse())
                .forEach(
                        mission ->
                                proto.addMissionList(
                                        _BeyondBattlePassMission.newBuilder()
                                                .setMissionId(mission.getId())
                                                .setTotalProgress(mission.getProgress())
                                                .setMissionStatus(
                                                        _BeyondBattlePassMission._BeyondMissionStatus.MISSION_UNFINISHED)));

        this.setData(proto);
    }
}
