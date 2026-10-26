package com.example.donutesp;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * ESP через HUD: позиции блоков сами проецируем на экран и рисуем рамки через DrawContext.fill.
 * Не использует внутренних рендер-пайплайнов, поэтому почти не ломается между версиями.
 * Рамки видны сквозь стены (рисуются поверх мира).
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

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!EspConfig.espEnabled || mc.world == null) return;
        Entity e = mc.getCameraEntity();
        if (e == null) return;
        Map<Identifier, List<BlockPos>> found = EspScanner.found;
        if (found.isEmpty()) return;

        double td = Math.max(0.0, Math.min(1.0, (System.nanoTime() - tickStart) / 50_000_000.0));
        double ex = lerp(td, e.lastRenderX, e.getX());
        double ey = lerp(td, e.lastRenderY, e.getY()) + (e.getEyeY() - e.getY());
        double ez = lerp(td, e.lastRenderZ, e.getZ());

        double yaw = Math.toRadians(e.getYaw());
        double pitch = Math.toRadians(e.getPitch());
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) sw / (double) sh;

        for (Map.Entry<Identifier, List<BlockPos>> entry : found.entrySet()) {
            int color = 0xFF000000 | colorFor(entry.getKey());
            for (BlockPos p : entry.getValue()) {
                double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
                double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                boolean ok = true;

                for (int i = 0; i < 8 && ok; i++) {
                    double dx = p.getX() + (i & 1) - ex;
                    double dy = p.getY() + ((i >> 1) & 1) - ey;
                    double dz = p.getZ() + ((i >> 2) & 1) - ez;
                    double z = dx * fx + dy * fy + dz * fz;
                    if (z <= 0.05) { ok = false; break; }
                    double x = dx * rx + dz * rz;
                    double y = dx * ux + dy * uy + dz * uz;
                    double sx = (x / (z * tanHalf * aspect) * 0.5 + 0.5) * sw;
                    double sy = (0.5 - y / (z * tanHalf) * 0.5) * sh;
                    if (sx < minX) minX = sx;
                    if (sx > maxX) maxX = sx;
                    if (sy < minY) minY = sy;
                    if (sy > maxY) maxY = sy;
                }
                if (!ok) continue;
                if (maxX < 0 || minX > sw || maxY < 0 || minY > sh) continue;

                int x1 = (int) Math.max(-1000, minX);
                int y1 = (int) Math.max(-1000, minY);
                int x2 = (int) Math.min(sw + 1000, maxX);
                int y2 = (int) Math.min(sh + 1000, maxY);
                if (x2 - x1 < 3) { x2 = x1 + 3; }
                if (y2 - y1 < 3) { y2 = y1 + 3; }

                ctx.fill(x1, y1, x2, y1 + 1, color);
                ctx.fill(x1, y2 - 1, x2, y2, color);
                ctx.fill(x1, y1, x1 + 1, y2, color);
                ctx.fill(x2 - 1, y1, x2, y2, color);
            }
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
