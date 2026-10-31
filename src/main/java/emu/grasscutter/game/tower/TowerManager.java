package emu.grasscutter.game.tower;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.tower.TowerLevelData;
import emu.grasscutter.game.dungeons.*;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.proto.EnterTypeOuterClass.EnterType;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import emu.grasscutter.server.packet.send.*;
import java.util.*;
import lombok.*;

public class TowerManager extends BasePlayerManager {
    private static final List<DungeonSettleListener> towerDungeonSettleListener =
            List.of(new TowerDungeonSettleListener());

    private static final Set<Integer> TOWER_START_KEY_GADGET_IDS = Set.of(70360010, 70350023);

    private static final int LEVELS_PER_FLOOR = 3;
    private static final int STARS_PER_LEVEL = 3;
    private static final int STARS_PER_FLOOR = LEVELS_PER_FLOOR * STARS_PER_LEVEL;

    private static final int ABYSS_ENTRANCE_SCENE = 3;

    private static final int ABYSS_ENTRANCE_POINT = 45;
    private static final Position ABYSS_ENTRANCE_FALLBACK =
            new Position(1638.7793f, 195.0458f, -2659.1758f);
    private static final Position ABYSS_ENTRANCE_ROTATION = new Position(0f, 181.4546f, 0f);

    private static final Position DEFAULT_CHAMBER_POS = new Position(0f, -0.102f, 15.011f);

    private static final Position DEFAULT_CHAMBER_ROT = new Position(0f, 180f, 0f);

    private int currentPossibleStars = 0;

    private final Set<Integer> alertedMonsterIds = new HashSet<>();
    @Getter private boolean inProgress;
    @Getter private int currentTimeLimit;

    private boolean upperHalf = true;

    public TowerManager(Player player) {
        super(player);
    }

    public TowerData getTowerData() {
        return this.getPlayer().getTowerData();
    }

    public int getCurrentFloorId() {
        return this.getTowerData().currentFloorId;
    }

    public int getCurrentFloorNumber() {
        var floorData = GameData.getTowerFloorDataMap().get(getCurrentFloorId());
        return floorData != null ? floorData.getFloorIndex() : 0;
    }

    public int getCurrentLevelId() {
        return this.getTowerData().currentLevelId + this.getTowerData().currentLevel;
    }

    public int getCurrentLevel() {
        return this.getTowerData().currentLevel + 1;
    }

    public void onTick() {
        if (!inProgress) return;

        var scene = player.getScene();
        var challenge = scene != null ? scene.getChallenge() : null;
        if (challenge == null || !challenge.inProgress()) return;

        alertNewChamberMonsters();

        int stars = getCurLevelStars();
        while (stars < currentPossibleStars) {
            player
                    .getSession()
                    .send(
                            new PacketTowerLevelStarCondNotify(
                                    getTowerData().currentFloorId,
                                    getCurrentLevel(),
                                    currentPossibleStars));
            currentPossibleStars--;
        }
    }

    public void onBegin() {
        var scene = player.getScene();
        var challenge = scene != null ? scene.getChallenge() : null;
        inProgress = true;
        currentTimeLimit = challenge != null ? challenge.getTimeLimit() : 0;

        notifyCurLevelRecordChange();

        hideStartKeyOptions();

        player.sendPacket(new PacketCanUseSkillNotify(true));

        alertedMonsterIds.clear();

        this.fillTeamEnergy();
    }

    private void alertNewChamberMonsters() {
        var scene = player.getScene();
        if (scene == null) return;

        for (var entity : scene.getEntities().values()) {
            if (!(entity instanceof EntityMonster monster)) continue;
            if (monster.getGroupId() <= 0) continue;
            if (!alertedMonsterIds.add(monster.getId())) continue;

            scene.broadcastPacket(new PacketMonsterForceAlertNotify(monster.getId()));
        }
    }

    private void hideStartKeyOptions() {
        var scene = player.getScene();
        if (scene == null) return;

        for (var entity : scene.getEntities().values()) {
            if (!(entity instanceof EntityGadget gadget)
                    || !TOWER_START_KEY_GADGET_IDS.contains(gadget.getGadgetId())
                    || !(gadget.getContent() instanceof GadgetWorktop worktop)
                    || worktop.getWorktopOptions().isEmpty()) {
                continue;
            }

            worktop.getWorktopOptions().clear();
            scene.broadcastPacket(new PacketWorktopOptionNotify(gadget));
        }
    }

