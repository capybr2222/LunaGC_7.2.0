package emu.grasscutter.command.commands;

import static emu.grasscutter.GameConstants.*;
import static emu.grasscutter.command.CommandHelpers.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.data.excels.reliquary.*;
import emu.grasscutter.data.excels.weapon.WeaponPromoteData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.server.packet.send.PacketAvatarPropNotify;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.*;
import lombok.Setter;

@Command(
        label = "give",
        aliases = {"g", "item", "giveitem"},
        usage = {
            "(<itemId>|<avatarId>|all|weapons|mats|avatars) [lv<level>] [r<refinement>] [x<amount>] [c<constellation>] [sl<skilllevel>]",
            "<artifactId> [lv<level>] [x<amount>] [<mainPropId>] [<appendPropId>[,<times>]]..."
        },
        permission = "player.give",
        permissionTargeted = "player.give.others",
        threading = true)
public final class GiveCommand implements CommandHandler {
    private static final int GIVE_ALL_AVATAR_LEVEL = 1;
    private static final int GIVE_ALL_WEAPON_LEVEL = 90;
    private static final int GIVE_ALL_CONSTELLATION = 0;
    private static final int MAX_CONSTELLATION = 6;
    private static final int GIVE_ALL_WEAPON_COPIES = 6;
    private static final int GIVE_ALL_MORA = 1_000_000_000;
    private static final int FIRST_WEAPON_ID = 11100;
    private static final int LAST_WEAPON_ID = 16000;
    private static final int ITEMS_PER_PACKET = 100;

    private static final Map<Pattern, BiConsumer<GiveItemParameters, Integer>> intCommandHandlers =
            Map.ofEntries(
                    Map.entry(lvlRegex, GiveItemParameters::setLvl),
                    Map.entry(refineRegex, GiveItemParameters::setRefinement),
                    Map.entry(amountRegex, GiveItemParameters::setAmount),
                    Map.entry(constellationRegex, GiveItemParameters::setConstellation),
                    Map.entry(skillLevelRegex, GiveItemParameters::setSkillLevel));

    private static Avatar makeAvatar(GiveItemParameters param) {
        return makeAvatar(
                param.avatarData,
                param.lvl,
                Avatar.getMinPromoteLevel(param.lvl),
                param.constellation,
                param.skillLevel);
    }

    private static Avatar makeAvatar(
            AvatarData avatarData, int level, int promoteLevel, int constellation, int skillLevel) {
        Avatar avatar = new Avatar(avatarData);
        avatar.setLevel(level);
        avatar.setPromoteLevel(promoteLevel);
        avatar
                .getSkillDepot()
                .getSkillsAndEnergySkill()
                .forEach(id -> avatar.setSkillLevel(id, skillLevel));
        avatar.forceConstellationLevel(constellation);
        avatar.recalcStats(true);
        avatar.save();
        return avatar;
    }

    private static void updateAvatar(Player player, Avatar avatar, GiveItemParameters param) {
        if (param.lvlGiven) {
            avatar.setLevel(param.lvl);
            avatar.setPromoteLevel(Avatar.getMinPromoteLevel(param.lvl));
        }

        if (param.skillLevelGiven) {
            avatar
                    .getSkillDepot()
                    .getSkillsAndEnergySkill()
                    .forEach(id -> avatar.setSkillLevel(id, param.skillLevel));
        }

        boolean loweredConstellation = false;
        if (param.constellationGiven) {
            loweredConstellation = param.constellation < avatar.getCoreProudSkillLevel();
            avatar.forceConstellationLevel(param.constellation);
            avatar.recalcConstellations();
        }

        avatar.recalcStats(true);
        avatar.save();
        player.sendPacket(new PacketAvatarPropNotify(avatar));

        if (loweredConstellation) {
            World world = player.getWorld();
            Scene scene = player.getScene();
            Position pos = player.getPosition();
            world.transferPlayerToScene(player, 1, pos);
            world.transferPlayerToScene(player, scene.getId(), pos);
            scene.broadcastPacket(new PacketSceneEntityAppearNotify(player));
        }
    }

    private static boolean isGiveAllAvatar(AvatarData avatarData) {
        return "AVATAR_FORMAL".equals(avatarData.getUseType())
                && GameData.getFetterDataEntries().containsKey(avatarData.getId());
    }

    private static boolean isOtherTraveler(Player player, AvatarData avatarData) {
        return avatarData.getCandSkillDepotIds() != null
                && !avatarData.getCandSkillDepotIds().isEmpty()
                && avatarData.getId() != player.getMainCharacterId();
    }

