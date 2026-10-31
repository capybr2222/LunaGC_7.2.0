package emu.grasscutter.game.player;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.props.ElementType;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public final class PhlogistonRegion {
    public static final int NATLAN_WEATHER = 6000;

    private static final int TEYVAT_SCENE = 3;
    private static final int NATLAN_SCENE = 101;
    private static final int AREA_TYPE_LEVEL_2 = 2;

    private static IntSet level1Areas;
    private static IntSet level2Areas;

    private PhlogistonRegion() {}

    public static boolean contains(int sceneId, int areaId, int areaType) {
        if (sceneId == NATLAN_SCENE) return true;
        if (sceneId != TEYVAT_SCENE || areaId == 0) return false;
        load();
        return areaType == AREA_TYPE_LEVEL_2 ? level2Areas.contains(areaId) : level1Areas.contains(areaId);
    }

    private static synchronized void load() {
        if (level1Areas != null) return;
        IntSet level1 = new IntOpenHashSet();
        IntSet level2 = new IntOpenHashSet();
        for (var area : GameData.getWorldAreaDataMap().values()) {
            if (area.getSceneId() != TEYVAT_SCENE || area.getElementType() != ElementType.Fire) continue;
            if (area.getChildArea() == 0) level1.add(area.getParentArea());
            else level2.add(area.getChildArea());
        }
        level2Areas = level2;
        level1Areas = level1;
    }
}
