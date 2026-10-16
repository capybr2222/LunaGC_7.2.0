package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.NCDHECPCPPOOuterClass.NCDHECPCPPO;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPPPAJBNFBOO;

@Opcodes(PacketOpcodes.NCDHECPCPPO)
public class HandlerNCDHECPCPPO extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = NCDHECPCPPO.parseFrom(payload);
        var player = session.getPlayer();
        int mcoin = req.getMcoinCost();
        int hcoin = req.getHcoin();

        if (mcoin < 0 || hcoin != mcoin) {
            session.send(new PacketPPPAJBNFBOO(req, Retcode.RET_SVR_ERROR, 0));
            return;
        }
        if (player.getCrystals() < mcoin) {
            session.send(new PacketPPPAJBNFBOO(req, Retcode.RET_MCOIN_NOT_ENOUGH, 0));
            return;
        }

        player.setCrystals(player.getCrystals() - mcoin);
        player.setPrimogems(player.getPrimogems() + hcoin);

        int goodsId = req.getGoods().getGoodsId();
        Retcode result = HandlerBuyGoodsReq.buy(session, req.getShopType(), goodsId, req.getBuyCount());
        if (result != Retcode.RET_SUCC) {
            player.setPrimogems(player.getPrimogems() - hcoin);
            player.setCrystals(player.getCrystals() + mcoin);
            session.send(new PacketPPPAJBNFBOO(req, result, 0));
            player.save();
            return;
        }

        session.send(
                new PacketPPPAJBNFBOO(
                        req, Retcode.RET_SUCC, player.getGoodsLimit(goodsId).getHasBoughtInPeriod()));
        player.save();
    }
}
