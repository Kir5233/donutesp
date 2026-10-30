package com.example.donutesp;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Вся отрисовка идёт через HUD: 3D-точки сами проецируем на экран и рисуем закрашенные силуэты блоков
 * (выпуклая оболочка 8 углов куба), поэтому подсветка видна сквозь стены и не зависит от внутренних
 * рендер-пайплайнов Minecraft.
 */
public final class EspRenderer {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static long tickStart = System.nanoTime();
    private static int budget;

    private static final double[] TMP = new double[3];
    private static final double[] SX = new double[8];
    private static final double[] SY = new double[8];
    private static final double[] PX = new double[8];
    private static final double[] PY = new double[8];
    private static final double[] HX = new double[20];
    private static final double[] HY = new double[20];

    private EspRenderer() {}

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(c -> tickStart = System.nanoTime());
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("donutesp", "esp"),
                (ctx, tickCounter) -> render(ctx));
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    private static double wrap(double a) {
        while (a > 180) a -= 360;
        while (a < -180) a += 360;
        return a;
    }

    /** Параметры камеры на текущий кадр. */
    private static final class View {
        double td;
        double ex, ey, ez;
        double fx, fy, fz, rx, rz, ux, uy, uz;
        double tanHalf, aspect;
        int sw, sh;

        static View of(MinecraftClient mc, Entity e) {
            View v = new View();
            v.td = Math.max(0.0, Math.min(1.0, (System.nanoTime() - tickStart) / 50_000_000.0));
            v.ex = lerp(v.td, e.lastRenderX, e.getX());
            v.ey = lerp(v.td, e.lastRenderY, e.getY()) + (e.getEyeY() - e.getY());
            v.ez = lerp(v.td, e.lastRenderZ, e.getZ());

            double yaw = Math.toRadians(e.getYaw());
            double pitch = Math.toRadians(e.getPitch());
            v.fx = -Math.sin(yaw) * Math.cos(pitch);
            v.fy = -Math.sin(pitch);
            v.fz = Math.cos(yaw) * Math.cos(pitch);
            v.rx = -Math.cos(yaw);
            v.rz = -Math.sin(yaw);
            v.ux = -v.rz * v.fy;
            v.uy = v.rz * v.fx - v.rx * v.fz;
            v.uz = v.rx * v.fy;

            v.sw = mc.getWindow().getScaledWidth();
            v.sh = mc.getWindow().getScaledHeight();
            v.tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
            v.aspect = (double) v.sw / (double) v.sh;
            return v;
        }

        /** Мировая точка -> экран. out = {x, y, глубина}. false, если точка позади камеры. */
        boolean project(double wx, double wy, double wz, double[] out) {
            double dx = wx - ex, dy = wy - ey, dz = wz - ez;
            double z = dx * fx + dy * fy + dz * fz;
            if (z <= 0.05) return false;
            double x = dx * rx + dz * rz;
            double y = dx * ux + dy * uy + dz * uz;
            out[0] = (x / (z * tanHalf * aspect) * 0.5 + 0.5) * sw;
            out[1] = (0.5 - y / (z * tanHalf) * 0.5) * sh;
            out[2] = z;
            return true;
        }
    }

    // ---------- примитивы ----------

    private static double cross(double ox, double oy, double ax, double ay, double bx, double by) {
        return (ax - ox) * (by - oy) - (ay - oy) * (bx - ox);
    }

    /** Выпуклая оболочка точек SX/SY (8 штук) -> HX/HY. Возвращает число вершин. */
    private static int hull() {
        int n = 8;
        for (int i = 0; i < n; i++) { PX[i] = SX[i]; PY[i] = SY[i]; }
        // сортировка вставками по x, затем по y
        for (int i = 1; i < n; i++) {
            double kx = PX[i], ky = PY[i];
            int j = i - 1;
            while (j >= 0 && (PX[j] > kx || (PX[j] == kx && PY[j] > ky))) {
                PX[j + 1] = PX[j];
                PY[j + 1] = PY[j];
                j--;
            }
            PX[j + 1] = kx;
            PY[j + 1] = ky;
        }
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cross(HX[k - 2], HY[k - 2], HX[k - 1], HY[k - 1], PX[i], PY[i]) <= 0) k--;
            HX[k] = PX[i];
            HY[k] = PY[i];
            k++;
        }
        int t = k + 1;
        for (int i = n - 2; i >= 0; i--) {
            while (k >= t && cross(HX[k - 2], HY[k - 2], HX[k - 1], HY[k - 1], PX[i], PY[i]) <= 0) k--;
            HX[k] = PX[i];
            HY[k] = PY[i];
            k++;
        }
        return Math.max(1, k - 1);
    }

    /** Закрашенный силуэт куба (как подсвеченный блок). Видно сквозь стены. */
    private static void drawBox(DrawContext ctx, View v,
                                double x1, double y1, double z1, double x2, double y2, double z2, int rgb) {
        if (budget <= 0) return;
        for (int i = 0; i < 8; i++) {
            double wx = (i & 1) == 0 ? x1 : x2;
            double wy = ((i >> 1) & 1) == 0 ? y1 : y2;
            double wz = ((i >> 2) & 1) == 0 ? z1 : z2;
            if (!v.project(wx, wy, wz, TMP)) return;
            SX[i] = TMP[0];
            SY[i] = TMP[1];
        }
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            minX = Math.min(minX, SX[i]);
            maxX = Math.max(maxX, SX[i]);
            minY = Math.min(minY, SY[i]);
            maxY = Math.max(maxY, SY[i]);
        }
        if (maxX < 0 || minX > v.sw || maxY < 0 || minY > v.sh) return;

        int fill = EspConfig.fillStyle ? ((0x50 << 24) | rgb) : 0;
        int edge = 0xFF000000 | rgb;

        int h = hull();
        if (h < 3) {
            int cx = (int) ((minX + maxX) / 2), cy = (int) ((minY + maxY) / 2);
            ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, edge);
            return;
        }

        int yTop = (int) Math.floor(minY);
        int yBot = (int) Math.floor(maxY);
        int ys = Math.max(0, yTop);
        int ye = Math.min(v.sh - 1, yBot);
        for (int y = ys; y <= ye; y++) {
            if (budget-- <= 0) return;
            double yc = y + 0.5;
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int i = 0; i < h; i++) {
                int j = (i + 1) % h;
                double ya = HY[i], yb = HY[j];
                if ((ya <= yc && yb > yc) || (yb <= yc && ya > yc)) {
                    double t = (yc - ya) / (yb - ya);
                    double x = HX[i] + t * (HX[j] - HX[i]);
                    if (x < lo) lo = x;
                    if (x > hi) hi = x;
                }
            }
            if (lo > hi) continue;
            int xl = (int) Math.max(0, Math.floor(lo));
            int xr = (int) Math.min(v.sw, Math.ceil(hi));
            if (xr <= xl) continue;

            boolean cap = (y == yTop || y == yBot);
            if (cap) {
                ctx.fill(xl, y, xr, y + 1, edge);
            } else {
                if (fill != 0) ctx.fill(xl, y, xr, y + 1, fill);
                ctx.fill(xl, y, Math.min(xl + 1, xr), y + 1, edge);
                ctx.fill(Math.max(xr - 1, xl), y, xr, y + 1, edge);
            }
        }
    }

    private static void drawLine(DrawContext ctx, double x0, double y0, double x1, double y1, int argb) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.max(Math.abs(dx), Math.abs(dy));
        int steps = (int) Math.min(600, Math.max(1, len / 1.5));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int px = (int) (x0 + dx * t);
            int py = (int) (y0 + dy * t);
            ctx.fill(px, py, px + 2, py + 2, argb);
        }
    }

    private static void text(DrawContext ctx, MinecraftClient mc, String s, int x, int y, int color) {
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s), x, y, color);
    }

    private static void textCenter(DrawContext ctx, MinecraftClient mc, String s, int cx, int y, int color) {
        Text t = Text.literal(s);
        ctx.drawTextWithShadow(mc.textRenderer, t, cx - mc.textRenderer.getWidth(t) / 2, y, color);
    }

    // ---------- кадр ----------

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        FreeCam.sync(mc); // плавный поворот камеры каждый кадр

        Entity e = mc.getCameraEntity();
        if (e == null) e = mc.player;
        View v = View.of(mc, e);
        budget = 2500;

        // 1) игроки
        if (EspConfig.playerEsp || EspConfig.tracers) {
            int traced = 0;
            for (PlayerEntity p : mc.world.getPlayers()) {
                boolean self = p == mc.player;
                if (self && !FreeCam.active) continue;
                if (p.isSpectator()) continue;

                double ox = lerp(v.td, p.lastRenderX, p.getX()) - p.getX();
                double oy = lerp(v.td, p.lastRenderY, p.getY()) - p.getY();
                double oz = lerp(v.td, p.lastRenderZ, p.getZ()) - p.getZ();
                Box b = p.getBoundingBox();
                int rgb = self ? 0x33FF66 : 0xFF3A3A;
                double cx = (b.minX + b.maxX) / 2 + ox;
                double cz = (b.minZ + b.maxZ) / 2 + oz;
                double cy = (b.minY + b.maxY) / 2 + oy;

                if (EspConfig.playerEsp) {
                    drawBox(ctx, v, b.minX + ox, b.minY + oy, b.minZ + oz, b.maxX + ox, b.maxY + oy, b.maxZ + oz, rgb);
                    if (v.project(cx, b.maxY + oy + 0.35, cz, TMP)) {
                        double dx = cx - v.ex, dy = cy - v.ey, dz = cz - v.ez;
                        int dist = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
                        String label = (self ? "Вы (тело)" : p.getName().getString())
                                + "  " + Math.round(p.getHealth()) + "hp  " + dist + "м";
                        textCenter(ctx, mc, label, (int) TMP[0], (int) TMP[1] - 9, 0xFF000000 | rgb);
                    }
                }
                if (EspConfig.tracers && !self && traced < 12 && v.project(cx, cy, cz, TMP)) {
                    drawLine(ctx, v.sw / 2.0, v.sh, TMP[0], TMP[1], 0xCC000000 | rgb);
                    traced++;
                }
            }
        }

        // 2) Stash Finder
        if (EspConfig.stashEnabled) {
            List<StashFinder.Stash> list = StashFinder.stashes;
            for (StashFinder.Stash s : list) {
                for (BlockPos p : s.containers) {
                    drawBox(ctx, v, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, 0xFFA500);
                }
                if (v.project(s.x, s.y + 1.0, s.z, TMP)) {
                    double dx = s.x - v.ex, dz = s.z - v.ez;
                    textCenter(ctx, mc, "STASH " + (int) Math.sqrt(dx * dx + dz * dz) + "м",
                            (int) TMP[0], (int) TMP[1] - 10, 0xFFFFD52E);
                }
            }

            // список слева сверху
            int y = 6;
            text(ctx, mc, "Stash Finder: " + list.size() + " найдено (порог " + EspConfig.stashMin + ")", 6, y, 0xFFFFD52E);
            y += 11;
            int shown = 0;
            for (StashFinder.Stash s : list) {
                if (shown++ >= 6) break;
                double dx = s.x - e.getX(), dz = s.z - e.getZ();
                text(ctx, mc, shown + ". X " + s.centerX() + " Z " + s.centerZ()
                        + "  [" + s.count + "]  " + (int) Math.sqrt(dx * dx + dz * dz) + "м", 6, y, 0xFFFFFFFF);
                y += 10;
            }

            // стрелка у прицела к ближайшему стэшу
            if (!list.isEmpty()) {
                StashFinder.Stash n = list.get(0);
                double dx = n.x - v.ex, dz = n.z - v.ez;
                double dist = Math.sqrt(dx * dx + dz * dz);
                double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
                double rel = wrap(targetYaw - e.getYaw());
                int sector = ((int) Math.round(rel / 45.0) % 8 + 8) % 8;
                int cx = v.sw / 2, cy = v.sh / 2;
                double rr = 38;
                int mx = (int) (cx + Math.sin(Math.toRadians(rel)) * rr);
                int my = (int) (cy - Math.cos(Math.toRadians(rel)) * rr);
                ctx.fill(mx - 4, my - 4, mx + 5, my + 5, 0xFFFFA500);
                ctx.fill(mx - 2, my - 2, mx + 3, my + 3, 0xFFFFFFFF);
                textCenter(ctx, mc, ARROWS[sector] + "  STASH " + (int) dist + "м  (" + n.centerX() + ", " + n.centerZ() + ")",
                        cx, cy + 48, 0xFFFFD52E);
            }
        }

        // 3) все контейнеры (Storage ESP), ближайшие 300
        if (EspConfig.storageEsp) {
            List<BlockPos> all = StashFinder.containers;
            List<BlockPos> near = new ArrayList<>(all);
            near.sort((a, b) -> Double.compare(d2(a, v), d2(b, v)));
            int n = Math.min(300, near.size());
            for (int i = 0; i < n; i++) {
                BlockPos p = near.get(i);
                drawBox(ctx, v, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, 0xFFC040);
            }
        }

        // 4) блоки ESP
        if (EspConfig.espEnabled) {
            for (Map.Entry<Identifier, List<BlockPos>> entry : EspScanner.found.entrySet()) {
                int rgb = colorFor(entry.getKey());
                for (BlockPos p : entry.getValue()) {
                    drawBox(ctx, v, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, rgb);
                }
            }
        }

        // 5) HUD: статус FreeCam, координаты, список модулей
        if (FreeCam.active) {
            textCenter(ctx, mc, "FREECAM  x" + FreeCam.speedText() + "   (PgUp/PgDn скорость, Ctrl быстро, Alt медленно)",
                    v.sw / 2, v.sh - 52, 0xFF7CFC00);
        }
        if (EspConfig.hudList) {
            text(ctx, mc, String.format("XYZ %.1f / %.1f / %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                    6, v.sh - 12, 0xFFFFFFFF);

            List<String> mods = new ArrayList<>();
            if (EspConfig.espEnabled) mods.add("ESP");
            if (EspConfig.xrayEnabled) mods.add("Xray");
            if (EspConfig.playerEsp) mods.add("Players");
            if (EspConfig.tracers) mods.add("Tracers");
            if (EspConfig.stashEnabled) mods.add("StashFinder");
            if (EspConfig.storageEsp) mods.add("Storage");
            if (FreeCam.active) mods.add("FreeCam");
            if (EspConfig.fullbright) mods.add("Fullbright");
            int y = 6;
            for (String m : mods) {
                Text t = Text.literal(m);
                ctx.drawTextWithShadow(mc.textRenderer, t, v.sw - mc.textRenderer.getWidth(t) - 6, y, 0xFF7CFC00);
                y += 10;
            }
        }
    }

    private static double d2(BlockPos p, View v) {
        double dx = p.getX() + 0.5 - v.ex, dy = p.getY() + 0.5 - v.ey, dz = p.getZ() + 0.5 - v.ez;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int colorFor(Identifier id) {
        String p = id.getPath();
        if (p.contains("diamond")) return 0x33E6FF;
        if (p.contains("ancient_debris")) return 0xFF7A1A;
        if (p.contains("emerald")) return 0x2BE05A;
        if (p.contains("gold")) return 0xFFD52E;
        if (p.contains("iron")) return 0xE0C8B0;
        if (p.contains("redstone")) return 0xFF2A2A;
        if (p.contains("lapis")) return 0x3A5BFF;
        if (p.contains("copper")) return 0xE08A5A;
        if (p.contains("coal")) return 0x9A9A9A;
        if (p.contains("quartz")) return 0xF2F2F2;
        if (p.contains("spawner")) return 0xB04BFF;
        if (p.contains("chest") || p.contains("barrel") || p.contains("shulker")) return 0xFFA500;
        int h = id.toString().hashCode();
        int r = Math.min(255, 80 + (h & 0x7F));
        int g = Math.min(255, 80 + ((h >> 7) & 0x7F));
        int b = Math.min(255, 80 + ((h >> 14) & 0x7F));
        return (r << 16) | (g << 8) | b;
    }
}
