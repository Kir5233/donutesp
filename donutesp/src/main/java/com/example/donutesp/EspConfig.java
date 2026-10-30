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
    // сохраняются
    public static boolean espEnabled = false;      // ESP блоков
    public static boolean playerEsp = false;       // игроки сквозь стены + ники
    public static boolean tracers = false;         // линии к игрокам
    public static boolean stashEnabled = false;    // Stash Finder
    public static boolean storageEsp = false;      // подсветка всех контейнеров
    public static boolean fullbright = false;
    public static boolean hudList = true;          // список включённых модулей
    public static boolean fillStyle = true;        // true = заливка, false = только контур
    public static boolean accurateView = true;     // отключить покачивание и FOV-эффекты (точнее ESP)

    // НЕ сохраняется: при старте игры Xray всегда выключен
    public static boolean xrayEnabled = false;

    public static int radiusChunks = 5;
    public static int stashMin = 8;
    public static double camSpeed = 1.0;

    /** Блоки, выбранные в меню: используются и для ESP, и для Xray. */
    public static final Set<Identifier> blocks = new LinkedHashSet<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutesp.txt");
    }

    private static boolean b(String line, String key) {
        return Boolean.parseBoolean(line.substring(key.length()));
    }

    public static void load() {
        blocks.clear();
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
                if (line.startsWith("esp=")) espEnabled = b(line, "esp=");
                else if (line.startsWith("players=")) playerEsp = b(line, "players=");
                else if (line.startsWith("tracers=")) tracers = b(line, "tracers=");
                else if (line.startsWith("stash=")) stashEnabled = b(line, "stash=");
                else if (line.startsWith("storage=")) storageEsp = b(line, "storage=");
                else if (line.startsWith("fullbright=")) fullbright = b(line, "fullbright=");
                else if (line.startsWith("hudList=")) hudList = b(line, "hudList=");
                else if (line.startsWith("fill=")) fillStyle = b(line, "fill=");
                else if (line.startsWith("accurate=")) accurateView = b(line, "accurate=");
                else if (line.startsWith("radius=")) radiusChunks = Math.max(1, Math.min(12, Integer.parseInt(line.substring(7))));
                else if (line.startsWith("stashMin=")) stashMin = Math.max(2, Math.min(60, Integer.parseInt(line.substring(9))));
                else if (line.startsWith("camSpeed=")) camSpeed = Math.max(0.2, Math.min(5.0, Double.parseDouble(line.substring(9))));
                else if (line.startsWith("block=")) {
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
        out.add("players=" + playerEsp);
        out.add("tracers=" + tracers);
        out.add("stash=" + stashEnabled);
        out.add("storage=" + storageEsp);
        out.add("fullbright=" + fullbright);
        out.add("hudList=" + hudList);
        out.add("fill=" + fillStyle);
        out.add("accurate=" + accurateView);
        out.add("radius=" + radiusChunks);
        out.add("stashMin=" + stashMin);
        out.add("camSpeed=" + camSpeed);
        for (Identifier id : blocks) out.add("block=" + id);
        try {
            Files.write(file(), out);
        } catch (IOException ignored) {
        }
    }
}
