package net.verity.worldgen;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.verity.VerityMod;

/**
 * Регистрация кастомного StructureType для Лаборатории Verity.
 */
public class VerityStructures {

    public static final StructureType<VerityLabStructure> VERITY_LAB_STRUCTURE_TYPE =
        Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            ResourceLocation.parse(VerityMod.MOD_ID + ":verity_lab"),
            () -> VerityLabStructure.CODEC
        );

    /**
     * Загружает класс и регистрирует StructureType.
     * Вызывается из VerityMod для инициализации worldgen.
     */
    public static void init() {
        // Триггер загрузки класса — регистрация происходит через static field
    }
}