    private static boolean isGiveAllWeapon(ItemData itemData) {
        int id = itemData.getId();
        return id >= FIRST_WEAPON_ID
                && id <= LAST_WEAPON_ID
                && !ILLEGAL_WEAPONS.contains(id)
                && itemData.isEquip()
                && itemData.getItemType() == ItemType.ITEM_WEAPON;
    }

    private static boolean isExperienceMaterial(ItemData itemData) {
        return itemData.getMaterialType() == MaterialType.MATERIAL_EXP_FRUIT
                || itemData.getMaterialType() == MaterialType.MATERIAL_WEAPON_EXP_STONE;
    }

    private static List<AvatarSkillDepotData> skillDepots(AvatarData avatarData) {
        List<Integer> depotIds = avatarData.getCandSkillDepotIds();
        if (depotIds == null || depotIds.isEmpty()) depotIds = List.of(avatarData.getSkillDepotId());
        return depotIds.stream()
                .map(depotId -> GameData.getAvatarSkillDepotDataMap().get((int) depotId))
                .filter(Objects::nonNull)
                .toList();
    }

    private static void countLockedConstellationItems(
            Avatar avatar, AvatarData avatarData, Int2IntMap itemCounts) {
        for (AvatarSkillDepotData depot : skillDepots(avatarData)) {
            if (depot.getTalents() == null) continue;
            for (int talentId : depot.getTalents()) {
                if (avatar.hasTalent(talentId)) continue;
                AvatarTalentData talent = GameData.getAvatarTalentDataMap().get(talentId);
                if (talent == null || talent.getMainCostItemId() == 0) continue;
                itemCounts.mergeInt(
                        talent.getMainCostItemId(), talent.getMainCostItemCount(), Integer::sum);
            }
        }
    }

    private static void topUpItems(Player player, Int2IntMap targetCounts) {
        Inventory inventory = player.getInventory();
        List<GameItem> items = new ArrayList<>();
        for (Int2IntMap.Entry target : targetCounts.int2IntEntrySet()) {
            ItemData data = GameData.getItemDataMap().get(target.getIntKey());
            if (data == null || data.getItemType() != ItemType.ITEM_MATERIAL) continue;
            int targetCount = Math.min(target.getIntValue(), data.getStackLimit());
            int missingCount = targetCount - inventory.getItemCountById(data.getId());
            if (missingCount > 0) items.add(new GameItem(data, missingCount));
        }
        addItemsChunked(player, items, ITEMS_PER_PACKET);
    }

    private static void giveAllAvatars(Player player, GiveItemParameters param) {
        int granted = 0, updated = 0, failed = 0;
        int level = param.lvlGiven ? param.lvl : GIVE_ALL_AVATAR_LEVEL;
        int promoteLevel = Avatar.getMinPromoteLevel(level);
        int constellation =
                param.constellationGiven
                        ? Math.max(0, Math.min(param.constellation, MAX_CONSTELLATION))
                        : GIVE_ALL_CONSTELLATION;
        Int2IntMap constellationItems = new Int2IntOpenHashMap();

        for (AvatarData avatarData : GameData.getAvatarDataMap().values()) {
            if (!isGiveAllAvatar(avatarData) || isOtherTraveler(player, avatarData)) continue;
            int id = avatarData.getId();
            try {
                Avatar avatar = player.getAvatars().getAvatarById(id);
                if (avatar != null) {
                    if (!param.lvlGiven && avatar.getLevel() < level) {
                        avatar.setLevel(level);
                        avatar.setPromoteLevel(Math.max(avatar.getPromoteLevel(), promoteLevel));
                    }
                    updateAvatar(player, avatar, param);
                    updated++;
                } else {
                    avatar = makeAvatar(avatarData, level, promoteLevel, constellation, param.skillLevel);
                    player.addAvatar(avatar, false);
                    granted++;
                }
                countLockedConstellationItems(avatar, avatarData, constellationItems);
            } catch (Exception e) {
                failed++;
                Grasscutter.getLogger().warn("Could not grant avatar {}", id, e);
            }
        }

        topUpItems(player, constellationItems);
        Grasscutter.getLogger()
                .info("give avatars: {} granted, {} updated, {} failed", granted, updated, failed);
    }

