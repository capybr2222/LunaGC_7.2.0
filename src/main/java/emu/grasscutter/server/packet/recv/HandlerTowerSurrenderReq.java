package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTowerSurrenderRsp;

@Opcodes(PacketOpcodes.TowerSurrenderReq)
public class HandlerTowerSurrenderReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        boolean surrendered = session.getPlayer().getTowerManager().surrender();
        session.send(new PacketTowerSurrenderRsp(surrendered ? Retcode.RET_SUCC : Retcode.RET_FAIL));
    }
}
