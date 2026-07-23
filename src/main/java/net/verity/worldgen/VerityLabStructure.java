package net.verity.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.Optional;

/**
 * "Лаборатория Verity" — генерируется под землёй (~Y=-50) в биоме Пустоты.
 *
 * Lore (lor.txt lines 125-133):
 *   "Под землёй (Y = -50) на глубине 1000 блоков от спавна генерируется
 *    структура «Лаборатория Verity». Это биом «Пустоты» (Void),
 *    куда игрок может попасть только через портал, который Verity
 *    случайно открывает в фазе 3."
 *
 * Поскольку портал в лабораторию открывает сам Verity в фазе COUNTDOWN,
 * сама комната (стены, пол, Якорь, Терминал) строится процедурно
 * через {@link #buildLabRoom} — это вызывается из VerityEntity при
 * достижении фазы COUNTDOWN. Данный класс регистрирует структуру
 * в реестре, чтобы она была валидной точкой интереса (и могла
 * использоваться майнкрафтовским worldgen при желании).
 */
public class VerityLabStructure extends Structure {

    public static final MapCodec<VerityLabStructure> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            Structure.settingsCodec(instance),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            Codec.intRange(0, 16).fieldOf("size").forGetter(s -> s.size)
        ).apply(instance, VerityLabStructure::new)
    );

    private final Holder<StructureTemplatePool> startPool;
    private final int size;

    public VerityLabStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(GenerationContext context) {
        // Лаборатория не генерируется естественным worldgen —
        // её строит Verity в фазе COUNTDOWN (см. buildLabRoom).
        // Возвращаем пустой Optional, чтобы не плодить лишние чанки.
        return Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return VerityStructures.VERITY_LAB_STRUCTURE_TYPE;
    }

    /**
     * Процедурно строит комнату лаборатории вокруг (centerX, centerZ) на Y=-50.
     * Содержит: пол, стены, Якорь Verity, Терминал Verity,
     * "туалет" и "измерительный прибор" (декоративные блоки).
     */
    public static void buildLabRoom(net.minecraft.server.level.ServerLevel level,
                                  int centerX, int centerZ) {
        final int y = -50;
        final int R = 5; // радиус комнаты (11x11)

        BlockState floor = Blocks.BEDROCK.defaultBlockState();
        BlockState wall = Blocks.OBSIDIAN.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        // Пол
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                level.setBlock(new BlockPos(centerX + dx, y, centerZ + dz), floor, 2);
            }
        }
        // Стены + потолок
        for (int dy = 1; dy <= 4; dy++) {
            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    boolean edge = (dx == -R || dx == R || dz == -R || dz == R || dy == 4);
                    if (edge) {
                        level.setBlock(new BlockPos(centerX + dx, y + dy, centerZ + dz), wall, 2);
                    } else {
                        level.setBlock(new BlockPos(centerX + dx, y + dy, centerZ + dz), air, 2);
                    }
                }
            }
        }

        // Якорь Verity (центр)
        level.setBlock(new BlockPos(centerX, y + 1, centerZ),
                net.verity.VerityMod.VERITY_ANCHOR.defaultBlockState(), 2);

        // Терминал (рядом с якорем)
        level.setBlock(new BlockPos(centerX + 2, y + 1, centerZ),
                net.verity.VerityMod.VERITY_TERMINAL.defaultBlockState(), 2);

        // "Туалет" (декор) — кварцевый блок
        level.setBlock(new BlockPos(centerX - 2, y + 1, centerZ + 2),
                Blocks.QUARTZ_BLOCK.defaultBlockState(), 2);
        // "Измерительный прибор" (декор) — редстоун-блок
        level.setBlock(new BlockPos(centerX + 2, y + 1, centerZ - 2),
                Blocks.REDSTONE_BLOCK.defaultBlockState(), 2);

        // Освещение
        level.setBlock(new BlockPos(centerX, y + 4, centerZ),
                Blocks.GLOWSTONE.defaultBlockState(), 2);
    }
}