    private static List<GameItem> makeUnstackableItems(GiveItemParameters param) {
        int promoteLevel = GameItem.getMinPromoteLevel(param.lvl);
        int totalExp =
                param.data.getItemType() == ItemType.ITEM_WEAPON ? totalWeaponExp(param.data, param.lvl) : 0;

        List<GameItem> items = new ArrayList<>(param.amount);
        for (int i = 0; i < param.amount; i++) {
            GameItem item = new GameItem(param.data);
            item.setLevel(param.lvl);
            if (item.getItemType() == ItemType.ITEM_WEAPON) {
                item.setPromoteLevel(promoteLevel);
                item.setTotalExp(totalExp);
                item.setRefinement(param.refinement - 1);
            }
            items.add(item);
        }
        return items;
    }

    private static List<GameItem> makeArtifacts(GiveItemParameters param) {
        param.lvl = Math.min(param.lvl, param.data.getMaxLevel());
        int rank = param.data.getRankLevel();
        int totalExp = 0;
        for (int i = 1; i < param.lvl; i++) totalExp += GameData.getRelicExpRequired(rank, i);

        List<GameItem> items = new ArrayList<>(param.amount);
        for (int i = 0; i < param.amount; i++) {
            GameItem item = new GameItem(param.data);
            item.setLevel(param.lvl);
            item.setTotalExp(totalExp);
            int numAffixes = param.data.getAppendPropNum() + (param.lvl - 1) / 4;
            if (param.mainPropId > 0)
            item.setMainPropId(param.mainPropId);
            if (param.appendPropIdList != null) {
                item.getAppendPropIdList().clear();
                item.getAppendPropIdList().addAll(param.appendPropIdList);
            }
            item.addAppendProps(numAffixes - item.getAppendPropIdList().size());
            items.add(item);
        }
        return items;
    }

    private static int getArtifactMainProp(ItemData itemData, FightProperty prop)
            throws IllegalArgumentException {
        if (prop != FightProperty.FIGHT_PROP_NONE)
            for (ReliquaryMainPropData data :
                    GameDepot.getRelicMainPropList(itemData.getMainPropDepotId()))
                if (data.getWeight() > 0 && data.getFightProp() == prop) return data.getId();
        throw new IllegalArgumentException();
    }

    private static List<Integer> getArtifactAffixes(ItemData itemData, FightProperty prop)
            throws IllegalArgumentException {
        if (prop == FightProperty.FIGHT_PROP_NONE) {
            throw new IllegalArgumentException();
        }
        List<Integer> affixes = new ArrayList<>();
        for (ReliquaryAffixData data : GameDepot.getRelicAffixList(itemData.getAppendPropDepotId())) {
            if (data.getWeight() > 0 && data.getFightProp() == prop) {
                affixes.add(data.getId());
            }
        }
        return affixes;
    }

    private static int getAppendPropId(String substatText, ItemData itemData)
            throws IllegalArgumentException {
        try {
            return Integer.parseInt(substatText);
        } catch (NumberFormatException ignored) {
            String[] substatArgs = substatText.split("_");
            String substatType = substatArgs[0];

            int substatTier = 4;
            if (substatArgs.length > 1) {
                substatTier = Integer.parseInt(substatArgs[1]);
            }

            List<Integer> substats =
                    getArtifactAffixes(itemData, FightProperty.getPropByShortName(substatType));

            if (substats.isEmpty()) {
                throw new IllegalArgumentException();
            }

            substatTier -= 1;
            substatTier = Math.min(Math.max(0, substatTier), substats.size() - 1);
            return substats.get(substatTier);
        }
    }

    private static void parseRelicArgs(GiveItemParameters param, List<String> args)
            throws IllegalArgumentException {
        String mainPropIdString = args.remove(0);

        try {
            param.mainPropId = Integer.parseInt(mainPropIdString);
        } catch (NumberFormatException ignored) {
            param.mainPropId =
                    getArtifactMainProp(param.data, FightProperty.getPropByShortName(mainPropIdString));
        }

        param.appendPropIdList = new ArrayList<>();
        for (String prop : args) {
            String[] arr = prop.split(",");
            prop = arr[0];
            int n = 1;
            if (arr.length > 1) {
                n = Math.min(Integer.parseInt(arr[1]), 200);
            }

            int appendPropId = getAppendPropId(prop, param.data);

            for (int i = 0; i < n; i++) {
                param.appendPropIdList.add(appendPropId);
            }
        }
    }

    private static void addItemsChunked(Player player, List<GameItem> items, int packetSize) {
        int lastIdx = items.size() - 1;
        for (int i = 0; i <= lastIdx; i += packetSize) {
            player.getInventory().addItems(items.subList(i, Math.min(i + packetSize, items.size())));
        }
    }

