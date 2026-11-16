package emu.grasscutter.data.excels.tower;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@ResourceType(name = "TowerScheduleExcelConfigData.json")
public class TowerScheduleData extends GameResource {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private int scheduleId;
    private List<Integer> entranceFloorId;
    @SerializedName(value = "schedules", alternate = {"LHOGNLPBILP"})
    private List<ScheduleDetail> schedules;
    private int monthlyLevelConfigId;
    private String closeTime;

    private transient LocalDateTime openDate;
    private transient LocalDateTime closeDate;

    @Override
    public int getId() {
        return scheduleId;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.schedules == null) {
            this.schedules = List.of();
            return;
        }
        this.openDate =
                this.schedules.stream()
                        .filter(Objects::nonNull)
                        .map(item -> parseTime(item.openTime))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
        this.closeDate = parseTime(this.closeTime);
        this.schedules =
                this.schedules.stream()
                        .filter(item -> item != null && item.getFloorList() != null && !item.getFloorList().isEmpty())
                        .toList();
    }

    private static LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDateTime.parse(text, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Date inZone(LocalDateTime time, ZoneId zone) {
        return time == null ? null : Date.from(time.atZone(zone).toInstant());
    }

    public int getScheduleId() {
        return scheduleId;
    }

    public List<Integer> getEntranceFloorId() {
        return entranceFloorId;
    }

    public List<ScheduleDetail> getSchedules() {
        return schedules;
    }

    public int getMonthlyLevelConfigId() {
        return monthlyLevelConfigId;
    }

    public Date getOpenTime(ZoneId zone) {
        return inZone(openDate, zone);
    }

    public Date getCloseTime(ZoneId zone) {
        return inZone(closeDate, zone);
    }

    public boolean isOpenAt(Date time, ZoneId zone) {
        var open = getOpenTime(zone);
        var close = getCloseTime(zone);
        return open != null && close != null && !time.before(open) && !time.after(close);
    }

    public static class ScheduleDetail {
        private List<Integer> floorList;
        private String openTime;

        public List<Integer> getFloorList() {
            return floorList;
        }
    }
}