    private void resendStartKeyOptions() {
        var scene = player.getScene();
        if (scene == null) return;

        for (var entity : scene.getEntities().values()) {
            if (entity instanceof EntityGadget gadget
                    && TOWER_START_KEY_GADGET_IDS.contains(gadget.getGadgetId())
                    && gadget.getContent() instanceof GadgetWorktop worktop
                    && !worktop.getWorktopOptions().isEmpty()) {
                scene.broadcastPacket(new PacketWorktopOptionNotify(gadget));
            }
        }
    }

    private void fillTeamEnergy() {
        player
                .getTeamManager()
                .getActiveTeam()
                .forEach(
                        entity -> {
                            var depot = entity.getAvatar().getSkillDepot();
                            if (depot == null) return;

                            var energySkill = depot.getEnergySkillData();
                            if (energySkill != null && energySkill.getSpecialEnergyMin() > 0) {
                                entity.addSpecialEnergy(
                                        entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY));
                                return;
                            }

                            var element = depot.getElementType();
                            if (element == null) return;

                            float max = entity.getFightProperty(element.getMaxEnergyProp());
                            if (max <= 0) return;

                            entity.addEnergy(
                                    max, PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY, true);
                        });
    }

    public void onEnd() {
        inProgress = false;
    }

    public Map<Integer, TowerLevelRecord> getRecordMap() {
        Map<Integer, TowerLevelRecord> recordMap = getTowerData().recordMap;
        if (recordMap == null) {
            recordMap = new HashMap<>();
            getTowerData().recordMap = recordMap;
        }
        grantEntranceFloors(recordMap);
        return recordMap;
    }

    private void grantEntranceFloors(Map<Integer, TowerLevelRecord> recordMap) {
        if (!GAME_OPTIONS.tower.skipEntranceFloors) return;

        var schedule = player.getServer().getTowerSystem().getCurrentTowerScheduleData();
        if (schedule == null) return;

        var entranceFloors = schedule.getEntranceFloorId();
        if (entranceFloors == null || entranceFloors.isEmpty()) return;

        for (int floorId : entranceFloors) {
            int firstLevelId = getFirstLevelId(floorId);
            if (firstLevelId == 0) continue;

            var record = recordMap.computeIfAbsent(floorId, TowerLevelRecord::new);
            if (record.getPassedLevelMap() == null) {
                record.setPassedLevelMap(new HashMap<>());
            }

            record
                    .getPassedLevelMap()
                    .keySet()
                    .removeIf(id -> id < firstLevelId || id >= firstLevelId + LEVELS_PER_FLOOR);

            if (record.getStarCount() >= STARS_PER_FLOOR) continue;

            for (int i = 0; i < LEVELS_PER_FLOOR; i++) {
                record.setLevelStars(firstLevelId + i, STARS_PER_LEVEL);
            }
            record.setFloorStarRewardProgress(STARS_PER_FLOOR);
        }

        int firstScheduleFloor =
                player
                        .getServer()
                        .getTowerSystem()
                        .getNextFloorId(entranceFloors.get(entranceFloors.size() - 1));
        if (firstScheduleFloor > 0) {
            recordMap.computeIfAbsent(firstScheduleFloor, TowerLevelRecord::new);
        }
    }

    private static int getFirstLevelId(int floorId) {
        var floorData = GameData.getTowerFloorDataMap().get(floorId);
        if (floorData == null) return 0;
        return GameData.getTowerLevelDataMap().values().stream()
                .filter(x -> x.getLevelGroupId() == floorData.getLevelGroupId() && x.getLevelIndex() == 1)
                .findFirst()
                .map(TowerLevelData::getId)
                .orElse(0);
    }

    public void teamSelect(int floor, List<List<Long>> towerTeams) {
        var floorData = GameData.getTowerFloorDataMap().get(floor);
        if (floorData == null) {
            Grasscutter.getLogger().warn("Tower team select for unknown floor {}", floor);
            return;
        }

        getTowerData().currentFloorId = floorData.getFloorId();

        getTowerData().currentLevel = findResumeChamberIndex(floorData.getFloorId());
        getTowerData().currentLevelId = getFirstLevelId(floorData.getFloorId());

        if (getTowerData().entryScene == 0) {
            getTowerData().entryScene = player.getSceneId();
        }

        Grasscutter.getLogger()
                .info(
                        "Tower team select on floor {}: {} team(s), sizes {}, resuming at chamber {}",
                        floor,
                        towerTeams.size(),
                        towerTeams.stream().map(List::size).toList(),
                        getCurrentLevel());

        player.getTeamManager().setupTemporaryTeam(towerTeams);
    }

    private int findResumeChamberIndex(int floorId) {
        int firstLevelId = getFirstLevelId(floorId);
        if (firstLevelId == 0) return 0;

        var record = getRecordMap().get(floorId);
        if (record == null || record.getPassedLevelMap() == null) return 0;

        for (int i = 0; i < LEVELS_PER_FLOOR; i++) {
            if (record.getLevelStars(firstLevelId + i) <= 0) return i;
        }
        return 0;
    }

    public TowerLevelData getCurrentTowerLevelDataMap() {
        var levelData = GameData.getTowerLevelDataMap().get(getCurrentLevelId());
        if (levelData != null) return levelData;

        var floorData = GameData.getTowerFloorDataMap().get(getCurrentFloorId());
        if (floorData == null) return null;

        return GameData.getTowerLevelDataMap().values().stream()
                .filter(x -> x.getLevelGroupId() == floorData.getLevelGroupId())
                .filter(x -> x.getLevelIndex() == getCurrentLevel())
                .findFirst()
                .orElse(null);
    }

    public int getCurrentMonsterLevel() {
        var levelData = getCurrentTowerLevelDataMap();
        if (levelData != null) {
            return levelData.getMonsterLevel() + 1;
        }
        var floorData = GameData.getTowerFloorDataMap().get(getCurrentFloorId());
        Grasscutter.getLogger()
                .warn("No tower level data for level {}, falling back to the floor level", getCurrentLevelId());
        return floorData != null ? floorData.getOverrideMonsterLevel() : 1;
    }

    public List<Integer> rollCurrentChamberBuffs() {
        var levelData = getCurrentTowerLevelDataMap();
        var schedule = player.getServer().getTowerSystem().getCurrentTowerScheduleData();
        int scheduleId = schedule == null ? 0 : schedule.getScheduleId();

        var offerings = TowerBuffHelper.rollOfferings(levelData, scheduleId);
        var towerData = getTowerData();
        towerData.currentBuffOfferings = new ArrayList<>(offerings);
        towerData.selectedTowerBuffId = 0;
        return offerings;
    }

    public boolean selectTowerBuff(int towerBuffId) {
        var towerData = getTowerData();
        if (!TowerBuffHelper.isOffered(towerBuffId, towerData.currentBuffOfferings)) {
            Grasscutter.getLogger()
                    .warn(
                            "Tower buff {} was not offered on floor {} level {} (offered: {})",
                            towerBuffId,
                            getCurrentFloorId(),
                            getCurrentLevel(),
                            towerData.currentBuffOfferings);
            return false;
        }

        towerData.selectedTowerBuffId = towerBuffId;
        int serverBuffId = TowerBuffHelper.getServerBuffId(towerBuffId);
        if (serverBuffId > 0) {
            player.getBuffManager().addBuff(serverBuffId);
        }
        notifyCurLevelRecordChange();
        return true;
    }

    public void enterLevel(int enterPointId, boolean restartFloor) {
        if (getTowerData().currentLevelId == 0) {
            getTowerData().currentLevelId = getFirstLevelId(getTowerData().currentFloorId);
        }

        var levelData = getCurrentTowerLevelDataMap();
        if (levelData == null) {
            Grasscutter.getLogger()
                    .warn(
                            "Tower enter level {} on floor {} has no level data",
                            getCurrentLevelId(),
                            getCurrentFloorId());
            return;
        }

        var dungeonId = levelData.getDungeonId();
        if (!restartFloor && isAlreadyInChamber(dungeonId)) {

            notifyCurLevelRecordChange();
            return;
        }

        upperHalf = true;
        currentPossibleStars = STARS_PER_LEVEL;
        notifyCurLevelRecordChange();

        player.getTeamManager().setCurrentCharacterIndex(0);
        player.getTeamManager().useTemporaryTeam(0);
        player.getTeamManager().forceActiveCharacter(0);

        player
                .getServer()
                .getDungeonSystem()
                .handoffDungeon(player, dungeonId, towerDungeonSettleListener);

        player.getScene().setPrevScene(getTowerData().entryScene);
        player.getScene().setPrevScenePoint(enterPointId);

        var buffOfferings = rollCurrentChamberBuffs();
        player
                .getSession()
                .send(
                        new PacketTowerEnterLevelRsp(
                                getTowerData().currentFloorId, getCurrentLevel(), buffOfferings));
    }

    private boolean isAlreadyInChamber(int dungeonId) {
        var scene = player.getScene();
        var dungeon = GameData.getDungeonDataMap().get(dungeonId);
        return scene != null
                && dungeon != null
                && scene.getId() == dungeon.getSceneId()
                && scene.getDungeonManager() != null
                && scene.getDungeonManager().isTowerDungeon();
    }

    public void onChamberReady() {
        var scene = player.getScene();
        if (scene == null || !isInAbyssScene(scene.getId())) return;

        resendStartKeyOptions();
        placePartyAtChamberEntrance(scene);

        player.sendPacket(new PacketCanUseSkillNotify(true));
    }

    public void notifyCurLevelRecordChange() {
        player
                .getSession()
                .send(
                        new PacketTowerCurLevelRecordChangeNotify(
                                getTowerData().currentFloorId,
                                getCurrentLevel(),
                                upperHalf,
                                getTowerData().selectedTowerBuffId));
    }

    public int getCurLevelStars() {
        var scene = player.getScene();
        var challenge = scene != null ? scene.getChallenge() : null;
        if (challenge == null) {
            Grasscutter.getLogger().error("getCurLevelStars: no challenge registered!");
            return 0;
        }

        var levelData = getCurrentTowerLevelDataMap();
        if (levelData == null) {
            Grasscutter.getLogger().error("getCurLevelStars: no level data for {}", getCurrentLevelId());
            return 0;
        }

        int elapsed =
                challenge.inProgress()
                        ? Math.max(0, scene.getSceneTimeSeconds() - challenge.getStartedAt())
                        : Math.max(0, challenge.getFinishedTime());
        int timeRemaining = challenge.getTimeLimit() - elapsed;

        int star;
        for (star = 2; star >= 0; star--) {
            var cond = levelData.getCondType(star);
            if (cond == TowerLevelData.TowerCondType.TOWER_COND_CHALLENGE_LEFT_TIME_MORE_THAN) {
                var params = levelData.getTimeCond(star);
                if (params != null && timeRemaining >= params.getMinimumTimeInSeconds()) {
                    break;
                }
            } else if (cond == TowerLevelData.TowerCondType.TOWER_COND_LEFT_HP_GREATER_THAN) {
                var params = levelData.getHpCond(star);
                if (params != null
                        && challenge.getGuardEntityHpPercent() >= params.getMinimumHpPercentage()) {
                    break;
                }
            } else {
                Grasscutter.getLogger()
                        .error(
                                "getCurLevelStars: Tower level {} has no or unknown condition defined for {} stars",
                                getCurrentLevelId(),
                                star + 1);
                continue;
            }
        }

        int stars = star + 1;

        Grasscutter.getLogger()
                .debug(
                        "getCurLevelStars: level {} left {}s => {} star(s)",
                        getCurrentLevelId(),
                        timeRemaining,
                        stars);
        return stars;
    }

    public void notifyCurLevelRecordChangeWhenDone(int stars) {
        Map<Integer, TowerLevelRecord> recordMap = this.getRecordMap();
        int currentFloorId = getTowerData().currentFloorId;
        int levelId = getCurrentLevelId();

        var record = recordMap.computeIfAbsent(currentFloorId, TowerLevelRecord::new);
        if (record.getPassedLevelMap() == null) {
            record.setPassedLevelMap(new HashMap<>());
        }
        if (stars > record.getLevelStars(levelId)) {
            record.setLevelStars(levelId, stars);
        }

        this.getTowerData().currentLevel++;
        upperHalf = true;

        if (hasNextLevel()) {
            player
                    .getSession()
                    .send(new PacketTowerCurLevelRecordChangeNotify(currentFloorId, getCurrentLevel()));
        } else {

            int nextFloorId = this.getNextFloorId();
            if (nextFloorId > 0) {
                recordMap.computeIfAbsent(nextFloorId, TowerLevelRecord::new);
            }
            player.getSession().send(PacketTowerCurLevelRecordChangeNotify.empty());
        }

        player.save();
    }

    public boolean hasNextLevel() {
        return getTowerData().currentLevel < LEVELS_PER_FLOOR;
    }

    public int getNextFloorId() {
        return this.player
                .getServer()
                .getTowerSystem()
                .getNextFloorId(this.getTowerData().currentFloorId);
    }

    public boolean hasNextFloor() {
        return this.player
                        .getServer()
                        .getTowerSystem()
                        .getNextFloorId(this.getTowerData().currentFloorId)
                > 0;
    }

    public void clearEntry() {
        getTowerData().entryScene = 0;
    }

    public void clearInterruptedRun() {
        var data = getTowerData();
        data.currentFloorId = 0;
        data.currentLevel = 0;
        data.currentLevelId = 0;
        data.entryScene = 0;
        data.selectedTowerBuffId = 0;
        if (data.currentBuffOfferings != null) {
            data.currentBuffOfferings.clear();
        } else {
            data.currentBuffOfferings = new ArrayList<>();
        }

        inProgress = false;
        upperHalf = true;
        currentPossibleStars = 0;
    }

    public boolean isInAbyssScene(int sceneId) {
        return player.getServer().getTowerSystem().getTowerSceneIds().contains(sceneId);
    }

    public static Position abyssEntrancePosition() {
        var entry = GameData.getScenePointEntryById(ABYSS_ENTRANCE_SCENE, ABYSS_ENTRANCE_POINT);
        if (entry != null && entry.getPointData() != null) {
            var data = entry.getPointData();
            var pos = data.getTranPos() != null ? data.getTranPos() : data.getPos();
            if (pos != null) return new Position(pos);
        }
        return new Position(ABYSS_ENTRANCE_FALLBACK);
    }

    public static Position abyssEntranceRotation() {
        var entry = GameData.getScenePointEntryById(ABYSS_ENTRANCE_SCENE, ABYSS_ENTRANCE_POINT);
        if (entry != null && entry.getPointData() != null && entry.getPointData().getRot() != null) {
            return new Position(entry.getPointData().getRot());
        }
        return new Position(ABYSS_ENTRANCE_ROTATION);
    }

    public boolean canEnterScheduleFloor() {
        Map<Integer, TowerLevelRecord> recordMap = this.getRecordMap();
        if (!recordMap.containsKey(this.player.getServer().getTowerSystem().getLastEntranceFloor())) {
            return false;
        }
        return recordMap
                        .get(this.player.getServer().getTowerSystem().getLastEntranceFloor())
                        .getStarCount()
                >= 6;
    }

    public boolean claimFloorStarReward(int floorId) {
        var record = getRecordMap().get(floorId);
        var floorData = GameData.getTowerFloorDataMap().get(floorId);
        if (record == null || floorData == null) {
            Grasscutter.getLogger().warn("Tower claim stars: no record for floor {}", floorId);
            return false;
        }

        int claimed = Math.max(0, Math.min(STARS_PER_FLOOR, record.getFloorStarRewardProgress()));
        claimed = (claimed / STARS_PER_LEVEL) * STARS_PER_LEVEL;

        int tier = claimed + STARS_PER_LEVEL;
        if (tier > STARS_PER_FLOOR || record.getStarCount() < tier) return false;

        int rewardId =
                switch (tier) {
                    case 3 -> floorData.getRewardIdThreeStars();
                    case 6 -> floorData.getRewardIdSixStars();
                    default -> floorData.getRewardIdNineStars();
                };

        var reward = GameData.getRewardDataMap().get(rewardId);
        if (reward == null || reward.getRewardItemList() == null) {
            Grasscutter.getLogger()
                    .warn("Tower claim stars: missing reward {} for floor {} tier {}", rewardId, floorId, tier);
            return false;
        }

        var items = new ArrayList<GameItem>();
        for (var param : reward.getRewardItemList()) {
            if (param.getId() > 0 && param.getCount() > 0) {
                items.add(new GameItem(param.getId(), param.getCount()));
            }
        }

        if (!items.isEmpty()) {
            player.getInventory().addItems(items, ActionReason.TowerFloorStarReward);
        }
        record.setFloorStarRewardProgress(tier);
        player.save();
        return true;
    }

    public void sendFloorRecordUpdate(int floorId) {
        var record = getRecordMap().computeIfAbsent(floorId, TowerLevelRecord::new);
        player
                .getSession()
                .send(new PacketTowerFloorRecordChangeNotify(record, canEnterScheduleFloor()));
    }

    public void mirrorTeamSetUp(int teamId) {
        if (teamId < 0) return;

        upperHalf = false;
        notifyCurLevelRecordChange();

        applyMidHalfTeamSwap(teamId, player.getSceneId());

        queueSecondHalfEntry();
    }

    private void queueSecondHalfEntry() {
        var scene = player.getScene();
        if (scene == null || !isInAbyssScene(scene.getId())) return;

        var config = scene.getScriptManager() != null ? scene.getScriptManager().getConfig() : null;
        var teleportProperties =
                TeleportProperties.builder()
                        .sceneId(scene.getId())
                        .teleportType(TeleportType.INTERNAL)
                        .enterType(EnterType.EnterType_ENTER_DUNGEON)
                        .enterReason(EnterReason.DungeonEnter)
                        .teleportTo(
                                config != null && config.born_pos != null
                                        ? config.born_pos
                                        : DEFAULT_CHAMBER_POS)
                        .teleportRot(
                                config != null && config.born_rot != null
                                        ? config.born_rot
                                        : DEFAULT_CHAMBER_ROT);

        var dungeonManager = scene.getDungeonManager();
        if (dungeonManager != null && dungeonManager.getDungeonData() != null) {
            teleportProperties.dungeonId(dungeonManager.getDungeonData().getId());
        }

        player.getWorld().queueTransferPlayerToScene(player, teleportProperties.build(), 300);
    }

    private void applyMidHalfTeamSwap(int teamId, int expectedSceneId) {
        var scene = player.getScene();
        if (scene == null || scene.getId() != expectedSceneId || player.getSession() == null) {
            return;
        }

        var teamManager = player.getTeamManager();
        if (teamManager.getTemporaryTeamCount() <= teamId) {
            Grasscutter.getLogger()
                    .warn(
                            "Tower mid-half: team {} requested but only {} are set up",
                            teamId + 1,
                            teamManager.getTemporaryTeamCount());
            return;
        }

        teamManager.setCurrentCharacterIndex(0);
        teamManager.useTemporaryTeam(teamId);
        teamManager.forceActiveCharacter(0);

        placePartyAtChamberEntrance(scene);

        this.fillTeamEnergy();
        notifyCurLevelRecordChange();
    }

    private void placePartyAtChamberEntrance(Scene scene) {
        var config = scene.getScriptManager() != null ? scene.getScriptManager().getConfig() : null;
        var pos =
                config != null && config.born_pos != null ? config.born_pos : DEFAULT_CHAMBER_POS;
        var rot =
                config != null && config.born_rot != null ? config.born_rot : DEFAULT_CHAMBER_ROT;

        player.getPosition().set(pos);
        player.getRotation().set(rot);

        var avatar = player.getTeamManager().getCurrentAvatarEntity();
        if (avatar != null) {
            avatar.getPosition().set(pos);
            avatar.getRotation().set(rot);
            scene.broadcastPacket(
                    new PacketSceneEntityDisappearNotify(avatar, VisionType.VisionType_VISION_REPLACE));
            scene.broadcastPacket(
                    new PacketSceneEntityAppearNotify(
                            avatar, VisionType.VisionType_VISION_REPLACE, avatar.getId()));
        }
        scene.broadcastPacket(new PacketScenePlayerLocationNotify(scene));
    }

}