    private static void addCosts(IntSet itemIds, Collection<ItemParamData> costs) {
        if (costs == null) return;
        for (ItemParamData cost : costs) {
            if (cost.getId() > 0) itemIds.add(cost.getId());
        }
    }

    private static Stream<AvatarPromoteData> avatarPromotions(AvatarData avatarData) {
        return IntStream.iterate(0, promoteLevel -> promoteLevel + 1)
                .mapToObj(
                        promoteLevel ->
                                GameData.getAvatarPromoteData(avatarData.getAvatarPromoteId(), promoteLevel))
                .takeWhile(Objects::nonNull);
    }

    private static Stream<WeaponPromoteData> weaponPromotions(ItemData weaponData) {
        return IntStream.iterate(0, promoteLevel -> promoteLevel + 1)
                .mapToObj(
                        promoteLevel ->
                                GameData.getWeaponPromoteData(weaponData.getWeaponPromoteId(), promoteLevel))
                .takeWhile(Objects::nonNull);
    }

    private static IntSet talentProudSkillGroupIds(AvatarData avatarData) {
        IntSet groupIds = new IntOpenHashSet();
        for (AvatarSkillDepotData depot : skillDepots(avatarData)) {
            depot.getSkillsAndEnergySkill()
                    .mapToObj(skillId -> GameData.getAvatarSkillDataMap().get(skillId))
                    .filter(Objects::nonNull)
                    .mapToInt(AvatarSkillData::getProudSkillGroupId)
                    .filter(groupId -> groupId > 0)
                    .forEach(groupIds::add);
        }
        return groupIds;
    }

    private static IntSet levelingMaterialIds() {
        IntSet itemIds = new IntOpenHashSet();
        IntSet talentGroupIds = new IntOpenHashSet();

        for (AvatarData avatarData : GameData.getAvatarDataMap().values()) {
            if (!isGiveAllAvatar(avatarData)) continue;
            avatarPromotions(avatarData)
                    .forEach(promote -> addCosts(itemIds, Arrays.asList(promote.getCostItems())));
            talentGroupIds.addAll(talentProudSkillGroupIds(avatarData));
        }

        for (ProudSkillData proudSkill : GameData.getProudSkillDataMap().values()) {
            if (talentGroupIds.contains(proudSkill.getProudSkillGroupId()))
                addCosts(itemIds, proudSkill.getCostItems());
        }

        for (ItemData itemData : GameData.getItemDataMap().values()) {
            if (isGiveAllWeapon(itemData)) {
                weaponPromotions(itemData)
                        .forEach(promote -> addCosts(itemIds, Arrays.asList(promote.getCostItems())));
                if (itemData.getAwakenMaterial() > 0) itemIds.add(itemData.getAwakenMaterial());
            } else if (isExperienceMaterial(itemData)) {
                itemIds.add(itemData.getId());
            }
        }
        return itemIds;
    }

    private static void giveAllMats(Player player, GiveItemParameters param) {
        Int2IntMap targetCounts = new Int2IntOpenHashMap();
        for (int itemId : levelingMaterialIds()) {
            ItemData data = GameData.getItemDataMap().get(itemId);
            if (data == null) continue;
            targetCounts.put(itemId, param.amountGiven ? param.amount : data.getStackLimit());
        }
        topUpItems(player, targetCounts);

        if (player.getMora() < GIVE_ALL_MORA) player.setMora(GIVE_ALL_MORA);
    }

    private static int maxWeaponLevel(ItemData weaponData) {
        return weaponPromotions(weaponData)
                .mapToInt(WeaponPromoteData::getUnlockMaxLevel)
                .max()
                .orElse(GIVE_ALL_WEAPON_LEVEL);
    }

    private static int totalWeaponExp(ItemData weaponData, int level) {
        return IntStream.range(1, level)
                .map(fromLevel -> GameData.getWeaponExpRequired(weaponData.getRankLevel(), fromLevel))
                .sum();
    }

