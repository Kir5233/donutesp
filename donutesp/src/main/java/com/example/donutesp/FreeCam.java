package com.example.donutesp;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * FreeCam без mixin'ов.
 *
 * Камера — отдельная сущность. Мышь вращает настоящего игрока как обычно, а мы каждый кадр
 * переносим это вращение на камеру и каждый тик возвращаем игрока в замороженный угол
 * (на сервер уходит неподвижный поворот). Если игра сама вращает камеру-сущность — это тоже учитывается.
 * Стрелки на клавиатуре тоже поворачивают камеру.
 */
public final class FreeCam {
    public static boolean active;
    public static OtherClientPlayerEntity cam;

    private static double vx, vy, vz;

    private static float camYaw, camPitch;          // "истинный" угол камеры
    private static float camYawSet, camPitchSet;    // что мы последний раз записали в камеру
    private static float frozenYaw, frozenPitch;    // угол, в котором держим игрока
    private static float plYawSeen, plPitchSeen;    // угол игрока, который мы уже учли
    private static float origYaw, origPitch;        // чтобы вернуть игроку после выхода

    private FreeCam() {}

    public static void toggle(MinecraftClient mc) {
        if (active) disable(mc); else enable(mc);
    }

    private static void enable(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        origYaw = mc.player.getYaw();
        origPitch = mc.player.getPitch();

        cam = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), "freecam"));
        cam.copyPositionAndRotation(mc.player);
        cam.refreshPositionAndAngles(mc.player.getX(), mc.player.getY(), mc.player.getZ(), origYaw, origPitch);

        camYaw = origYaw;
        camPitch = origPitch;
        camYawSet = cam.getYaw();
        camPitchSet = cam.getPitch();

        // игрока держим на pitch 0, чтобы вверх/вниз камера могла вращаться симметрично
        frozenYaw = origYaw;
        frozenPitch = 0f;
        mc.player.setYaw(frozenYaw);
        mc.player.setPitch(frozenPitch);
        plYawSeen = frozenYaw;
        plPitchSeen = frozenPitch;

        vx = vy = vz = 0;
        mc.setCameraEntity(cam);
        active = true;
        message(mc, "§aFreeCam ВКЛ §7(скорость x" + speedText() + ")");
    }

    public static void disable(MinecraftClient mc) {
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
            mc.player.setYaw(origYaw);
            mc.player.setPitch(origPitch);
            if (active) message(mc, "§cFreeCam ВЫКЛ");
        }
        active = false;
        cam = null;
    }

    public static String speedText() {
        return String.valueOf(Math.round(EspConfig.camSpeed * 10) / 10.0);
    }

    public static void changeSpeed(double delta) {
        double v = Math.round((EspConfig.camSpeed + delta) * 10) / 10.0;
        EspConfig.camSpeed = Math.max(0.2, Math.min(5.0, v));
        EspConfig.save();
    }

    public static void message(MinecraftClient mc, String text) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(text), true);
    }

    /** Записать истинный угол в сущность-камеру, не ломая плавность движения. */
    private static void writeCam() {
        double lx = cam.lastRenderX, ly = cam.lastRenderY, lz = cam.lastRenderZ;
        cam.refreshPositionAndAngles(cam.getX(), cam.getY(), cam.getZ(), camYaw, camPitch);
        cam.lastRenderX = lx;
        cam.lastRenderY = ly;
        cam.lastRenderZ = lz;
        camYawSet = cam.getYaw();
        camPitchSet = cam.getPitch();
    }

    /** Перенос поворота мыши на камеру. Вызывается каждый кадр (из HUD) и каждый тик. */
    public static void sync(MinecraftClient mc) {
        if (!active || cam == null || mc.player == null) return;

        float dpY = mc.player.getYaw() - plYawSeen;
        float dpP = mc.player.getPitch() - plPitchSeen;
        float dcY = cam.getYaw() - camYawSet;
        float dcP = cam.getPitch() - camPitchSet;
        plYawSeen = mc.player.getYaw();
        plPitchSeen = mc.player.getPitch();

        if (dpY == 0 && dpP == 0 && dcY == 0 && dcP == 0) return;
        camYaw = camYaw + dpY + dcY;
        camPitch = MathHelper.clamp(camPitch + dpP + dcP, -90f, 90f);
        writeCam();
    }

    public static void tick(MinecraftClient mc) {
        if (!active) return;
        if (mc.player == null || mc.world == null || cam == null) {
            active = false;
            cam = null;
            return;
        }
        // защита: получили урон — выходим из FreeCam
        if (mc.player.hurtTime > 0) {
            disable(mc);
            return;
        }

        // учесть поворот мыши и вернуть игрока в замороженный угол ДО отправки пакетов
        sync(mc);
        mc.player.setYaw(frozenYaw);
        mc.player.setPitch(frozenPitch);
        plYawSeen = frozenYaw;
        plPitchSeen = frozenPitch;

        // замораживаем настоящего игрока и блокируем клики
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        mc.options.attackKey.setPressed(false);
        mc.options.useKey.setPressed(false);

        boolean typing = mc.currentScreen != null;
        long h = mc.getWindow().getHandle();
        boolean w = !typing && down(h, GLFW.GLFW_KEY_W);
        boolean s = !typing && down(h, GLFW.GLFW_KEY_S);
        boolean a = !typing && down(h, GLFW.GLFW_KEY_A);
        boolean d = !typing && down(h, GLFW.GLFW_KEY_D);
        boolean up = !typing && down(h, GLFW.GLFW_KEY_SPACE);
        boolean dn = !typing && down(h, GLFW.GLFW_KEY_LEFT_SHIFT);
        boolean fast = !typing && down(h, GLFW.GLFW_KEY_LEFT_CONTROL);
        boolean slow = !typing && down(h, GLFW.GLFW_KEY_LEFT_ALT);

        // поворот стрелками
        if (!typing) {
            float ry = 0, rp = 0;
            if (down(h, GLFW.GLFW_KEY_LEFT)) ry -= 4f;
            if (down(h, GLFW.GLFW_KEY_RIGHT)) ry += 4f;
            if (down(h, GLFW.GLFW_KEY_UP)) rp -= 4f;
            if (down(h, GLFW.GLFW_KEY_DOWN)) rp += 4f;
            if (ry != 0 || rp != 0) {
                camYaw += ry;
                camPitch = MathHelper.clamp(camPitch + rp, -90f, 90f);
                writeCam();
            }
        }

        double speed = EspConfig.camSpeed * 0.5 * (fast ? 2.5 : 1.0) * (slow ? 0.3 : 1.0);
        double yaw = Math.toRadians(camYaw);
        double pitch = Math.toRadians(camPitch);
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);

        double mx = 0, my = 0, mz = 0;
        if (w) { mx += fx; my += fy; mz += fz; }
        if (s) { mx -= fx; my -= fy; mz -= fz; }
        if (d) { mx += rx; mz += rz; }
        if (a) { mx -= rx; mz -= rz; }
        if (up) my += 1;
        if (dn) my -= 1;

        double len = Math.sqrt(mx * mx + my * my + mz * mz);
        double tx = 0, ty = 0, tz = 0;
        if (len > 0) {
            tx = mx / len * speed;
            ty = my / len * speed;
            tz = mz / len * speed;
        }
        vx += (tx - vx) * 0.35;
        vy += (ty - vy) * 0.35;
        vz += (tz - vz) * 0.35;
        if (Math.abs(vx) < 1e-4) vx = 0;
        if (Math.abs(vy) < 1e-4) vy = 0;
        if (Math.abs(vz) < 1e-4) vz = 0;

        cam.lastRenderX = cam.getX();
        cam.lastRenderY = cam.getY();
        cam.lastRenderZ = cam.getZ();
        cam.setPosition(cam.getX() + vx, cam.getY() + vy, cam.getZ() + vz);
    }

    /** Оставлено для совместимости со старым EntityMixin; больше не используется. */
    public static void look(double dx, double dy) {
    }

    private static boolean down(long handle, int key) {
        return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
    }
}
