package com.example.donutesp;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Настройки мода, хранятся в config/donutesp.txt */
public final class EspConfig {
    public static boolean espEnabled = false;
    public static int radiusChunks = 5;
    public static final Set<Identifier> blocks = new LinkedHashSet<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutesp.txt");
    }

    public static void load() {
        blocks.clear();
        // значения по умолчанию
        blocks.add(Identifier.of("minecraft", "diamond_ore"));
        blocks.add(Identifier.of("minecraft", "deepslate_diamond_ore"));
        blocks.add(Identifier.of("minecraft", "ancient_debris"));
        blocks.add(Identifier.of("minecraft", "spawner"));

        Path f = file();
        if (!Files.exists(f)) return;
        try {
            blocks.clear();
            for (String line : Files.readAllLines(f)) {
                line = line.trim();
                if (line.startsWith("esp=")) {
                    espEnabled = Boolean.parseBoolean(line.substring(4));
                } else if (line.startsWith("radius=")) {
                    radiusChunks = Math.max(1, Math.min(12, Integer.parseInt(line.substring(7))));
                } else if (line.startsWith("block=")) {
                    Identifier id = Identifier.tryParse(line.substring(6));
                    if (id != null && Registries.BLOCK.containsId(id)) blocks.add(id);
                }
            }
        } catch (IOException | NumberFormatException ignored) {
        }
    }

    public static void save() {
        List<String> out = new ArrayList<>();
        out.add("esp=" + espEnabled);
        out.add("radius=" + radiusChunks);
        for (Identifier id : blocks) out.add("block=" + id);
        try {
            Files.write(file(), out);
        } catch (IOException ignored) {
        }
    }
}
