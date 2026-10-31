package emu.grasscutter.data.excels;

import emu.grasscutter.GameConstants;
import emu.grasscutter.data.*;
import lombok.Getter;

@ResourceType(name = "BeyondBattlePassScheduleExcelConfigData.json")
@Getter
public class BeyondBattlePassScheduleData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int levelRewardIndexId;

    public static int currentId() {
        int wanted = GameConstants.VERSION_PARTS[0] * 1000 + GameConstants.VERSION_PARTS[1] * 100;
        int best = 0;
        int lowest = Integer.MAX_VALUE;
        for (int id : GameData.getBeyondBattlePassScheduleDataMap().keySet()) {
            if (id <= wanted && id > best) best = id;
            if (id < lowest) lowest = id;
        }
        return best != 0 ? best : (lowest == Integer.MAX_VALUE ? 0 : lowest);
    }
}
