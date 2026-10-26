package com.example.donutesp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class DonutEspClient implements ClientModInitializer {
    private boolean zWas;
    private boolean f4Was;

    @Override
    public void onInitializeClient() {
        EspConfig.load();
        EspRenderer.init();
        ClientTickEvents.START_CLIENT_TICK.register(this::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(EspScanner::tick);
    }

    private void startTick(MinecraftClient mc) {
        if (mc.getWindow() == null) return;
        long handle = mc.getWindow().getHandle();

        boolean z = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
        if (z && !zWas && mc.currentScreen == null && mc.world != null) {
            mc.setScreen(new MenuScreen());
        }
        zWas = z;

        boolean f4 = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_F4) == GLFW.GLFW_PRESS;
        if (f4 && !f4Was && mc.currentScreen == null) {
            FreeCam.toggle(mc);
        }
        f4Was = f4;

        FreeCam.tick(mc);
    }
}
