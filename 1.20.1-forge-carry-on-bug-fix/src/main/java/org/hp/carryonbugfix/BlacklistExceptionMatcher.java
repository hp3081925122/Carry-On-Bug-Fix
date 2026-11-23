package org.hp.carryonbugfix;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import tschipp.carryon.utils.StringHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 1.20.1 解析并缓存本模组独立的黑名单豁免表，支持精确 ID、通配符与标签匹配。
 */
public final class BlacklistExceptionMatcher {
    private static final Object LOCK = new Object();
    private static volatile boolean dirty = true;

    private static volatile Set<String> blockIds = Set.of();
    private static volatile List<TagKey<Block>> blockTags = List.of();
    private static volatile Set<String> entityIds = Set.of();
    private static volatile List<TagKey<EntityType<?>>> entityTags = List.of();
    private static volatile Set<String> stackingIds = Set.of();
    private static volatile List<TagKey<EntityType<?>>> stackingTags = List.of();

    private BlacklistExceptionMatcher() {
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void rebuild() {
        if (!CarryOnBugFixConfig.SPEC.isLoaded()) {
            dirty = true;
            return;
        }
        synchronized (LOCK) {
            Map<ResourceLocation, TagKey<Block>> allBlockTags = BuiltInRegistries.BLOCK.getTagNames()
                    .collect(Collectors.toMap(TagKey::location, tag -> tag, (a, b) -> a));
            Map<ResourceLocation, TagKey<EntityType<?>>> allEntityTags = BuiltInRegistries.ENTITY_TYPE.getTagNames()
                    .collect(Collectors.toMap(TagKey::location, tag -> tag, (a, b) -> a));

            Set<String> newBlockIds = new HashSet<>();
            List<TagKey<Block>> newBlockTags = new ArrayList<>();
            parseEntries(CarryOnBugFixConfig.BLOCK_BLACKLIST_EXCEPTIONS.get(), newBlockIds, newBlockTags, BuiltInRegistries.BLOCK, allBlockTags);

            Set<String> newEntityIds = new HashSet<>();
            List<TagKey<EntityType<?>>> newEntityTags = new ArrayList<>();
            parseEntries(CarryOnBugFixConfig.ENTITY_BLACKLIST_EXCEPTIONS.get(), newEntityIds, newEntityTags, BuiltInRegistries.ENTITY_TYPE, allEntityTags);

            Set<String> newStackingIds = new HashSet<>();
            List<TagKey<EntityType<?>>> newStackingTags = new ArrayList<>();
            parseEntries(CarryOnBugFixConfig.STACKING_BLACKLIST_EXCEPTIONS.get(), newStackingIds, newStackingTags, BuiltInRegistries.ENTITY_TYPE, allEntityTags);

            blockIds = Set.copyOf(newBlockIds);
            blockTags = List.copyOf(newBlockTags);
            entityIds = Set.copyOf(newEntityIds);
            entityTags = List.copyOf(newEntityTags);
            stackingIds = Set.copyOf(newStackingIds);
            stackingTags = List.copyOf(newStackingTags);
            dirty = false;
        }
    }

    public static boolean isBlockException(Block block) {
        ensureLoaded();
        String key = BuiltInRegistries.BLOCK.getKey(block).toString();
        if (blockIds.contains(key)) {
            return true;
        }
        for (TagKey<Block> tag : blockTags) {
            if (block.defaultBlockState().is(tag)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEntityException(Entity entity) {
        ensureLoaded();
        return matchesEntity(entity, entityIds, entityTags);
    }

    public static boolean isStackingException(Entity entity) {
        ensureLoaded();
        return matchesEntity(entity, stackingIds, stackingTags);
    }

    private static boolean matchesEntity(Entity entity, Set<String> ids, List<TagKey<EntityType<?>>> tags) {
        String key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (ids.contains(key)) {
            return true;
        }
        for (TagKey<EntityType<?>> tag : tags) {
            if (entity.getType().is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static void ensureLoaded() {
        if (dirty) {
            rebuild();
        }
    }

    private static <T> void parseEntries(
            List<? extends String> rawEntries,
            Set<String> outIds,
            List<TagKey<T>> outTags,
            Registry<T> registry,
            Map<ResourceLocation, TagKey<T>> tagMap
    ) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return;
        }
        ResourceLocation[] keys = null;
        for (String raw : rawEntries) {
            if (raw == null) {
                continue;
            }
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.startsWith("#")) {
                ResourceLocation tagLoc = ResourceLocation.tryParse(entry.substring(1));
                if (tagLoc != null) {
                    TagKey<T> resolved = tagMap.get(tagLoc);
                    if (resolved != null) {
                        outTags.add(resolved);
                    } else {
                        outTags.add(TagKey.create(registry.key(), tagLoc));
                    }
                }
            } else if (entry.contains("*")) {
                if (keys == null) {
                    keys = registry.keySet().toArray(new ResourceLocation[0]);
                }
                String[] parts = entry.replace("*", ",").split(",");
                for (ResourceLocation key : keys) {
                    if (StringHelper.matchesWildcards(key.toString(), parts)) {
                        outIds.add(key.toString());
                    }
                }
            } else {
                outIds.add(entry);
            }
        }
    }
}
