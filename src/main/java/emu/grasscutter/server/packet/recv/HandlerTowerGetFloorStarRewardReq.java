package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.TowerGetFloorStarRewardReqOuterClass.TowerGetFloorStarRewardReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTowerAllDataRsp;
import emu.grasscutter.server.packet.send.PacketTowerCurLevelRecordChangeNotify;
import emu.grasscutter.server.packet.send.PacketTowerGetFloorStarRewardRsp;

@Opcodes(PacketOpcodes.TowerGetFloorStarRewardReq)
public class HandlerTowerGetFloorStarRewardReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = TowerGetFloorStarRewardReq.parseFrom(payload == null ? new byte[0] : payload);
        var player = session.getPlayer();
        var tower = player.getTowerManager();

        session.send(PacketTowerCurLevelRecordChangeNotify.empty());

        boolean claimed = tower.claimFloorStarReward(req.getFloorId());
        session.send(
                new PacketTowerGetFloorStarRewardRsp(
                        req.getFloorId(), claimed ? Retcode.RET_SUCC : Retcode.RET_FAIL));

        tower.sendFloorRecordUpdate(req.getFloorId());
        session.send(new PacketTowerAllDataRsp(player.getServer().getTowerSystem(), tower));
    }
}
