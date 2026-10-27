package com.example.donutesp;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Xray: рисуем только выбранные в меню блоки, остальные грани не рисуются (см. BlockMixin).
 * Список блоков общий с ESP.
 */
public final class Xray {
    private static volatile Set<Block> wanted = new HashSet<>();

    private Xray() {}

    public static void rebuild() {
        Set<Block> s = new HashSet<>();
        for (Identifier id : EspConfig.blocks) s.add(Registries.BLOCK.get(id));
        wanted = s;
    }

    public static boolean isWanted(Block b) {
        return wanted.contains(b);
    }

    /** Пересобрать список и перерисовать чанки, чтобы изменения стали видны. */
    public static void apply(MinecraftClient mc) {
        rebuild();
        if (mc.world != null && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    public static void toggle(MinecraftClient mc) {
        EspConfig.xrayEnabled = !EspConfig.xrayEnabled;
        apply(mc);
    }
}
