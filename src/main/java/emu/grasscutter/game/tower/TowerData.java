package emu.grasscutter.game.tower;

import dev.morphia.annotations.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
public class TowerData {
    int currentFloorId;

    int currentLevel;
    @Transient int currentLevelId;

    Map<Integer, TowerLevelRecord> recordMap;

    @Transient int entryScene;

    @Transient List<Integer> currentBuffOfferings = new ArrayList<>();

    @Transient int selectedTowerBuffId;
}
