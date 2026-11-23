package org.hp.carryonbugfix.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.hp.carryonbugfix.BlacklistExceptionMatcher;
import org.hp.carryonbugfix.CarryOnBugFix;
import org.hp.carryonbugfix.CarryOnBugFixConfig;
import org.hp.carryonbugfix.PortableJukeboxHandler;
import org.hp.carryonbugfix.VehicleBoardingHandler;
import tschipp.carryon.CarryOnCommon;
import tschipp.carryon.Constants;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnDataManager;
import tschipp.carryon.common.carry.PickupHandler;
import tschipp.carryon.common.carry.PlacementHandler;
import tschipp.carryon.common.config.ListHandler;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 26.x NeoForge 端到端 GameTest，覆盖生物塞船/矿车、便携唱片机无缝续播（含 setRemoved 停歌事件拦截）与黑名单白名单豁免共存。
 */
public final class CarryOnBugFixGameTests {
    private static final Identifier EMPTY_STRUCTURE = Identifier.fromNamespaceAndPath(CarryOnBugFix.MOD_ID, "empty");
    private static final Identifier ENV_ID = Identifier.fromNamespaceAndPath(CarryOnBugFix.MOD_ID, "default");

    private static final Identifier FN_VEHICLE = Identifier.fromNamespaceAndPath(CarryOnBugFix.MOD_ID, "vehicle_boarding_boat_and_minecart");
    private static final Identifier FN_JUKEBOX = Identifier.fromNamespaceAndPath(CarryOnBugFix.MOD_ID, "portable_jukebox_pickup_tick_and_resume");
    private static final Identifier FN_EXCEPTIONS = Identifier.fromNamespaceAndPath(CarryOnBugFix.MOD_ID, "blacklist_with_whitelist_exceptions");

    private CarryOnBugFixGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(Registries.TEST_FUNCTION)) {
                event.register(Registries.TEST_FUNCTION, helper -> {
                    helper.register(FN_VEHICLE, CarryOnBugFixGameTests::vehicleBoardingBoatAndMinecart);
                    helper.register(FN_JUKEBOX, CarryOnBugFixGameTests::portableJukeboxPickupTickAndResume);
                    helper.register(FN_EXCEPTIONS, CarryOnBugFixGameTests::blacklistWithWhitelistExceptions);
                });
            }
        });

        modEventBus.addListener((RegisterGameTestsEvent event) -> {
            Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(ENV_ID, new TestEnvironmentDefinition.AllOf(List.of()));
            registerInstance(event, env, FN_VEHICLE, 40);
            registerInstance(event, env, FN_JUKEBOX, 40);
            registerInstance(event, env, FN_EXCEPTIONS, 40);
        });
    }

    private static void registerInstance(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> env, Identifier id, int maxTicks) {
        ResourceKey<Consumer<GameTestHelper>> fnKey = ResourceKey.create(Registries.TEST_FUNCTION, id);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(env, EMPTY_STRUCTURE, maxTicks, 0, true);
        event.registerTest(id, new FunctionGameTestInstance(fnKey, data));
    }

    public static void vehicleBoardingBoatAndMinecart(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "carryon_test_player"));
        player.setGameMode(GameType.SURVIVAL);
        Vec3 basePos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        player.setPos(basePos.x, basePos.y, basePos.z);
        player.setShiftKeyDown(true);

        // 1. 测试抱着 2 个生物依次塞进同一艘橡木船，第 3 个被满座拒绝
        Boat boat = EntityType.OAK_BOAT.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        boat.setPos(basePos.x + 1.0D, basePos.y, basePos.z);
        helper.getLevel().addFreshEntity(boat);

        Pig pig1 = EntityType.PIG.create(helper.getLevel(), EntitySpawnReason.COMMAND);
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
        Pig pig2 = EntityType.PIG.create(helper.getLevel(), EntitySpawnReason.COMMAND);
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
        Pig pig3 = EntityType.PIG.create(helper.getLevel(), EntitySpawnReason.COMMAND);
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
        Minecart minecart = EntityType.MINECART.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        minecart.setPos(basePos.x - 1.0D, basePos.y, basePos.z);
        helper.getLevel().addFreshEntity(minecart);
        player.tickCount += 5;

        boolean handledCart = VehicleBoardingHandler.tryBoardVehicle(player, minecart);
        if (!handledCart || minecart.getPassengers().size() != 1 || CarryOnDataManager.getCarryData(player).isCarrying()) {
            helper.fail("Failed to board mob into empty minecart");
            return;
        }

        helper.succeed();
    }

    public static void portableJukeboxPickupTickAndResume(GameTestHelper helper) {
        BlockPos originRel = new BlockPos(0, 1, 1);
        BlockPos targetRel = new BlockPos(2, 1, 1);
        helper.setBlock(originRel, Blocks.JUKEBOX);
        helper.setBlock(new BlockPos(2, 0, 1), Blocks.STONE);
        BlockPos originAbs = helper.absolutePos(originRel);
        BlockPos targetAbs = helper.absolutePos(targetRel);

        JukeboxBlockEntity jukebox = helper.getBlockEntity(originRel, JukeboxBlockEntity.class);
        jukebox.setTheItem(new ItemStack(Items.MUSIC_DISC_CAT));
        if (!jukebox.getSongPlayer().isPlaying()) {
            helper.fail("Jukebox did not start playing disc");
            return;
        }

        for (int i = 0; i < 10; i++) {
            JukeboxBlockEntity.tick(helper.getLevel(), originAbs, helper.getBlockState(originRel), jukebox);
        }
        long elapsedBeforePickup = jukebox.getSongPlayer().getTicksSinceSongStarted();

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
        Optional<CompoundTag> infoOpt = carried.getNbt().getCompound(PortableJukeboxHandler.TAG_ROOT);
        if (infoOpt.isEmpty()) {
            helper.fail("Missing carryOnBugFixJukebox metadata after pickup");
            return;
        }
        CompoundTag info = infoOpt.get();
        if (!info.getBooleanOr(PortableJukeboxHandler.KEY_PLAYING, false) || !info.getStringOr(PortableJukeboxHandler.KEY_SONG_ID, "").equals("minecraft:cat")) {
            helper.fail("Invalid jukebox metadata: " + info);
            return;
        }

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
            if (!placedJukebox.getSongPlayer().isPlaying()) {
                helper.fail("Placed jukebox should still be playing");
                return;
            }
            long resumedTicks = placedJukebox.getSongPlayer().getTicksSinceSongStarted();
            if (resumedTicks < elapsedBeforePickup + 5) {
                helper.fail("Placed jukebox ticks did not advance continuously: before=" + elapsedBeforePickup + " resumed=" + resumedTicks);
                return;
            }
            helper.succeed();
        });
    }

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

            Villager villager = EntityType.VILLAGER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
            Pig pig = EntityType.PIG.create(helper.getLevel(), EntitySpawnReason.COMMAND);
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