    private static void giveAllWeapons(Player player, GiveItemParameters param) {
        int level = param.lvlGiven ? param.lvl : GIVE_ALL_WEAPON_LEVEL;
        int copies =
                param.amountGiven
                        ? Math.min(param.amount, GIVE_ALL_WEAPON_COPIES)
                        : GIVE_ALL_WEAPON_COPIES;
        int refinement = param.refinement - 1;
        Inventory inventory = player.getInventory();

        List<GameItem> itemList = new ArrayList<>();
        for (ItemData itemdata : GameData.getItemDataMap().values()) {
            if (!isGiveAllWeapon(itemdata)) continue;
            int missingCopies = copies - inventory.getItemCountById(itemdata.getId());
            if (missingCopies <= 0) continue;

            int weaponLevel = Math.min(level, maxWeaponLevel(itemdata));
            int promoteLevel = GameItem.getMinPromoteLevel(weaponLevel);
            int totalExp = totalWeaponExp(itemdata, weaponLevel);
            for (int i = 0; i < missingCopies; i++) {
                GameItem item = new GameItem(itemdata);
                item.setLevel(weaponLevel);
                item.setPromoteLevel(promoteLevel);
                item.setTotalExp(totalExp);
                item.setRefinement(refinement);
                itemList.add(item);
            }
        }

        addItemsChunked(player, itemList, ITEMS_PER_PACKET);
    }

    private static void giveAll(Player player, GiveItemParameters param) {
        giveAllAvatars(player, param);
        giveAllMats(player, param);
        giveAllWeapons(player, param);
    }

