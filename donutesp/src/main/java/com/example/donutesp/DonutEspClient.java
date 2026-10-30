package com.example.donutesp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class DonutEspClient implements ClientModInitializer {
    private static KeyBinding menuKey;
    private static KeyBinding freecamKey;
    private static KeyBinding speedUpKey;
    private static KeyBinding speedDownKey;
    private static KeyBinding zoomKey;

    private boolean greeted;
    private boolean zooming;
    private int savedFov = 70;
    private boolean fullbrightApplied;

    @Override
    public void onInitializeClient() {
        EspConfig.load();
        Xray.rebuild();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("donutesp", "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.donutesp.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, category));
        freecamKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.donutesp.freecam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F4, category));
        speedUpKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.donutesp.speed_up", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, category));
        speedDownKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.donutesp.speed_down", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, category));
        zoomKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.donutesp.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, category));

        EspRenderer.init();
        ClientTickEvents.START_CLIENT_TICK.register(this::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            EspScanner.tick(mc);
            StashFinder.tick(mc);
        });
    }

    private void startTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            greeted = false;
            fullbrightApplied = false;
            while (menuKey.wasPressed()) { }
            while (freecamKey.wasPressed()) { }
            while (speedUpKey.wasPressed()) { }
            while (speedDownKey.wasPressed()) { }
            FreeCam.tick(mc);
            return;
        }

        if (!greeted) {
            greeted = true;
            mc.player.sendMessage(Text.literal("§a[DonutESP] §fзагружен: §eZ§f — меню, §eF4§f — FreeCam, §eC§f — Zoom"), false);
            if (EspConfig.accurateView) {
                // чтобы рамки точно совпадали с блоками: без покачивания камеры и FOV-эффектов
                mc.options.getBobView().setValue(false);
                mc.options.getFovEffectScale().setValue(0.0);
            }
        }

        while (menuKey.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new MenuScreen());
        }
        while (freecamKey.wasPressed()) {
            if (mc.currentScreen == null) FreeCam.toggle(mc);
        }
        while (speedUpKey.wasPressed()) {
            FreeCam.changeSpeed(0.2);
            FreeCam.message(mc, "§eСкорость FreeCam: x" + FreeCam.speedText());
        }
        while (speedDownKey.wasPressed()) {
            FreeCam.changeSpeed(-0.2);
            FreeCam.message(mc, "§eСкорость FreeCam: x" + FreeCam.speedText());
        }

        FreeCam.tick(mc);
        zoom(mc);
        fullbright(mc);
    }

    /** Zoom: пока зажата C, FOV = 30. */
    private void zoom(MinecraftClient mc) {
        boolean want = zoomKey.isPressed() && mc.currentScreen == null;
        if (want && !zooming) {
            savedFov = mc.options.getFov().getValue();
            zooming = true;
        }
        if (want) {
            mc.options.getFov().setValue(30);
        } else if (zooming) {
            mc.options.getFov().setValue(savedFov);
            zooming = false;
        }
    }

    /** Fullbright: клиентский эффект ночного зрения (сервер о нём не знает). Включается и вместе с Xray. */
    private void fullbright(MinecraftClient mc) {
        boolean want = EspConfig.fullbright || EspConfig.xrayEnabled;
        StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (want) {
            if (cur == null || cur.getDuration() < 4000) {
                mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 100000, 0, false, false, false));
                fullbrightApplied = true;
            }
        } else if (fullbrightApplied) {
            if (cur != null && cur.getDuration() > 50000) {
                mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
            fullbrightApplied = false;
        }
    }
}
