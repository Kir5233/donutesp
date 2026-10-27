package com.example.donutesp;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stash Finder: раз в секунду смотрит загруженные чанки и ищет те, где много контейнеров
 * (сундуки, бочки, шалкеры). Чанки с числом контейнеров >= порога считаются "стэшем".
 */
public final class StashFinder {
    public static final class Stash {
        public final int cx, cz;
        public int count;
        public final List<BlockPos> containers = new ArrayList<>();

        Stash(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }

        public int centerX() { return cx * 16 + 8; }
        public int centerZ() { return cz * 16 + 8; }
    }

    private static final int SCAN_RADIUS = 32;

    public static volatile List<Stash> stashes = new ArrayList<>();
    private static final Set<Long> announced = new HashSet<>();
    private static ClientWorld lastWorld;
    private static int ticks;

    private StashFinder() {}

    private static boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof ShulkerBoxBlockEntity;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            announced.clear();
            stashes = new ArrayList<>();
        }
        if (!EspConfig.stashEnabled || mc.world == null || mc.player == null) {
            if (!stashes.isEmpty()) stashes = new ArrayList<>();
            return;
        }
        if (++ticks % 20 != 0) return;

        Entity c = FreeCam.active && FreeCam.cam != null ? FreeCam.cam : mc.player;
        double px = c.getX();
        double pz = c.getZ();
        int pcx = ((int) Math.floor(px)) >> 4;
        int pcz = ((int) Math.floor(pz)) >> 4;

        List<Stash> out = new ArrayList<>();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                Stash s = null;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!isStorage(be)) continue;
                    if (s == null) s = new Stash(cx, cz);
                    s.count++;
                    s.containers.add(be.getPos());
                }
                if (s == null || s.count < EspConfig.stashMin) continue;

                out.add(s);
                long key = (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
                if (announced.add(key)) {
                    mc.player.sendMessage(Text.literal("§e[Stash] §fнайден: §bX " + s.centerX()
                            + " Z " + s.centerZ() + " §f(" + s.count + " контейнеров)"), false);
                }
            }
        }

        out.sort((a, b) -> {
            double da = Math.pow(a.centerX() - px, 2) + Math.pow(a.centerZ() - pz, 2);
            double db = Math.pow(b.centerX() - px, 2) + Math.pow(b.centerZ() - pz, 2);
            return Double.compare(da, db);
        });
        stashes = out;
    }
}
