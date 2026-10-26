package com.example.donutesp;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * FreeCam: подменяем камеру на невидимую "фейковую" сущность, а настоящего игрока замораживаем
 * (сбрасываем нажатия WASD/Space/Shift). Вращение мыши перехватывает EntityMixin.
 */
public final class FreeCam {
    public static boolean active;
    public static OtherClientPlayerEntity cam;

    private FreeCam() {}

    public static void toggle(MinecraftClient mc) {
        if (active) disable(mc); else enable(mc);
    }

    private static void enable(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        cam = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), "freecam"));
        cam.copyPositionAndRotation(mc.player);
        cam.refreshPositionAndAngles(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.getYaw(), mc.player.getPitch());
        mc.setCameraEntity(cam);
        active = true;
    }

    public static void disable(MinecraftClient mc) {
        if (mc.player != null) mc.setCameraEntity(mc.player);
        active = false;
        cam = null;
    }

    /** Вызывается из EntityMixin при движении мыши. */
    public static void look(double dx, double dy) {
        if (cam == null) return;
        float yaw = cam.getYaw() + (float) dx * 0.15f;
        float pitch = MathHelper.clamp(cam.getPitch() + (float) dy * 0.15f, -90f, 90f);
        cam.setYaw(yaw);
        cam.setPitch(pitch);
        cam.prevYaw = yaw;
        cam.prevPitch = pitch;
    }

    public static void tick(MinecraftClient mc) {
        if (!active) return;
        if (mc.player == null || mc.world == null || cam == null) {
            active = false;
            cam = null;
            return;
        }

        long h = mc.getWindow().getHandle();
        boolean w = down(h, GLFW.GLFW_KEY_W);
        boolean s = down(h, GLFW.GLFW_KEY_S);
        boolean a = down(h, GLFW.GLFW_KEY_A);
        boolean d = down(h, GLFW.GLFW_KEY_D);
        boolean up = down(h, GLFW.GLFW_KEY_SPACE);
        boolean dn = down(h, GLFW.GLFW_KEY_LEFT_SHIFT);
        boolean fast = down(h, GLFW.GLFW_KEY_LEFT_CONTROL);

        // замораживаем настоящего игрока
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);

        double speed = fast ? 1.6 : 0.7;
        double yaw = Math.toRadians(cam.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);

        double mx = 0, my = 0, mz = 0;
        if (w) { mx += fx; mz += fz; }
        if (s) { mx -= fx; mz -= fz; }
        if (d) { mx += rx; mz += rz; }
        if (a) { mx -= rx; mz -= rz; }
        if (up) my += 1;
        if (dn) my -= 1;

        double len = Math.sqrt(mx * mx + my * my + mz * mz);
        if (len > 0) { mx = mx / len * speed; my = my / len * speed; mz = mz / len * speed; }

        // для плавной интерполяции между тиками
        cam.lastRenderX = cam.getX();
        cam.lastRenderY = cam.getY();
        cam.lastRenderZ = cam.getZ();
        cam.setPosition(cam.getX() + mx, cam.getY() + my, cam.getZ() + mz);
    }

    private static boolean down(long handle, int key) {
        return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
    }
}
