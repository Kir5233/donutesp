package com.example.donutesp;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/**
 * Отрисовка рамок вокруг найденных блоков СКВОЗЬ стены.
 *
 * ВСЁ, что зависит от внутреннего рендер-API конкретной версии, собрано в этом файле
 * (событие, RenderPipeline, RenderLayer, drawBox). Если после обновления Fabric/yarn
 * что-то не компилируется — править нужно только здесь.
 */
public final class EspRenderer {
    // линии без проверки глубины => видны сквозь блоки
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("donutesp", "pipeline/esp_lines"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    private static final RenderLayer LAYER = RenderLayer.of(
            "donutesp_lines", 1536, false, false, PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false));

    private EspRenderer() {}

    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(EspRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (!EspConfig.espEnabled) return;
        Map<Identifier, List<BlockPos>> found = EspScanner.found;
        if (found.isEmpty()) return;

        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider consumers = ctx.consumers();
        if (ms == null || consumers == null) return;

        Vec3d cam = ctx.camera().getPos();
        VertexConsumer vc = consumers.getBuffer(LAYER);

        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);
        for (Map.Entry<Identifier, List<BlockPos>> e : found.entrySet()) {
            int rgb = colorFor(e.getKey());
            float r = ((rgb >> 16) & 0xFF) / 255f;
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float b = (rgb & 0xFF) / 255f;
            for (BlockPos p : e.getValue()) {
                VertexRendering.drawBox(ms, vc,
                        p.getX(), p.getY(), p.getZ(),
                        p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                        r, g, b, 1f);
            }
        }
        ms.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(LAYER);
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
        // остальные — стабильный цвет из хэша id
        int h = id.toString().hashCode();
        int r = 80 + (h & 0x7F) * 1;
        int g = 80 + ((h >> 7) & 0x7F);
        int b = 80 + ((h >> 14) & 0x7F);
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }
}
