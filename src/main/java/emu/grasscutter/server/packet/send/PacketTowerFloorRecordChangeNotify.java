package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.tower.TowerLevelRecord;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerFloorRecordChangeNotifyOuterClass.TowerFloorRecordChangeNotify;
import emu.grasscutter.net.proto.TowerFloorRecordOuterClass.TowerFloorRecord;
import emu.grasscutter.net.proto.TowerLevelRecordOuterClass;
import java.util.Map;
import java.util.stream.IntStream;

public class PacketTowerFloorRecordChangeNotify extends BasePacket {

    public PacketTowerFloorRecordChangeNotify(
            TowerLevelRecord record, boolean canEnterScheduleFloor) {
        super(PacketOpcodes.TowerFloorRecordChangeNotify);

        var proto =
                TowerFloorRecordChangeNotify.newBuilder()
                        .addTowerFloorRecordList(toProto(record))
                        .setIsFinishedEntranceFloor(canEnterScheduleFloor)
                        .build();

        this.setData(proto);
    }

    public static TowerFloorRecord toProto(TowerLevelRecord record) {
        int floorId = record != null ? record.getFloorId() : 0;
        int progress = record != null ? record.getFloorStarRewardProgress() : 0;
        progress = Math.max(0, Math.min(9, progress));
        progress = (progress / 3) * 3;

        var floor = TowerFloorRecord.newBuilder().setFloorId(floorId).setFloorStarRewardProgress(progress);

        Map<Integer, Integer> passedLevels = record != null ? record.getPassedLevelMap() : null;
        if (passedLevels == null || passedLevels.isEmpty()) {
            return floor.build();
        }

        for (var entry : passedLevels.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            int stars = Math.min(3, entry.getValue());
            floor.putPassedLevelMap(entry.getKey(), stars);
            floor.addPassedLevelRecordList(
                    TowerLevelRecordOuterClass.TowerLevelRecord.newBuilder()
                            .setLevelId(entry.getKey())
                            .addAllSatisfiedCondList(IntStream.rangeClosed(1, stars).boxed().toList())
                            .build());
        }

        return floor.build();
    }
}
