package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerCurLevelRecordChangeNotifyOuterClass.TowerCurLevelRecordChangeNotify;
import emu.grasscutter.net.proto.TowerCurLevelRecordOuterClass.TowerCurLevelRecord;

public class PacketTowerCurLevelRecordChangeNotify extends BasePacket {

    private PacketTowerCurLevelRecordChangeNotify() {
        super(PacketOpcodes.TowerCurLevelRecordChangeNotify);
    }

    public static PacketTowerCurLevelRecordChangeNotify empty() {
        var record = TowerCurLevelRecord.newBuilder().setIsEmpty(true).build();
        var packet = new PacketTowerCurLevelRecordChangeNotify();
        packet.setData(
                TowerCurLevelRecordChangeNotify.newBuilder().setCurLevelRecord(record).build());
        return packet;
    }

    public PacketTowerCurLevelRecordChangeNotify(int curFloorId, int curLevelIndex) {
        this(curFloorId, curLevelIndex, true, 0);
    }

    public PacketTowerCurLevelRecordChangeNotify(
            int curFloorId, int curLevelIndex, boolean isUpperPart, int towerBuffId) {
        super(PacketOpcodes.TowerCurLevelRecordChangeNotify);

        var record =
                TowerCurLevelRecord.newBuilder()
                        .setCurFloorId(curFloorId)
                        .setCurLevelIndex(curLevelIndex)
                        .setIsUpperPart(isUpperPart);

        if (towerBuffId > 0) {
            record.addBuffIdList(towerBuffId);
        }

        var proto =
                TowerCurLevelRecordChangeNotify.newBuilder().setCurLevelRecord(record.build()).build();

        this.setData(proto);
    }
}
