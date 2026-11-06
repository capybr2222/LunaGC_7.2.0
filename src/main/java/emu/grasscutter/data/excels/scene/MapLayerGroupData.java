package emu.grasscutter.data.excels.scene;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@ResourceType(name = "MapLayerGroupExcelConfigData.json")
@Getter
public final class MapLayerGroupData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    @SerializedName(value = "KIBILDMLEPI", alternate = {"FGPPGFADOCH"})
    private List<Integer> areaIds;

    @SerializedName(value = "DAMKLEIJMAG", alternate = {"FLEEHDLAOHE"})
    private float mapFloorId;
}
