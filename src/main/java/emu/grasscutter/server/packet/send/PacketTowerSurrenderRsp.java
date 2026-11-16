package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TowerSurrenderRspOuterClass.TowerSurrenderRsp;

public class PacketTowerSurrenderRsp extends BasePacket {

    public PacketTowerSurrenderRsp(Retcode retcode) {
        super(PacketOpcodes.TowerSurrenderRsp);

        this.setData(TowerSurrenderRsp.newBuilder().setRetcode(retcode.getNumber()).build());
    }
}
