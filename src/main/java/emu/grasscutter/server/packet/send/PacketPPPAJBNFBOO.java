package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.NCDHECPCPPOOuterClass.NCDHECPCPPO;
import emu.grasscutter.net.proto.PPPAJBNFBOOOuterClass.PPPAJBNFBOO;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.ShopGoodsOuterClass.ShopGoods;

public class PacketPPPAJBNFBOO extends BasePacket {

    public PacketPPPAJBNFBOO(
            NCDHECPCPPO req, Retcode retcode, int boughtNum) {
        super(PacketOpcodes.PPPAJBNFBOO);

        var rsp =
                PPPAJBNFBOO.newBuilder()
                        .setRetcode(retcode.getNumber())
                        .setShopType(req.getShopType())
                        .setMcoinCost(req.getMcoinCost())
                        .setHcoin(req.getHcoin());

        if (retcode == Retcode.RET_SUCC) {
            rsp.setBuyCount(req.getBuyCount())
                    .addGoodsList(ShopGoods.newBuilder().mergeFrom(req.getGoods()).setBoughtNum(boughtNum));
        }

        this.setData(rsp.build());
    }
}
