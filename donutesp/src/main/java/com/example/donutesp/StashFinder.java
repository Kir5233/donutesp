package com.example.donutesp;

import net.fabricmc.loader.api.FabricLoader;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stash Finder: раз в секунду смотрит загруженные чанки, считает контейнеры (сундуки, бочки, шалкеры),
 * соседние чанки с контейнерами склеивает в один "стэш" (кластер) и показывает те, где очков >= порога.
 * Шалкер весит 2 очка. Найденные стэши пишутся в чат и в config/donutesp-stashes.txt.
 */
public final class StashFinder {
    public static final class Stash {
        public int count;
        public int score;
        public double x, y, z;
        public final List<BlockPos> containers = new ArrayList<>();

        public int centerX() { return (int) Math.round(x); }
        public int centerZ() { return (int) Math.round(z); }
    }

    private static final class Cell {
        final int cx, cz;
        int score, count;
        final List<BlockPos> list = new ArrayList<>();
        boolean visited;

        Cell(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }
    }

    private static final int SCAN_RADIUS = 32;

    /** Найденные стэши, ближайшие первыми. */
    public static volatile List<Stash> stashes = new ArrayList<>();
    /** Все контейнеры в загруженных чанках (для Storage ESP). */
    public static volatile List<BlockPos> containers = new ArrayList<>();

    private static final Set<Long> announced = new HashSet<>();
    private static ClientWorld lastWorld;
    private static int ticks;

    private StashFinder() {}

    private static int weight(BlockEntity be) {
        if (be instanceof ShulkerBoxBlockEntity) return 2;
        if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) return 1;
        return 0;
    }

    private static long key(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            announced.clear();
            stashes = new ArrayList<>();
            containers = new ArrayList<>();
        }
        boolean need = EspConfig.stashEnabled || EspConfig.storageEsp;
        if (!need || mc.world == null || mc.player == null) {
            if (!stashes.isEmpty()) stashes = new ArrayList<>();
            if (!containers.isEmpty()) containers = new ArrayList<>();
            return;
        }
        if (++ticks % 20 != 0) return;

        Entity c = FreeCam.active && FreeCam.cam != null ? FreeCam.cam : mc.player;
        double px = c.getX();
        double pz = c.getZ();
        int pcx = ((int) Math.floor(px)) >> 4;
        int pcz = ((int) Math.floor(pz)) >> 4;

        Map<Long, Cell> cells = new HashMap<>();
        List<BlockPos> all = new ArrayList<>();

        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                Cell cell = null;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    int w = weight(be);
                    if (w == 0) continue;
                    if (cell == null) cell = new Cell(cx, cz);
                    cell.score += w;
                    cell.count++;
                    cell.list.add(be.getPos());
                    all.add(be.getPos());
                }
                if (cell != null) cells.put(key(cx, cz), cell);
            }
        }
        containers = all;

        if (!EspConfig.stashEnabled) {
            stashes = new ArrayList<>();
            return;
        }

        // склеиваем соседние чанки (8 соседей), где очков >= 2
        List<Stash> out = new ArrayList<>();
        for (Cell start : cells.values()) {
            if (start.visited || start.score < 2) continue;
            Stash s = new Stash();
            ArrayDeque<Cell> queue = new ArrayDeque<>();
            queue.add(start);
            start.visited = true;
            while (!queue.isEmpty()) {
                Cell cur = queue.poll();
                s.score += cur.score;
                s.count += cur.count;
                s.containers.addAll(cur.list);
                for (int ax = -1; ax <= 1; ax++) {
                    for (int az = -1; az <= 1; az++) {
                        if (ax == 0 && az == 0) continue;
                        Cell n = cells.get(key(cur.cx + ax, cur.cz + az));
                        if (n != null && !n.visited && n.score >= 2) {
                            n.visited = true;
                            queue.add(n);
                        }
                    }
                }
            }
            if (s.score < EspConfig.stashMin) continue;

            double sx = 0, sy = 0, sz = 0;
            for (BlockPos p : s.containers) {
                sx += p.getX() + 0.5;
                sy += p.getY() + 0.5;
                sz += p.getZ() + 0.5;
            }
            int n = Math.max(1, s.containers.size());
            s.x = sx / n;
            s.y = sy / n;
            s.z = sz / n;
            out.add(s);

            long k = key(Math.floorDiv(s.centerX(), 64), Math.floorDiv(s.centerZ(), 64));
            if (announced.add(k)) {
                announce(mc, s);
            }
        }

        out.sort((a, b) -> {
            double da = Math.pow(a.x - px, 2) + Math.pow(a.z - pz, 2);
            double db = Math.pow(b.x - px, 2) + Math.pow(b.z - pz, 2);
            return Double.compare(da, db);
        });
        stashes = out;
    }

    private static void announce(MinecraftClient mc, Stash s) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§e[Stash] §fнайден: §bX " + s.centerX() + " Z " + s.centerZ()
                    + " §f(" + s.count + " контейнеров, очков " + s.score + ")"), false);
        }
        try {
            Path f = FabricLoader.getInstance().getConfigDir().resolve("donutesp-stashes.txt");
            String dim = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "?";
            String line = dim + "  X " + s.centerX() + "  Y " + (int) s.y + "  Z " + s.centerZ()
                    + "  containers=" + s.count + "  score=" + s.score + System.lineSeparator();
            Files.writeString(f, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
