package emu.grasscutter.game.tower;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.tower.TowerLevelData;
import java.util.*;

public final class TowerBuffHelper {
    private static final List<Integer> FALLBACK_OFFERINGS = List.of(4, 28, 19);

    private TowerBuffHelper() {}

    public static List<Integer> rollOfferings(TowerLevelData levelData, int scheduleId) {
        if (levelData == null || levelData.getTowerBuffConfigStrList() == null) {
            return new ArrayList<>(FALLBACK_OFFERINGS);
        }

        var random = new Random(Objects.hash(scheduleId, levelData.getId()));
        var offerings = new ArrayList<Integer>(3);
        for (String pool : levelData.getTowerBuffConfigStrList()) {
            int buffId = pickWeighted(pool, random);
            if (buffId > 0) {
                offerings.add(buffId);
            }
        }

        if (offerings.isEmpty()) {
            return new ArrayList<>(FALLBACK_OFFERINGS);
        }
        return offerings;
    }

    public static boolean isOffered(int towerBuffId, List<Integer> offerings) {
        return towerBuffId > 0 && offerings != null && offerings.contains(towerBuffId);
    }

    public static int getServerBuffId(int towerBuffId) {
        var buffData = GameData.getTowerBuffDataMap().get(towerBuffId);
        return buffData == null ? 0 : buffData.getBuffId();
    }

    private static int pickWeighted(String pool, Random random) {
        if (pool == null || pool.isBlank()) {
            return 0;
        }

        var entries = new ArrayList<int[]>();
        int totalWeight = 0;
        for (String entry : pool.split(",")) {
            String[] parts = entry.trim().split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                int buffId = Integer.parseInt(parts[0].trim());
                int weight = Integer.parseInt(parts[1].trim());
                if (buffId > 0 && weight > 0) {
                    entries.add(new int[] {buffId, weight});
                    totalWeight += weight;
                }
            } catch (NumberFormatException ignored) {

            }
        }

        if (entries.isEmpty()) {
            return 0;
        }

        int roll = random.nextInt(totalWeight);
        for (int[] entry : entries) {
            roll -= entry[1];
            if (roll < 0) {
                return entry[0];
            }
        }
        return entries.get(entries.size() - 1)[0];
    }
}
