package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import lombok.Getter;

@ResourceType(name = "BeyondBattlePassMissionExcelConfigData.json")
@Getter
public class BeyondBattlePassMissionData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int progress;
    private boolean isDisuse;
}
