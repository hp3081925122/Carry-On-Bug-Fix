package org.hp.carryonbugfix.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.hp.carryonbugfix.BlacklistExceptionMatcher;
import org.hp.carryonbugfix.CarryOnBugFix;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.hp.carryonbugfix.PortableJukeboxHandler;
import org.hp.carryonbugfix.VehicleBoardingHandler;
import org.hp.carryonbugfix.mixin.JukeboxBlockEntityAccessor;
import tschipp.carryon.CarryOnCommon;
import tschipp.carryon.Constants;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnDataManager;
import tschipp.carryon.common.carry.PickupHandler;
import tschipp.carryon.common.carry.PlacementHandler;
import tschipp.carryon.common.config.ListHandler;

import java.util.List;
import java.util.UUID;

/**
 * 1.20.1 Forge 端到端 GameTest，覆盖生物塞船/矿车、便携唱片机无缝续播、黑名单与白名单豁免共存。
 */
@GameTestHolder(CarryOnBugFix.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CarryOnBugFixGameTests {
    private CarryOnBugFixGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void vehicleBoardingBoatAndMinecart(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "carryon_test_player"));
        player.setGameMode(GameType.SURVIVAL);
        Vec3 basePos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 2)));
        player.setPos(basePos.x, basePos.y, basePos.z);
        player.setShiftKeyDown(true);

        // 1. 测试抱着 2 个生物依次塞进同一艘普通船，第 3 个被满座拒绝
        Boat boat = new Boat(helper.getLevel(), basePos.x + 1.0D, basePos.y, basePos.z);
        helper.getLevel().addFreshEntity(boat);

        Pig pig1 = EntityType.PIG.create(helper.getLevel());
        pig1.setPos(basePos.x, basePos.y, basePos.z);
        CarryOnData carry1 = CarryOnDataManager.getCarryData(player);
        carry1.setEntity(pig1);
        CarryOnDataManager.setCarryData(player, carry1);
        player.tickCount += 5;

        boolean handled1 = VehicleBoardingHandler.tryBoardVehicle(player, boat);
        if (!handled1 || boat.getPassengers().size() != 1 || CarryOnDataManager.getCarryData(player).isCarrying()) {
            helper.fail("First mob failed to board empty boat: handled=" + handled1 + " passengers=" + boat.getPassengers().size());
            return;
        }

        // 第 2 只猪通过点击已在船内的第 1 只猪也能定位根载具坐进第 2 个座位
        Pig pig2 = EntityType.PIG.create(helper.getLevel());
        pig2.setPos(basePos.x, basePos.y, basePos.z);
        CarryOnData carry2 = CarryOnDataManager.getCarryData(player);
        carry2.setEntity(pig2);
        CarryOnDataManager.setCarryData(player, carry2);
        player.tickCount += 5;

        boolean handled2 = VehicleBoardingHandler.tryBoardVehicle(player, boat.getFirstPassenger());
        if (!handled2 || boat.getPassengers().size() != 2 || CarryOnDataManager.getCarryData(player).isCarrying()) {
            helper.fail("Second mob failed to board second seat of boat: handled=" + handled2 + " passengers=" + boat.getPassengers().size());
            return;
        }

        // 第 3 只猪面对满座船应被拒绝且保留在手中不丢失
        Pig pig3 = EntityType.PIG.create(helper.getLevel());
        CarryOnData carry3 = CarryOnDataManager.getCarryData(player);
        carry3.setEntity(pig3);
        CarryOnDataManager.setCarryData(player, carry3);
        player.tickCount += 5;

        boolean handled3 = VehicleBoardingHandler.tryBoardVehicle(player, boat);
        if (!handled3 || boat.getPassengers().size() != 2 || !CarryOnDataManager.getCarryData(player).isCarrying(CarryOnData.CarryType.ENTITY)) {
            helper.fail("Full boat should reject third mob and keep it carried");
            return;
        }

        // 2. 测试把手上第 3 只生物放进空矿车
        Minecart minecart = new Minecart(helper.getLevel(), basePos.x - 1.0D, basePos.y, basePos.z);
        helper.getLevel().addFreshEntity(minecart);
        player.tickCount += 5;

        boolean handledCart = VehicleBoardingHandler.tryBoardVehicle(player, minecart);
        if (!handledCart || minecart.getPassengers().size() != 1 || CarryOnDataManager.getCarryData(player).isCarrying()) {
            helper.fail("Failed to board mob into empty minecart");
            return;
        }

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void portableJukeboxPickupTickAndResume(GameTestHelper helper) {
        BlockPos originRel = new BlockPos(0, 1, 1);
        BlockPos targetRel = new BlockPos(2, 1, 1);
        helper.setBlock(originRel, Blocks.JUKEBOX);
        helper.setBlock(new BlockPos(2, 0, 1), Blocks.STONE);
        BlockPos originAbs = helper.absolutePos(originRel);
        BlockPos targetAbs = helper.absolutePos(targetRel);

        BlockEntity be = helper.getBlockEntity(originRel);
        if (!(be instanceof JukeboxBlockEntity jukebox)) {
            helper.fail("Expected JukeboxBlockEntity");
            return;
        }
        jukebox.setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
        if (!jukebox.isRecordPlaying()) {
            helper.fail("Jukebox did not start playing disc");
            return;
        }

        // 推进 10 tick 模拟已播放一段进度
        for (int i = 0; i < 10; i++) {
            JukeboxBlockEntity.playRecordTick(helper.getLevel(), originAbs, helper.getBlockState(originRel), jukebox);
        }
        JukeboxBlockEntityAccessor beforeAccessor = (JukeboxBlockEntityAccessor) jukebox;
        long elapsedBeforePickup = beforeAccessor.carryOnBugFix$getTickCount() - beforeAccessor.carryOnBugFix$getRecordStartedTick();

        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "carryon_jukebox_player"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(originAbs.getX() + 0.5D, originAbs.getY(), originAbs.getZ() + 1.5D);
        CarryOnData carry = CarryOnDataManager.getCarryData(player);
        carry.setKeyPressed(true);
        CarryOnDataManager.setCarryData(player, carry);
        player.tickCount += 5;

        boolean picked = PickupHandler.tryPickUpBlock(player, originAbs, helper.getLevel(), null);
        if (!picked) {
            helper.fail("Failed to pick up playing jukebox");
            return;
        }

        CarryOnData carried = CarryOnDataManager.getCarryData(player);
        CompoundTag nbt = carried.getNbt();
        if (!nbt.contains(PortableJukeboxHandler.TAG_ROOT, Tag.TAG_COMPOUND)) {
            helper.fail("Missing carryOnBugFixJukebox metadata after pickup");
            return;
        }
        CompoundTag info = nbt.getCompound(PortableJukeboxHandler.TAG_ROOT);
        if (!info.getBoolean(PortableJukeboxHandler.KEY_PLAYING) || !info.getString(PortableJukeboxHandler.KEY_SONG_ID).equals("minecraft:music_disc_cat")) {
            helper.fail("Invalid jukebox metadata: " + info);
            return;
        }

        // 推进若干 tick 验证搬运期间计时正常推进并在放下后无缝续播
        helper.runAfterDelay(5, () -> {
            player.tickCount += 5;
            CarryOnCommon.onCarryTick(player);

            boolean placed = PlacementHandler.tryPlaceBlock(player, targetAbs.below(), Direction.UP, null);
            if (!placed) {
                helper.fail("Failed to place carried jukebox at " + targetAbs);
                return;
            }
            BlockEntity placedBe = helper.getLevel().getBlockEntity(targetAbs);
            if (!(placedBe instanceof JukeboxBlockEntity placedJukebox)) {
                helper.fail("Expected placed JukeboxBlockEntity");
                return;
            }
            if (!placedJukebox.isRecordPlaying()) {
                helper.fail("Placed jukebox should still be playing");
                return;
            }
            JukeboxBlockEntityAccessor afterAccessor = (JukeboxBlockEntityAccessor) placedJukebox;
            long resumedTicks = afterAccessor.carryOnBugFix$getTickCount() - afterAccessor.carryOnBugFix$getRecordStartedTick();
            if (resumedTicks < elapsedBeforePickup + 5) {
                helper.fail("Placed jukebox ticks did not advance continuously: before=" + elapsedBeforePickup + " resumed=" + resumedTicks);
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void blacklistWithWhitelistExceptions(GameTestHelper helper) {
        String[] oldForbiddenTiles = Constants.COMMON_CONFIG.blacklist.forbiddenTiles;
        String[] oldAllowedBlocks = Constants.COMMON_CONFIG.whitelist.allowedBlocks;
        boolean oldUseWhitelistBlocks = Constants.COMMON_CONFIG.settings.useWhitelistBlocks;
        String[] oldForbiddenEntities = Constants.COMMON_CONFIG.blacklist.forbiddenEntities;
        boolean oldUseWhitelistEntities = Constants.COMMON_CONFIG.settings.useWhitelistEntities;
        List<? extends String> oldBlockExceptions = List.copyOf(CarryOnBugFixConfig.BLOCK_BLACKLIST_EXCEPTIONS.get());
        List<? extends String> oldEntityExceptions = List.copyOf(CarryOnBugFixConfig.ENTITY_BLACKLIST_EXCEPTIONS.get());

        try {
            Constants.COMMON_CONFIG.settings.useWhitelistBlocks = false;
            Constants.COMMON_CONFIG.settings.useWhitelistEntities = false;
            Constants.COMMON_CONFIG.blacklist.forbiddenTiles = new String[]{"minecraft:*"};
            Constants.COMMON_CONFIG.whitelist.allowedBlocks = new String[]{"minecraft:chest"};
            Constants.COMMON_CONFIG.blacklist.forbiddenEntities = new String[]{"minecraft:*"};
            CarryOnBugFixConfig.BLOCK_BLACKLIST_EXCEPTIONS.set(List.of("minecraft:barrel"));
            CarryOnBugFixConfig.ENTITY_BLACKLIST_EXCEPTIONS.set(List.of("minecraft:villager"));
            ListHandler.initConfigLists();
            BlacklistExceptionMatcher.rebuild();

            if (!ListHandler.isPermitted(Blocks.CHEST)) {
                helper.fail("Carry On whitelist entry minecraft:chest should bypass minecraft:* blacklist");
                return;
            }
            if (!ListHandler.isPermitted(Blocks.BARREL)) {
                helper.fail("Config exception minecraft:barrel should bypass minecraft:* blacklist");
                return;
            }
            if (ListHandler.isPermitted(Blocks.FURNACE)) {
                helper.fail("Non-excepted block minecraft:furnace should remain blacklisted");
                return;
            }

            Villager villager = EntityType.VILLAGER.create(helper.getLevel());
            Pig pig = EntityType.PIG.create(helper.getLevel());
            if (!ListHandler.isPermitted(villager)) {
                helper.fail("Config exception minecraft:villager should bypass minecraft:* entity blacklist");
                return;
            }
            if (ListHandler.isPermitted(pig)) {
                helper.fail("Non-excepted entity minecraft:pig should remain blacklisted");
                return;
            }
            helper.succeed();
        } finally {
            Constants.COMMON_CONFIG.settings.useWhitelistBlocks = oldUseWhitelistBlocks;
            Constants.COMMON_CONFIG.settings.useWhitelistEntities = oldUseWhitelistEntities;
            Constants.COMMON_CONFIG.blacklist.forbiddenTiles = oldForbiddenTiles;
            Constants.COMMON_CONFIG.whitelist.allowedBlocks = oldAllowedBlocks;
            Constants.COMMON_CONFIG.blacklist.forbiddenEntities = oldForbiddenEntities;
            CarryOnBugFixConfig.BLOCK_BLACKLIST_EXCEPTIONS.set(oldBlockExceptions);
            CarryOnBugFixConfig.ENTITY_BLACKLIST_EXCEPTIONS.set(oldEntityExceptions);
            ListHandler.initConfigLists();
            BlacklistExceptionMatcher.rebuild();
        }
    }
}
