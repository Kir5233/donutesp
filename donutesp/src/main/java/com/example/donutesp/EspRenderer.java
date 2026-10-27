package com.example.donutesp;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * Отрисовка через HUD: позиции блоков сами проецируем на экран и рисуем рамки через DrawContext.fill.
 * Не использует внутренних рендер-пайплайнов. Рамки видны сквозь стены (рисуются поверх мира).
 * Здесь же рисуется список Stash Finder и подпись FreeCam.
 */
public final class EspRenderer {
    private static long tickStart = System.nanoTime();

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

    /** Параметры камеры на текущий кадр. */
    private static final class View {
        double ex, ey, ez;
        double fx, fy, fz, rx, rz, ux, uy, uz;
        double tanHalf, aspect;
        int sw, sh;

        static View of(MinecraftClient mc, Entity e) {
            View v = new View();
            double td = Math.max(0.0, Math.min(1.0, (System.nanoTime() - tickStart) / 50_000_000.0));
            v.ex = lerp(td, e.lastRenderX, e.getX());
            v.ey = lerp(td, e.lastRenderY, e.getY()) + (e.getEyeY() - e.getY());
            v.ez = lerp(td, e.lastRenderZ, e.getZ());

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
    }

    private static void drawBlock(DrawContext ctx, View v, BlockPos p, int color) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (int i = 0; i < 8; i++) {
            double dx = p.getX() + (i & 1) - v.ex;
            double dy = p.getY() + ((i >> 1) & 1) - v.ey;
            double dz = p.getZ() + ((i >> 2) & 1) - v.ez;
            double z = dx * v.fx + dy * v.fy + dz * v.fz;
            if (z <= 0.05) return;
            double x = dx * v.rx + dz * v.rz;
            double y = dx * v.ux + dy * v.uy + dz * v.uz;
            double sx = (x / (z * v.tanHalf * v.aspect) * 0.5 + 0.5) * v.sw;
            double sy = (0.5 - y / (z * v.tanHalf) * 0.5) * v.sh;
            if (sx < minX) minX = sx;
            if (sx > maxX) maxX = sx;
            if (sy < minY) minY = sy;
            if (sy > maxY) maxY = sy;
        }
        if (maxX < 0 || minX > v.sw || maxY < 0 || minY > v.sh) return;

        int x1 = (int) Math.max(-1000, minX);
        int y1 = (int) Math.max(-1000, minY);
        int x2 = (int) Math.min(v.sw + 1000, maxX);
        int y2 = (int) Math.min(v.sh + 1000, maxY);
        if (x2 - x1 < 3) x2 = x1 + 3;
        if (y2 - y1 < 3) y2 = y1 + 3;

        ctx.fill(x1, y1, x2, y1 + 1, color);
        ctx.fill(x1, y2 - 1, x2, y2, color);
        ctx.fill(x1, y1, x1 + 1, y2, color);
        ctx.fill(x2 - 1, y1, x2, y2, color);
    }

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        Entity e = mc.getCameraEntity();
        if (e == null) e = mc.player;
        View v = View.of(mc, e);

        // ESP по выбранным блокам
        if (EspConfig.espEnabled) {
            for (Map.Entry<Identifier, List<BlockPos>> entry : EspScanner.found.entrySet()) {
                int color = 0xFF000000 | colorFor(entry.getKey());
                for (BlockPos p : entry.getValue()) drawBlock(ctx, v, p, color);
            }
        }

        // Stash Finder: контейнеры в найденных чанках + список слева сверху
        if (EspConfig.stashEnabled) {
            List<StashFinder.Stash> list = StashFinder.stashes;
            for (StashFinder.Stash s : list) {
                for (BlockPos p : s.containers) drawBlock(ctx, v, p, 0xFFFFA500);
            }
            int y = 6;
            ctx.drawTextWithShadow(mc.textRenderer,
                    Text.literal("Stash Finder: " + list.size() + " найдено (порог " + EspConfig.stashMin + ")"),
                    6, y, 0xFFFFD52E);
            y += 11;
            int shown = 0;
            for (StashFinder.Stash s : list) {
                if (shown++ >= 6) break;
                double dx = s.centerX() - e.getX();
                double dz = s.centerZ() - e.getZ();
                int dist = (int) Math.sqrt(dx * dx + dz * dz);
                ctx.drawTextWithShadow(mc.textRenderer,
                        Text.literal(shown + ". X " + s.centerX() + " Z " + s.centerZ()
                                + "  [" + s.count + "]  " + dist + "м"),
                        6, y, 0xFFFFFFFF);
                y += 10;
            }
        }

        // статус FreeCam
        if (FreeCam.active) {
            Text t = Text.literal("FREECAM  x" + FreeCam.speedText() + "   (PgUp/PgDn скорость, Ctrl быстро, Alt медленно)");
            int w = mc.textRenderer.getWidth(t);
            ctx.drawTextWithShadow(mc.textRenderer, t, (v.sw - w) / 2, v.sh - 52, 0xFF7CFC00);
        }
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