    private GiveItemParameters parseArgs(Player sender, List<String> args)
            throws IllegalArgumentException {
        GiveItemParameters param = new GiveItemParameters();

        parseIntParameters(args, param, intCommandHandlers);

        if (args.size() < 1) {
            sendUsageMessage(sender);
            throw new IllegalArgumentException();
        }
        String id = args.remove(0);
        boolean isRelic = false;

        switch (id) {
            case "all":
                param.giveAllType = GiveAllType.ALL;
                break;
            case "weapons":
                param.giveAllType = GiveAllType.WEAPONS;
                break;
            case "mats":
                param.giveAllType = GiveAllType.MATS;
                break;
            case "avatars":
                param.giveAllType = GiveAllType.AVATARS;
                break;
            default:
                try {
                    param.id = Integer.parseInt(id);
                } catch (NumberFormatException e) {
                    param.setPieces = NameIndex.resolveRelicSet(id, args);
                    if (!param.setPieces.isEmpty()) param.id = param.setPieces.get(0);
                    else param.id = NameIndex.resolveRelic(id, args);
                    if (param.id == 0) param.id = NameIndex.resolve(id, args);
                    if (param.id == 0) {
                        CommandHandler.sendTranslatedMessage(sender, "commands.generic.invalid.itemId");
                        throw e;
                    }
                }
                param.data = GameData.getItemDataMap().get(param.id);
                if ((param.id > 10_000_000) && (param.id < 12_000_000))
                    param.avatarData = GameData.getAvatarDataMap().get(param.id);
                else if ((param.id > 1000) && (param.id < 1100))
                    param.avatarData = GameData.getAvatarDataMap().get(param.id - 1000 + 10_000_000);
                isRelic = ((param.data != null) && (param.data.getItemType() == ItemType.ITEM_RELIQUARY));

                if (!isRelic
                        && !args.isEmpty()
                        && (param.amount == 1)) {
                    try {
                        param.amount = Integer.parseInt(args.remove(0));
                    } catch (NumberFormatException e) {
                        CommandHandler.sendTranslatedMessage(sender, "commands.generic.invalid.amount");
                        throw e;
                    }
                }
        }

        if (param.amount < 1) param.amount = 1;
        if (param.refinement < 1) param.refinement = 1;
        if (param.refinement > 5) param.refinement = 5;
        if (isRelic) {
            if (param.lvl < 0) param.lvl = 0;
            if (param.lvl > 20) param.lvl = 20;
            param.lvl += 1;
            if (ILLEGAL_RELICS.contains(param.id))
                CommandHandler.sendTranslatedMessage(sender, "commands.give.illegal_relic");
        } else {
            if (param.lvl < 1) param.lvl = 1;
            if (param.lvl > 90) param.lvl = 90;
        }

        if (!args.isEmpty()) {
            if (isRelic) {
                try {
                    parseRelicArgs(param, args);
                } catch (IllegalArgumentException e) {
                    CommandHandler.sendTranslatedMessage(sender, "commands.execution.argument_error");
                    CommandHandler.sendTranslatedMessage(sender, "commands.give.usage_relic");
                    throw e;
                }
            } else {
                sendUsageMessage(sender);
                throw new IllegalArgumentException();
            }
        }

        return param;
    }

    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        if (args.size() < 1) {
            sendUsageMessage(sender);
            return;
        }
        try {
            GiveItemParameters param = parseArgs(sender, args);

            switch (param.giveAllType) {
                case ALL:
                    giveAll(targetPlayer, param);
                    CommandHandler.sendTranslatedMessage(sender, "commands.give.giveall_success");
                    return;
                case WEAPONS:
                    giveAllWeapons(targetPlayer, param);
                    CommandHandler.sendTranslatedMessage(sender, "commands.give.giveall_success");
                    return;
                case MATS:
                    giveAllMats(targetPlayer, param);
                    CommandHandler.sendTranslatedMessage(sender, "commands.give.giveall_success");
                    return;
                case AVATARS:
                    giveAllAvatars(targetPlayer, param);
                    CommandHandler.sendTranslatedMessage(sender, "commands.give.giveall_success");
                    return;
                case NONE:
                    break;
            }

            if (param.avatarData != null) {
                Avatar owned = targetPlayer.getAvatars().getAvatarById(param.avatarData.getId());
                if (owned != null) {
                    updateAvatar(targetPlayer, owned, param);
                } else {
                    targetPlayer.addAvatar(makeAvatar(param));
                }
                CommandHandler.sendTranslatedMessage(
                        sender,
                        "commands.give.given_avatar",
                        NameIndex.describe(param.id),
                        param.lvl,
                        targetPlayer.getUid());
                return;
            }
            if (param.data == null) {
                CommandHandler.sendTranslatedMessage(sender, "commands.generic.invalid.itemId");
                return;
            }

            switch (param.data.getItemType()) {
                case ITEM_WEAPON:
                    targetPlayer
                            .getInventory()
                            .addItems(makeUnstackableItems(param), ActionReason.SubfieldDrop);
                    CommandHandler.sendTranslatedMessage(
                            sender,
                            "commands.give.given_with_level_and_refinement",
                            NameIndex.describe(param.id),
                            param.lvl,
                            param.refinement,
                            param.amount,
                            targetPlayer.getUid());
                    return;
                case ITEM_RELIQUARY:
                    if (!param.setPieces.isEmpty()) {
                        giveWholeSet(sender, targetPlayer, param);
                        return;
                    }

                    targetPlayer.getInventory().addItems(makeArtifacts(param), ActionReason.SubfieldDrop);
                    CommandHandler.sendTranslatedMessage(
                            sender,
                            "commands.give.given_level",
                            NameIndex.describe(param.id),
                            param.lvl,
                            param.amount,
                            targetPlayer.getUid());
                    return;
                default:
                    targetPlayer
                            .getInventory()
                            .addItem(new GameItem(param.data, param.amount), ActionReason.SubfieldDrop);
                    CommandHandler.sendTranslatedMessage(
                            sender,
                            "commands.give.given",
                            param.amount,
                            NameIndex.describe(param.id),
                            targetPlayer.getUid());
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private enum GiveAllType {
        NONE,
        ALL,
        WEAPONS,
        MATS,
        AVATARS
    }

    private static void giveWholeSet(Player sender, Player targetPlayer, GiveItemParameters param) {
        param.mainPropId = -1;
        param.appendPropIdList = null;

        var given = 0;
        for (var piece : param.setPieces) {
            var data = GameData.getItemDataMap().get(piece.intValue());
            if (data == null) continue;

            param.data = data;
            param.id = piece;
            targetPlayer.getInventory().addItems(makeArtifacts(param), ActionReason.SubfieldDrop);
            given++;
        }

        CommandHandler.sendTranslatedMessage(
                sender, "commands.give.given", given, "artifacts of the set", targetPlayer.getUid());
    }

    private static class GiveItemParameters {
        public int id;
        public int lvl = 0;
        public int amount = 1;
        @Setter public int refinement = 1;
        public int constellation = -1;
        public int skillLevel = 1;
        public int mainPropId = -1;
        public List<Integer> appendPropIdList;

        public List<Integer> setPieces = List.of();
        public ItemData data;
        public AvatarData avatarData;
        public GiveAllType giveAllType = GiveAllType.NONE;

        public boolean lvlGiven, amountGiven, constellationGiven, skillLevelGiven;

        public void setAmount(int amount) {
            this.amount = amount;
            this.amountGiven = true;
        }

        public void setLvl(int lvl) {
            this.lvl = lvl;
            this.lvlGiven = true;
        }

        public void setConstellation(int constellation) {
            this.constellation = constellation;
            this.constellationGiven = true;
        }

        public void setSkillLevel(int skillLevel) {
            this.skillLevel = skillLevel;
            this.skillLevelGiven = true;
        }
    }
}
