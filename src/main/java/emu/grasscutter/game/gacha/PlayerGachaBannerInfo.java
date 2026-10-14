package emu.grasscutter.game.gacha;

import dev.morphia.annotations.Entity;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.*;

@Entity
public class PlayerGachaBannerInfo {
    @Getter @Setter private int totalPulls = 0;
    @Getter @Setter private int pity5 = 0;
    @Getter @Setter private int pity4 = 0;
    private int failedFeaturedItemPulls = 0;
    private int failedFeatured4ItemPulls = 0;
    private int pity5Pool1 = 0;
    private int pity5Pool2 = 0;
    private int pity4Pool1 = 0;
    private int pity4Pool2 = 0;

    private int failedChosenItemPulls = 0;
    private int wishItemId = 0;
    private Map<Integer, Integer> wishItems = new HashMap<>();
    private Map<Integer, Integer> wishProgress = new HashMap<>();

    @Getter @Setter private int capturingRadianceCounter = 1;

    public void addTotalPulls(int amount) {
        this.totalPulls += amount;
    }

    public void addPity5(int amount) {
        this.pity5 += amount;
    }

    public void addPity4(int amount) {
        this.pity4 += amount;
    }

    private void migrateLegacyWish(GachaBanner banner) {
        if (this.wishItems == null) this.wishItems = new HashMap<>();
        if (this.wishProgress == null) this.wishProgress = new HashMap<>();
        if (this.wishItemId != 0
                && Arrays.stream(banner.getRateUpItems5()).anyMatch(id -> id == this.wishItemId)) {
            this.wishItems.putIfAbsent(banner.getScheduleId(), this.wishItemId);
            this.wishProgress.putIfAbsent(banner.getScheduleId(), this.failedChosenItemPulls);
            this.wishItemId = 0;
            this.failedChosenItemPulls = 0;
        }
    }

    public int getWishItemId(GachaBanner banner) {
        if (!banner.hasEpitomized()) return 0;
        this.migrateLegacyWish(banner);
        return this.wishItems.getOrDefault(banner.getScheduleId(), 0);
    }

    public int getWishProgress(GachaBanner banner) {
        if (this.getWishItemId(banner) == 0) return 0;
        return this.wishProgress.getOrDefault(banner.getScheduleId(), 0);
    }

    public void setWishItemId(GachaBanner banner, int itemId) {
        if (this.getWishItemId(banner) == itemId) return;
        if (itemId == 0) {
            this.wishItems.remove(banner.getScheduleId());
            this.wishProgress.remove(banner.getScheduleId());
        } else {
            this.wishItems.put(banner.getScheduleId(), itemId);
            this.wishProgress.put(banner.getScheduleId(), 0);
        }
    }

    public void setWishProgress(GachaBanner banner, int progress) {
        this.migrateLegacyWish(banner);
        this.wishProgress.put(banner.getScheduleId(), progress);
    }

    public void onFiftyFifty(boolean won, boolean captured, int max) {
        if (captured) {
            capturingRadianceCounter = 1;
        } else if (won) {
            capturingRadianceCounter = capturingRadianceCounter == 1 ? 0 : 1;
        } else {
            capturingRadianceCounter = Math.min(capturingRadianceCounter + 1, max);
        }
    }

    public int getFailedFeaturedItemPulls(int rarity) {
        return switch (rarity) {
            case 4 -> failedFeatured4ItemPulls;
            default -> failedFeaturedItemPulls;
        };
    }

    public void setFailedFeaturedItemPulls(int rarity, int amount) {
        if (rarity == 4) {
            failedFeatured4ItemPulls = amount;
        } else {
            failedFeaturedItemPulls = amount;
        }
    }

    public void addFailedFeaturedItemPulls(int rarity, int amount) {
        if (rarity == 4) {
            failedFeatured4ItemPulls += amount;
        } else {
            failedFeaturedItemPulls += amount;
        }
    }

    public int getPityPool(int rarity, int pool) {
        return switch (rarity) {
            case 4 -> switch (pool) {
                case 1 -> pity4Pool1;
                default -> pity4Pool2;
            };
            default -> switch (pool) {
                case 1 -> pity5Pool1;
                default -> pity5Pool2;
            };
        };
    }

    public void setPityPool(int rarity, int pool, int amount) {
        switch (rarity) {
            case 4:
                if (pool == 1) {
                    pity4Pool1 = amount;
                } else {
                    pity4Pool2 = amount;
                }
                break;
            case 5:
            default:
                if (pool == 1) {
                    pity5Pool1 = amount;
                } else {
                    pity5Pool2 = amount;
                }
                break;
        }
    }

    public void addPityPool(int rarity, int pool, int amount) {
        switch (rarity) {
            case 4:
                if (pool == 1) {
                    pity4Pool1 += amount;
                } else {
                    pity4Pool2 += amount;
                }
                break;
            case 5:
            default:
                if (pool == 1) {
                    pity5Pool1 += amount;
                } else {
                    pity5Pool2 += amount;
                }
                break;
        }
    }

    public void incPityAll() {
        pity4++;
        pity5++;
        pity4Pool1++;
        pity4Pool2++;
        pity5Pool1++;
        pity5Pool2++;
    }
}
