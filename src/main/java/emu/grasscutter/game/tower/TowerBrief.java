package emu.grasscutter.game.tower;

import dev.morphia.annotations.Entity;
import emu.grasscutter.net.proto.TowerMonthlyBriefOuterClass.TowerMonthlyBrief;

@Entity
public class TowerBrief {
    int scheduleId;
    int bestFloorIndex;
    int bestLevelIndex;
    int totalStars;

    public TowerBrief() {}

    public TowerBrief(int scheduleId, int bestFloorIndex, int bestLevelIndex, int totalStars) {
        this.scheduleId = scheduleId;
        this.bestFloorIndex = bestFloorIndex;
        this.bestLevelIndex = bestLevelIndex;
        this.totalStars = totalStars;
    }

    public TowerMonthlyBrief toProto() {
        return TowerMonthlyBrief.newBuilder()
                .setTowerScheduleId(scheduleId)
                .setBestFloorIndex(bestFloorIndex)
                .setBestLevelIndex(bestLevelIndex)
                .setTotalStarCount(totalStars)
                .build();
    }
}
