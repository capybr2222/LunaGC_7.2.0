package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.tower.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerAllDataRspOuterClass.TowerAllDataRsp;
import emu.grasscutter.net.proto.TowerCurLevelRecordOuterClass.TowerCurLevelRecord;
import emu.grasscutter.utils.helpers.DateHelper;
import java.util.*;
import java.util.stream.*;

public class PacketTowerAllDataRsp extends BasePacket {

    public PacketTowerAllDataRsp(TowerSystem towerScheduleManager, TowerManager towerManager) {
        super(PacketOpcodes.TowerAllDataRsp);

        var recordList =
                towerManager.getRecordMap().values().stream()
                        .filter(Objects::nonNull)
                        .map(PacketTowerFloorRecordChangeNotify::toProto)
                        .toList();

        var schedule = towerScheduleManager.getCurrentTowerScheduleData();
        var scheduleStart = DateHelper.getUnixTime(towerScheduleManager.getScheduleStartTime());

        var openTimeMap =
                towerScheduleManager.getScheduleFloors().stream()
                        .collect(Collectors.toMap(x -> x, y -> scheduleStart));

        int validRecordCount =
                (int)
                        recordList.stream()
                                .filter(
                                        record ->
                                                record.getPassedLevelMapMap().values().stream()
                                                        .anyMatch(stars -> stars > 0))
                                .count();

        var proto =
                TowerAllDataRsp.newBuilder()
                        .setTowerScheduleId(schedule != null ? schedule.getScheduleId() : 0)
                        .addAllTowerFloorRecordList(recordList)
                        .setCurLevelRecord(TowerCurLevelRecord.newBuilder().setIsEmpty(true))
                        .setScheduleStartTime(scheduleStart)
                        .setNextScheduleChangeTime(
                                DateHelper.getUnixTime(towerScheduleManager.getNextScheduleChangeTime()))
                        .putAllFloorOpenTimeMap(openTimeMap)
                        .setValidTowerRecordNum(validRecordCount)
                        .setIsFinishedEntranceFloor(towerManager.canEnterScheduleFloor())
                        .build();

        this.setData(proto);
    }
}
