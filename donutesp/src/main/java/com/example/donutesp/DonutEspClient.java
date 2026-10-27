package com.example.donutesp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class DonutEspClient implements ClientModInitializer {
    private static KeyBinding menuKey;
    private static KeyBinding freecamKey;
    private static KeyBinding speedUpKey;
    private static KeyBinding speedDownKey;
    private boolean greeted;

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
            // сбрасываем накопленные нажатия
            while (menuKey.wasPressed()) { }
            while (freecamKey.wasPressed()) { }
            while (speedUpKey.wasPressed()) { }
            while (speedDownKey.wasPressed()) { }
            FreeCam.tick(mc);
            return;
        }

        if (!greeted) {
            greeted = true;
            mc.player.sendMessage(Text.literal("§a[DonutESP] §fзагружен: §eZ§f — меню, §eF4§f — FreeCam"), false);
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
    }
}
