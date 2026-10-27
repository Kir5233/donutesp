package com.example.donutesp;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Раз в полсекунды сканирует загруженные чанки вокруг камеры и запоминает позиции выбранных блоков. */
public final class EspScanner {
    private static final int MAX_RESULTS = 4000;
    /** Результат последнего скана. Читается из рендера, пишется из тика (оба в главном потоке). */
    public static volatile Map<Identifier, List<BlockPos>> found = new HashMap<>();
    private static int ticks;

    private EspScanner() {}

    public static void tick(MinecraftClient mc) {
        if (!EspConfig.espEnabled || mc.world == null || mc.player == null) {
            if (!found.isEmpty()) found = new HashMap<>();
            return;
        }
        if (++ticks % 10 != 0) return;

        Set<Block> wanted = new HashSet<>();
        for (Identifier id : EspConfig.blocks) wanted.add(Registries.BLOCK.get(id));
        if (wanted.isEmpty()) {
            found = new HashMap<>();
            return;
        }

        ClientWorld world = mc.world;
        net.minecraft.entity.Entity c = FreeCam.active && FreeCam.cam != null ? FreeCam.cam : mc.player;
        int cx = ((int) Math.floor(c.getX())) >> 4;
        int cz = ((int) Math.floor(c.getZ())) >> 4;
        int r = EspConfig.radiusChunks;

        Map<Identifier, List<BlockPos>> result = new HashMap<>();
        int total = 0;

        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(cx + dx, cz + dz);
                if (chunk == null) continue;
                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    ChunkSection section = sections[i];
                    if (section == null || section.isEmpty()) continue;
                    if (!section.hasAny(st -> wanted.contains(st.getBlock()))) continue;

                    int baseX = (cx + dx) << 4;
                    int baseZ = (cz + dz) << 4;
                    int baseY = chunk.sectionIndexToCoord(i) << 4;
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState st = section.getBlockState(x, y, z);
                                Block b = st.getBlock();
                                if (!wanted.contains(b)) continue;
                                result.computeIfAbsent(Registries.BLOCK.getId(b), k -> new ArrayList<>())
                                        .add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                                if (++total >= MAX_RESULTS) break outer;
                            }
                        }
                    }
                }
            }
        }
        found = result;
    }
}
