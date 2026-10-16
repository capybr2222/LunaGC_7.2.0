package emu.grasscutter.server.packet.recv;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.shop.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.BuyGoodsReqOuterClass;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketBuyGoodsRsp;
import emu.grasscutter.utils.Utils;
import java.util.*;

@Opcodes(PacketOpcodes.BuyGoodsReq)
public class HandlerBuyGoodsReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        BuyGoodsReqOuterClass.BuyGoodsReq buyGoodsReq =
                BuyGoodsReqOuterClass.BuyGoodsReq.parseFrom(payload);
        var player = session.getPlayer();
        int goodsId = buyGoodsReq.getGoods().getGoodsId();

        Retcode result =
                buy(session, buyGoodsReq.getShopType(), goodsId, buyGoodsReq.getBuyCount());
        if (result != Retcode.RET_SUCC) {
            session.send(new PacketBuyGoodsRsp(result));
            return;
        }

        session.send(
                new PacketBuyGoodsRsp(
                        buyGoodsReq.getShopType(),
                        player.getGoodsLimit(goodsId).getHasBoughtInPeriod(),
                        buyGoodsReq.getGoods()));
        player.save();
    }

    public static Retcode buy(GameSession session, int shopType, int goodsId, int buyCount) {
        List<ShopInfo> configShop = session.getServer().getShopSystem().getShopData().get(shopType);
        if (configShop == null || buyCount <= 0) return Retcode.RET_SVR_ERROR;

        Player player = session.getPlayer();
        Optional<ShopInfo> sg2 = configShop.stream().filter(x -> x.getGoodsId() == goodsId).findFirst();
        if (sg2.isEmpty()) return Retcode.RET_SVR_ERROR;
        ShopInfo sg = sg2.get();

        int currentTs = Utils.getCurrentSeconds();
        ShopLimit shopLimit = player.getGoodsLimit(sg.getGoodsId());
        int bought = 0;
        if (shopLimit != null) {
            if (currentTs > shopLimit.getNextRefreshTime()) {
                shopLimit.setNextRefreshTime(ShopSystem.getShopNextRefreshTime(sg));
            } else {
                bought = shopLimit.getHasBoughtInPeriod();
            }
            player.save();
        }

        if ((bought + buyCount > sg.getBuyLimit()) && sg.getBuyLimit() != 0) {
            return Retcode.RET_SHOP_BATCH_BUY_COUNT_LIMIT;
        }

        var artifactShop = session.getServer().getShopSystem().getArtifactShop();
        var piece = artifactShop.getPiece(sg.getGoodsId());
        if (piece != null) {
            var relics = player.getInventory().getInventoryTab(ItemType.ITEM_RELIQUARY);
            if (buyCount > relics.getMaxCapacity() - relics.getSize()) {
                return Retcode.RET_PACK_EXCEED_MAX_WEIGHT;
            }
        }

        int itemCount;
        try {
            itemCount = Math.multiplyExact(buyCount, sg.getGoodsItem().getCount());
        } catch (ArithmeticException overflow) {
            return Retcode.RET_SVR_ERROR;
        }

        List<ItemParamData> costs = new ArrayList<ItemParamData>(sg.getCostItemList());
        costs.add(new ItemParamData(202, sg.getScoin()));
        costs.add(new ItemParamData(201, sg.getHcoin()));
        costs.add(new ItemParamData(203, sg.getMcoin()));
        if (!player.getInventory().payItems(costs, buyCount)) {
            return Retcode.RET_SHOP_CONTENT_NOT_MATCH;
        }

        player.addShopLimit(sg.getGoodsId(), buyCount, ShopSystem.getShopNextRefreshTime(sg));
        if (piece != null) {
            var rolled = new ArrayList<GameItem>(buyCount);
            for (int i = 0; i < buyCount; i++) {
                rolled.add(artifactShop.roll(piece));
            }
            player.getInventory().addItems(rolled, ActionReason.Shop);
        } else {
            GameItem item = new GameItem(sg.getGoodsItem().getId(), itemCount);
            player.getInventory().addItem(item, ActionReason.Shop, true);
        }
        return Retcode.RET_SUCC;
    }
}
