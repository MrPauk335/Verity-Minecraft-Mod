package net.verity.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.tags.FluidTags;

import java.util.HashMap;
import java.util.Map;

public class VerityChunkChecker {

    public static record ChunkAnalysis(
            int chunkX,
            int chunkZ,
            String primaryBiome,
            String standingBlock,
            String detectedStructure,
            boolean isSubmerged,
            int diamonds,
            int iron,
            int gold,
            int coal,
            int chests,
            int beds,
            int craftingTables,
            int furnaces,
            int crops,
            int buildingBlocks,
            int lights,
            int waterBlocks,
            int lavaBlocks,
            boolean isUnderground,
            boolean hasPortal,
            int surfaceY,
            boolean hasHouseOrBase,
            // ── MOB CHUNK CHECKER DATA ──
            int totalMobs,
            int monsterCount,
            int passiveCount,
            String nearbyMobsSummary,
            String nearestMobName,
            int nearestMobDist,
            boolean hasThreatNearby
    ) {
        public String toSummaryString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Биом ").append(primaryBiome).append(". ");
            if (detectedStructure != null && !detectedStructure.isEmpty()) {
                sb.append("ОБНАРУЖЕНА СТРУКТУРА: ").append(detectedStructure).append("! ");
            }
            if (isSubmerged) {
                sb.append("Игрок под водой. ");
            }
            sb.append("Блок под ногами: ").append(standingBlock).append(". ");
            if (isUnderground) {
                sb.append("Под землёй / в пещере (Y=").append(surfaceY).append("). ");
                if (diamonds > 0) sb.append("Алмазов: ").append(diamonds).append(". ");
                if (iron > 0) sb.append("Железа: ").append(iron).append(". ");
            } else {
                sb.append("На поверхности (Уровень земли Y=").append(surfaceY).append("). ");
                if (hasHouseOrBase) {
                    sb.append("ОБНАРУЖЕН ДОМ/БАЗА ИГРОКА (постройки, крыша, блоки строительства: ").append(buildingBlocks).append(")! ");
                }
                if (furnaces > 0) sb.append("Печей/Коптилен: ").append(furnaces).append(". ");
                if (crops > 0) sb.append("Ферма/Грядки: ").append(crops).append(" блоков. ");
                if (lights > 0) sb.append("Освещение/Факелы: ").append(lights).append(". ");
            }

            // ── ИНФОРМАЦИЯ ЧАНК-ЧЕКЕРА МОБОВ ──
            if (totalMobs > 0) {
                sb.append("СУЩЕСТВА В ЧАНКЕ (MobChecker): ").append(nearbyMobsSummary).append(". ");
                if (nearestMobName != null) {
                    sb.append("Ближайший моб: ").append(nearestMobName).append(" в ").append(nearestMobDist).append("б. ");
                }
                if (hasThreatNearby) {
                    sb.append("ВНИМАНИЕ: Опасные враждебные монстры рядом с игроком! ");
                }
            } else {
                sb.append("Живых существ и мобов в чанке нет. ");
            }

            if (chests > 0) sb.append("Сундуков/Хранилищ: ").append(chests).append(". ");
            if (beds > 0) sb.append("Кроватей: ").append(beds).append(". ");
            if (craftingTables > 0) sb.append("Верстаков: ").append(craftingTables).append(". ");
            if (hasPortal) sb.append("Рядом портал в Незер! ");
            return sb.toString();
        }
    }

    public static ChunkAnalysis analyzeChunk(Player player) {
        if (!(player.level() instanceof ServerLevel sl)) {
            return new ChunkAnalysis(0, 0, "неизвестно", "воздух", null, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, 64, false, 0, 0, 0, "", null, 0, false);
        }

        BlockPos ppos = player.blockPosition();
        ChunkPos cpos = new ChunkPos(ppos);

        String biome = sl.getBiome(ppos).unwrapKey()
                .map(k -> k.location().getPath().replace("_", " "))
                .orElse("равнина");

        String structureName = findStructureAt(sl, ppos);

        BlockPos feetPos = ppos.below();
        BlockState feetState = sl.getBlockState(feetPos);
        if (feetState.isAir()) {
            feetPos = ppos.below(2);
            feetState = sl.getBlockState(feetPos);
        }
        String standingBlock = feetState.getBlock().getName().getString();
        boolean isSubmerged = player.isEyeInFluid(FluidTags.WATER) || sl.getFluidState(ppos).is(FluidTags.WATER);

        if (isSubmerged && (standingBlock.toLowerCase().contains("доски") || standingBlock.toLowerCase().contains("planks") || standingBlock.toLowerCase().contains("дерево") || standingBlock.toLowerCase().contains("обломки"))) {
            if (structureName == null) {
                structureName = "затонувший корабль";
            }
        }

        int surfaceY = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, ppos.getX(), ppos.getZ());
        boolean isUnderground = !sl.canSeeSky(ppos) && ppos.getY() < (surfaceY - 5);

        // ── 1. MOB CHUNK CHECKER SCANNER ──
        var livingList = sl.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(32.0D),
                e -> e != player && !e.isSpectator() && e.isAlive()
        );

        int totalMobs = livingList.size();
        int monsterCount = 0;
        int passiveCount = 0;
        boolean hasThreatNearby = false;
        String nearestMobName = null;
        int nearestMobDist = 999;

        Map<String, Integer> mobCounts = new HashMap<>();

        for (LivingEntity le : livingList) {
            String name = le.getName().getString();
            mobCounts.put(name, mobCounts.getOrDefault(name, 0) + 1);

            int dist = (int) Math.sqrt(player.distanceToSqr(le));
            if (dist < nearestMobDist) {
                nearestMobDist = dist;
                nearestMobName = name;
            }

            if (le instanceof Enemy) {
                monsterCount++;
                if (dist <= 16) {
                    hasThreatNearby = true;
                }
            } else {
                passiveCount++;
            }
        }

        StringBuilder mobSummarySb = new StringBuilder();
        int added = 0;
        for (var entry : mobCounts.entrySet()) {
            if (added > 0) mobSummarySb.append(", ");
            mobSummarySb.append(entry.getKey()).append(" x").append(entry.getValue());
            added++;
            if (added >= 5) break; // топ 5 видов мобов в чанке
        }
        String nearbyMobsSummary = mobSummarySb.toString();

        // ── 2. BLOCK CHUNK CHECKER SCANNER ──
        int diamonds = 0, iron = 0, gold = 0, coal = 0;
        int chests = 0, beds = 0, crafting = 0, furnaces = 0, crops = 0, buildingBlocks = 0, lights = 0;
        int water = 0, lava = 0;
        boolean hasPortal = false;

        int minX = cpos.getMinBlockX();
        int minZ = cpos.getMinBlockZ();

        int startY, endY;
        if (isUnderground) {
            startY = Math.max(sl.getMinBuildHeight(), ppos.getY() - 20);
            endY = Math.min(sl.getMaxBuildHeight(), ppos.getY() + 20);
        } else {
            startY = Math.max(sl.getMinBuildHeight(), surfaceY - 3);
            endY = Math.min(sl.getMaxBuildHeight(), surfaceY + 40);
        }

        for (int x = minX; x <= minX + 15; x += 2) {
            for (int z = minZ; z <= minZ + 15; z += 2) {
                for (int y = startY; y <= endY; y += 2) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = sl.getBlockState(pos);
                    if (state.isAir()) continue;

                    var block = state.getBlock();
                    String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();

                    if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) diamonds++;
                    else if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) iron++;
                    else if (state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE)) gold++;
                    else if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) coal++;

                    else if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL)) chests++;
                    else if (block instanceof net.minecraft.world.level.block.BedBlock) beds++;
                    else if (state.is(Blocks.CRAFTING_TABLE)) crafting++;
                    else if (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)) furnaces++;

                    else if (state.is(Blocks.FARMLAND) || path.contains("wheat") || path.contains("carrots") || path.contains("potatoes") || path.contains("beetroot") || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) crops++;

                    else if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN) || state.is(Blocks.GLOWSTONE)) lights++;

                    else if (path.contains("planks") || path.contains("cobblestone") || path.contains("bricks") || path.contains("glass") || path.contains("door") || path.contains("stairs") || path.contains("slab")) {
                        buildingBlocks++;
                    }

                    else if (state.is(Blocks.WATER)) water++;
                    else if (state.is(Blocks.LAVA)) lava++;
                    else if (state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.OBSIDIAN)) hasPortal = true;
                }
            }
        }

        boolean hasHouseOrBase = buildingBlocks > 10 || (furnaces > 0 && beds > 0) || (crafting > 0 && chests > 0 && buildingBlocks > 5);

        return new ChunkAnalysis(
                cpos.x, cpos.z, biome, standingBlock, structureName, isSubmerged,
                diamonds, iron, gold, coal,
                chests, beds, crafting, furnaces, crops, buildingBlocks, lights, water, lava,
                isUnderground, hasPortal, surfaceY, hasHouseOrBase,
                totalMobs, monsterCount, passiveCount, nearbyMobsSummary, nearestMobName, (nearestMobDist == 999 ? 0 : nearestMobDist), hasThreatNearby
        );
    }

    private static String findStructureAt(ServerLevel sl, BlockPos pos) {
        try {
            var structureManager = sl.structureManager();
            for (int dx = -16; dx <= 16; dx += 8) {
                for (int dz = -16; dz <= 16; dz += 8) {
                    BlockPos checkPos = pos.offset(dx, 0, dz);
                    var map = structureManager.getAllStructuresAt(checkPos);
                    for (var entry : map.entrySet()) {
                        Structure struct = entry.getKey();
                        String path = sl.registryAccess().registryOrThrow(Registries.STRUCTURE)
                                .getKey(struct).getPath();
                        if (path.contains("shipwreck")) return "затонувший корабль";
                        if (path.contains("village")) return "деревню";
                        if (path.contains("mineshaft")) return "заброшенную шахту";
                        if (path.contains("ruined_portal")) return "разрушенный портал";
                        if (path.contains("ocean_ruin")) return "подводные руины";
                        if (path.contains("monument")) return "подводный монумент";
                        if (path.contains("ancient_city")) return "древний город";
                        if (path.contains("mansion")) return "лесной особняк";
                        if (path.contains("fortress")) return "адскую крепость";
                        if (path.contains("stronghold")) return "крепость Края";
                        if (path.contains("outpost")) return "аванпост разбойников";
                        return path.replace("_", " ");
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }
}
