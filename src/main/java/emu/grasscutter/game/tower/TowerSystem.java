package emu.grasscutter.game.tower;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.tower.TowerScheduleData;
import emu.grasscutter.server.game.*;
import emu.grasscutter.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class TowerSystem extends BaseGameSystem {

    private static final int ROTATION_DAY = 16;
    private static final int ROTATION_HOUR = 4;
    private static final long MONTHS_PER_YEAR = 12;
    private static final long CLOSE_TO_NEXT_OPEN_MS = 1000;

    private TowerScheduleConfig towerScheduleConfig;

    private List<Integer> playableSchedules;

    private final Map<Integer, Boolean> scriptedScenes = new HashMap<>();

    private Set<Integer> towerSceneIds;

    private ZoneId zone = ZoneId.systemDefault();

    public TowerSystem(GameServer server) {
        super(server);
        this.load();
    }

    public synchronized void load() {
        try {
            towerScheduleConfig = DataLoader.loadClass("TowerSchedule.json", TowerScheduleConfig.class);
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load tower schedule config.", e);
        }

        var zoneName = GAME_OPTIONS.tower.timeZone;
        if (zoneName == null || zoneName.isBlank()) return;
        try {
            zone = ZoneId.of(zoneName);
        } catch (DateTimeException e) {
            Grasscutter.getLogger()
                    .error("Spiral Abyss time zone {} is not valid, using {} instead.", zoneName, zone);
        }
    }

    public TowerScheduleConfig getTowerScheduleConfig() {
        return towerScheduleConfig;
    }

    public Set<Integer> getTowerSceneIds() {
        if (towerSceneIds != null) return towerSceneIds;

        towerSceneIds =
                GameData.getTowerLevelDataMap().values().stream()
                        .map(level -> GameData.getDungeonDataMap().get(level.getDungeonId()))
                        .filter(Objects::nonNull)
                        .map(DungeonData::getSceneId)
                        .filter(sceneId -> sceneId > 0)
                        .collect(Collectors.toUnmodifiableSet());
        return towerSceneIds;
    }

    private boolean isScripted(int sceneId) {
        return scriptedScenes.computeIfAbsent(
                sceneId,
                id -> {
                    var dir = FileUtils.getScriptPath("Scene/" + id);
                    if (!Files.isDirectory(dir)) return false;
                    try (var stream = Files.newDirectoryStream(dir, "scene" + id + "_group*.lua")) {
                        return stream.iterator().hasNext();
                    } catch (IOException e) {
                        return false;
                    }
                });
    }

    private static List<Integer> floorsOf(TowerScheduleData schedule) {
        var floors = new ArrayList<Integer>();
        if (schedule.getEntranceFloorId() != null) floors.addAll(schedule.getEntranceFloorId());
        if (schedule.getSchedules() != null) {
            schedule.getSchedules().stream()
                    .filter(Objects::nonNull)
                    .map(TowerScheduleData.ScheduleDetail::getFloorList)
                    .filter(Objects::nonNull)
                    .forEach(floors::addAll);
        }
        return floors;
    }

    private boolean isPlayable(TowerScheduleData schedule) {
        var floors = floorsOf(schedule);
        if (floors.isEmpty()) return false;

        for (int floorId : floors) {
            var floorData = GameData.getTowerFloorDataMap().get(floorId);
            if (floorData == null) return false;

            var levels =
                    GameData.getTowerLevelDataMap().values().stream()
                            .filter(level -> level.getLevelGroupId() == floorData.getLevelGroupId())
                            .toList();
            if (levels.isEmpty()) return false;

            for (var level : levels) {
                var dungeon = GameData.getDungeonDataMap().get(level.getDungeonId());
                if (dungeon == null || !isScripted(dungeon.getSceneId())) return false;
            }
        }
        return true;
    }

    public synchronized List<Integer> getPlayableSchedules() {
        if (playableSchedules != null) return playableSchedules;

        var all = GameData.getTowerScheduleDataMap();
        if (all.isEmpty()) return List.of();

        var playable =
                all.values().stream()
                        .filter(this::isPlayable)
                        .map(TowerScheduleData::getScheduleId)
                        .sorted()
                        .toList();

        if (playable.isEmpty()) {
            Grasscutter.getLogger()
                    .error(
                            "No Spiral Abyss rotation is playable: every one names dungeon scenes this server has no group scripts for.");
        } else {
            Grasscutter.getLogger()
                    .info(
                            "Spiral Abyss: {} of {} rotations are playable, newest is {}.",
                            playable.size(),
                            all.size(),
                            playable.get(playable.size() - 1));
        }

        playableSchedules = playable;
        return playableSchedules;
    }

    private List<Integer> getRotationPool() {
        var playable = getPlayableSchedules();
        int pool = GAME_OPTIONS.tower.rotationPool;
        if (pool <= 0 || pool >= playable.size()) return playable;
        return playable.subList(playable.size() - pool, playable.size());
    }

    private int getNewestPlayable() {
        var playable = getPlayableSchedules();
        return playable.isEmpty() ? 0 : playable.get(playable.size() - 1);
    }

    private Calendar periodStart(Date now) {
        var current = Calendar.getInstance(TimeZone.getTimeZone(zone));
        current.setTime(now);

        var start = (Calendar) current.clone();
        start.set(Calendar.DAY_OF_MONTH, ROTATION_DAY);
        start.set(Calendar.HOUR_OF_DAY, ROTATION_HOUR);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        if (start.after(current)) {
            start.add(Calendar.MONTH, -1);
        }
        return start;
    }

    private static Calendar periodEnd(Calendar start) {
        var end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        return end;
    }

    private static long periodOrdinal(Calendar start) {
        return start.get(Calendar.YEAR) * MONTHS_PER_YEAR + start.get(Calendar.MONTH);
    }

    private static boolean isRotating() {
        return GAME_OPTIONS.tower.scheduleId <= 0 && GAME_OPTIONS.tower.rotate;
    }

    private TowerScheduleData getLiveSchedule(Date now) {
        return GameData.getTowerScheduleDataMap().values().stream()
                .filter(schedule -> schedule.isOpenAt(now, zone))
                .max(Comparator.comparingInt(TowerScheduleData::getScheduleId))
                .orElse(null);
    }

    private TowerScheduleData getNewestPlayableOpenedBy(Date now) {
        var playable = getPlayableSchedules();
        for (int i = playable.size() - 1; i >= 0; i--) {
            var schedule = GameData.getTowerScheduleDataMap().get(playable.get(i).intValue());
            var opens = schedule != null ? schedule.getOpenTime(zone) : null;
            if (schedule != null && (opens == null || !opens.after(now))) {
                return schedule;
            }
        }
        var newest = getNewestPlayable();
        return newest > 0 ? GameData.getTowerScheduleDataMap().get(newest) : null;
    }

    private TowerScheduleData getCalendarWindow(Date now) {
        if (isRotating()) return null;
        var live = getLiveSchedule(now);
        return live != null && live.getOpenTime(zone) != null && live.getCloseTime(zone) != null ? live : null;
    }

    public Date getScheduleStartTime() {
        var now = new Date();
        var live = getCalendarWindow(now);
        if (live != null) return live.getOpenTime(zone);
        return periodStart(now).getTime();
    }

    public Date getNextScheduleChangeTime() {
        var now = new Date();
        var live = getCalendarWindow(now);
        if (live != null) return new Date(live.getCloseTime(zone).getTime() + CLOSE_TO_NEXT_OPEN_MS);
        return periodEnd(periodStart(now)).getTime();
    }

    public TowerScheduleData getCurrentTowerScheduleData() {
        int pinned = GAME_OPTIONS.tower.scheduleId;
        if (pinned > 0) {
            var data = GameData.getTowerScheduleDataMap().get(pinned);
            if (data != null) return data;
            Grasscutter.getLogger()
                    .error("Spiral Abyss rotation {} does not exist; rotating instead.", pinned);
        }

        var now = new Date();
        if (isRotating()) {
            var pool = getRotationPool();
            if (!pool.isEmpty()) {
                int index = (int) Math.floorMod(periodOrdinal(periodStart(now)), pool.size());
                var data = GameData.getTowerScheduleDataMap().get(pool.get(index).intValue());
                if (data != null) return data;
            }
        } else {
            var live = getLiveSchedule(now);
            if (live != null && getPlayableSchedules().contains(live.getScheduleId())) return live;
            var data = getNewestPlayableOpenedBy(now);
            if (data != null) return data;
        }

        var fallback = GameData.getTowerScheduleDataMap().get(towerScheduleConfig.getScheduleId());
        if (fallback == null) {
            Grasscutter.getLogger()
                    .error(
                            "Could not get current tower schedule data by schedule id {}, please check your resource files",
                            towerScheduleConfig.getScheduleId());
        }
        return fallback;
    }

    public List<Integer> getAllFloors() {
        var schedule = this.getCurrentTowerScheduleData();
        if (schedule == null) return List.of();

        List<Integer> floors = new ArrayList<>(schedule.getEntranceFloorId());
        floors.addAll(this.getScheduleFloors());
        return floors;
    }

    public List<Integer> getScheduleFloors() {
        var schedule = this.getCurrentTowerScheduleData();
        if (schedule == null || schedule.getSchedules().isEmpty()) return List.of();
        return schedule.getSchedules().get(0).getFloorList();
    }

    public int getNextFloorId(int floorId) {
        var schedule = this.getCurrentTowerScheduleData();
        if (schedule == null) return 0;

        var entranceFloors = schedule.getEntranceFloorId();
        var scheduleFloors = getScheduleFloors();
        var nextId = 0;

        for (int i = 0; i < entranceFloors.size() - 1; i++) {
            if (floorId == entranceFloors.get(i)) {
                nextId = entranceFloors.get(i + 1);
            }
        }

        if (!entranceFloors.isEmpty()
                && floorId == entranceFloors.get(entranceFloors.size() - 1)
                && !scheduleFloors.isEmpty()) {
            nextId = scheduleFloors.get(0);
        }

        if (nextId != 0) {
            return nextId;
        }

        for (int i = 0; i < scheduleFloors.size() - 1; i++) {
            if (floorId == scheduleFloors.get(i)) {
                nextId = scheduleFloors.get(i + 1);
            }
        }
        return nextId;
    }

    public Integer getLastEntranceFloor() {
        var schedule = this.getCurrentTowerScheduleData();
        if (schedule == null || schedule.getEntranceFloorId().isEmpty()) return 0;
        return schedule.getEntranceFloorId().get(schedule.getEntranceFloorId().size() - 1);
    }
}
