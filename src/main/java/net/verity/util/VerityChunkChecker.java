package net.verity.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.tags.FluidTags;

public class VerityChunkChecker {

    public static record ChunkAnalysis(
            int chunkX,
            int chunkZ,
            String primaryBiome,
            String standingBlock,
            String detectedStructure,
            boolean isSubmerged,
            int diamondOres,
            int ironOres,
            int goldOres,
            int coalOres,
            int chests,
            int beds,
            int craftingTables,
            int waterBlocks,
            int lavaBlocks,
            boolean isUnderground,
            boolean hasPortal,
            int surfaceY
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
                sb.append("Под землёй / в пещере. ");
            } else {
                sb.append("Поверхность (Y=").append(surfaceY).append("). ");
            }
            if (diamondOres > 0) sb.append("Алмазов: ").append(diamondOres).append(". ");
            if (ironOres > 0) sb.append("Железа: ").append(ironOres).append(". ");
            if (chests > 0) sb.append("Сундуков: ").append(chests).append(". ");
            if (hasPortal) sb.append("Рядом портал в Незер! ");
            return sb.toString();
        }
    }

    public static ChunkAnalysis analyzeChunk(Player player) {
        if (!(player.level() instanceof ServerLevel sl)) {
            return new ChunkAnalysis(0, 0, "неизвестно", "воздух", null, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, 64);
        }

        BlockPos ppos = player.blockPosition();
        ChunkPos cpos = new ChunkPos(ppos);

        String biome = sl.getBiome(ppos).unwrapKey()
                .map(k -> k.location().getPath().replace("_", " "))
                .orElse("равнина");

        // ── 1. ОПРЕДЕЛЕНИЕ СТРУКТУРЫ (Затонувший корабль, деревня, и т.д.) ──
        String structureName = findStructureAt(sl, ppos);

        // ── 2. ОПРЕДЕЛЕНИЕ БЛОКА ПОД НОГАМИ И ПОГРУЖЕНИЯ В ВОДУ ──
        BlockPos feetPos = ppos.below();
        BlockState feetState = sl.getBlockState(feetPos);
        if (feetState.isAir()) {
            feetPos = ppos.below(2);
            feetState = sl.getBlockState(feetPos);
        }
        String standingBlock = feetState.getBlock().getName().getString();
        boolean isSubmerged = player.isEyeInFluid(FluidTags.WATER) || sl.getFluidState(ppos).is(FluidTags.WATER);

        // Если блок под ногами — доски или люк под водой, и структура не определилась — автодетекция Затонувшего корабля!
        if (isSubmerged && (standingBlock.toLowerCase().contains("доски") || standingBlock.toLowerCase().contains("planks") || standingBlock.toLowerCase().contains("дерево") || standingBlock.toLowerCase().contains("обломки"))) {
            if (structureName == null) {
                structureName = "затонувший корабль";
            }
        }

        int diamonds = 0, iron = 0, gold = 0, coal = 0;
        int chests = 0, beds = 0, crafting = 0, water = 0, lava = 0;
        boolean hasPortal = false;

        int minBuildY = sl.getMinBuildHeight();
        int maxBuildY = sl.getMaxBuildHeight();
        int playerY = ppos.getY();

        int minX = cpos.getMinBlockX();
        int minZ = cpos.getMinBlockZ();

        for (int x = minX; x <= minX + 15; x += 2) {
            for (int z = minZ; z <= minZ + 15; z += 2) {
                for (int y = Math.max(minBuildY, playerY - 30); y <= Math.min(maxBuildY, playerY + 30); y += 2) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = sl.getBlockState(pos);
                    if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) diamonds++;
                    else if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) iron++;
                    else if (state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE)) gold++;
                    else if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) coal++;
                    else if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL)) chests++;
                    else if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) beds++;
                    else if (state.is(Blocks.CRAFTING_TABLE)) crafting++;
                    else if (state.is(Blocks.WATER)) water++;
                    else if (state.is(Blocks.LAVA)) lava++;
                    else if (state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.OBSIDIAN)) hasPortal = true;
                }
            }
        }

        boolean isUnderground = !sl.canSeeSky(ppos) && ppos.getY() < 55;
        int surfaceY = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, ppos.getX(), ppos.getZ());

        return new ChunkAnalysis(
                cpos.x, cpos.z, biome, standingBlock, structureName, isSubmerged,
                diamonds, iron, gold, coal,
                chests, beds, crafting, water, lava,
                isUnderground, hasPortal, surfaceY
        );
    }

    private static String findStructureAt(ServerLevel sl, BlockPos pos) {
        try {
            var structureManager = sl.structureManager();
            // Сканируем окрестность в радиусе 16 блоков для майнкрафтовских структур
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

        // Резервная детекция Затонувшего Корабля по блокам палубы/мачты (доски, обтёсанные брёвна, люки рядом с водой/песком)
        try {
            int woodCount = 0;
            int chestCount = 0;
            int waterCount = 0;
            for (int dx = -12; dx <= 12; dx += 2) {
                for (int dz = -12; dz <= 12; dz += 2) {
                    for (int dy = -8; dy <= 8; dy += 2) {
                        BlockPos p = pos.offset(dx, dy, dz);
                        BlockState st = sl.getBlockState(p);
                        String name = st.getBlock().getName().getString().toLowerCase();
                        String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                .getKey(st.getBlock()).getPath();
                        if (blockId.contains("planks") || blockId.contains("stripped_oak")
                                || blockId.equals("oak_log") || blockId.equals("oak_wood")
                                || blockId.contains("oak_stairs") || blockId.contains("oak_slab")
                                || blockId.contains("oak_fence") || blockId.contains("oak_door")
                                || blockId.contains("oak_trapdoor")) woodCount++;
                        if (blockId.equals("chest") || blockId.equals("trapped_chest")) chestCount++;
                        if (blockId.equals("water")) waterCount++;
                        if (name.contains("доски") || name.contains("planks") || name.contains("обтёсанн") || name.contains("stripped") || name.contains("древесина")) {
                            woodCount++;
                        }
                    }
                }
            }
            // Если рядом много обработанной древесины у воды/песка/моря — это затонувший корабль!
            if (woodCount >= 8 && (chestCount > 0 || waterCount >= 8)) {
                String biome = sl.getBiome(pos).unwrapKey().map(k -> k.location().getPath()).orElse("");
                if (biome.contains("ocean") || biome.contains("sea") || biome.contains("beach") || biome.contains("river") || sl.isWaterAt(pos.below())) {
                    return "затонувший корабль";
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }
}
