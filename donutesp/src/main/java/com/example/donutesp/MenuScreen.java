package com.example.donutesp;

import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Меню на Z. Вкладка "Модули" — переключатели и настройки, вкладка "Блоки" — выбор блоков для ESP/Xray. */
public class MenuScreen extends Screen {
    private static int tab = 0;
    private static String query = "";

    private int page = 0;
    private TextFieldWidget search;
    private ButtonWidget radiusButton;
    private ButtonWidget speedButton;
    private ButtonWidget stashMinButton;
    private ButtonWidget pageLabel;
    private final List<ButtonWidget> listButtons = new ArrayList<>();

    public MenuScreen() {
        super(Text.literal("DonutESP"));
    }

    @Override
    protected void init() {
        listButtons.clear();
        pageLabel = null;
        int cx = this.width / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal(tab == 0 ? "§e[Модули]" : "Модули"), b -> { tab = 0; clearAndInit(); })
                .dimensions(cx - 100, 22, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(tab == 1 ? "§e[Блоки]" : "Блоки"), b -> { tab = 1; clearAndInit(); })
                .dimensions(cx + 2, 22, 98, 20).build());

        if (tab == 0) initModules(cx); else initBlocks(cx);
    }

    // ---------- вкладка "Модули" ----------

    private Text label(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "§aВКЛ" : "§cВЫКЛ"));
    }

    private void toggle(int x, int y, String name, BooleanSupplier get, Runnable flip) {
        addDrawableChild(ButtonWidget.builder(label(name, get.getAsBoolean()), b -> {
            flip.run();
            b.setMessage(label(name, get.getAsBoolean()));
        }).dimensions(x, y, 98, 20).build());
    }

    private void initModules(int cx) {
        int L = cx - 100, R = cx + 2;
        int y = 48;

        toggle(L, y, "Block ESP", () -> EspConfig.espEnabled, () -> { EspConfig.espEnabled = !EspConfig.espEnabled; EspConfig.save(); });
        toggle(R, y, "Xray", () -> EspConfig.xrayEnabled, () -> Xray.toggle(client));
        y += 24;
        toggle(L, y, "Игроки", () -> EspConfig.playerEsp, () -> { EspConfig.playerEsp = !EspConfig.playerEsp; EspConfig.save(); });
        toggle(R, y, "Tracers", () -> EspConfig.tracers, () -> { EspConfig.tracers = !EspConfig.tracers; EspConfig.save(); });
        y += 24;
        toggle(L, y, "Stash", () -> EspConfig.stashEnabled, () -> { EspConfig.stashEnabled = !EspConfig.stashEnabled; EspConfig.save(); });
        toggle(R, y, "Контейнеры", () -> EspConfig.storageEsp, () -> { EspConfig.storageEsp = !EspConfig.storageEsp; EspConfig.save(); });
        y += 24;
        toggle(L, y, "FreeCam", () -> FreeCam.active, () -> FreeCam.toggle(client));
        toggle(R, y, "Fullbright", () -> EspConfig.fullbright, () -> { EspConfig.fullbright = !EspConfig.fullbright; EspConfig.save(); });
        y += 24;
        toggle(L, y, "HUD", () -> EspConfig.hudList, () -> { EspConfig.hudList = !EspConfig.hudList; EspConfig.save(); });
        toggle(R, y, "Заливка", () -> EspConfig.fillStyle, () -> { EspConfig.fillStyle = !EspConfig.fillStyle; EspConfig.save(); });

        // радиус ESP (слева) и скорость FreeCam (справа)
        y += 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> changeRadius(-1)).dimensions(L, y, 20, 20).build());
        radiusButton = addDrawableChild(ButtonWidget.builder(radiusText(), b -> {}).dimensions(L + 22, y, 54, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> changeRadius(1)).dimensions(L + 78, y, 20, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> changeSpeed(-0.2)).dimensions(R, y, 20, 20).build());
        speedButton = addDrawableChild(ButtonWidget.builder(speedText(), b -> {}).dimensions(R + 22, y, 54, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> changeSpeed(0.2)).dimensions(R + 78, y, 20, 20).build());

        // порог Stash Finder
        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> changeStashMin(-1)).dimensions(L, y, 20, 20).build());
        stashMinButton = addDrawableChild(ButtonWidget.builder(stashMinText(), b -> {}).dimensions(L + 22, y, 156, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> changeStashMin(1)).dimensions(L + 180, y, 20, 20).build());
    }

    private void changeRadius(int d) {
        EspConfig.radiusChunks = Math.max(1, Math.min(12, EspConfig.radiusChunks + d));
        EspConfig.save();
        radiusButton.setMessage(radiusText());
    }

    private void changeSpeed(double d) {
        FreeCam.changeSpeed(d);
        speedButton.setMessage(speedText());
    }

    private void changeStashMin(int d) {
        EspConfig.stashMin = Math.max(2, Math.min(60, EspConfig.stashMin + d));
        EspConfig.save();
        stashMinButton.setMessage(stashMinText());
    }

    private Text radiusText() { return Text.literal("Рад.: " + EspConfig.radiusChunks); }
    private Text speedText() { return Text.literal("Скор.: x" + FreeCam.speedText()); }
    private Text stashMinText() { return Text.literal("Stash: мин. очков: " + EspConfig.stashMin); }

    // ---------- вкладка "Блоки" ----------

    private int listY() { return 76; }

    private int perPage() {
        return Math.max(3, Math.min(10, (this.height - listY() - 30) / 22));
    }

    private void initBlocks(int cx) {
        search = new TextFieldWidget(this.textRenderer, cx - 100, 48, 200, 18, Text.literal("Поиск"));
        search.setPlaceholder(Text.literal("Поиск блока (diamond, chest, ...)"));
        search.setText(query);
        search.setChangedListener(s -> {
            query = s.trim().toLowerCase();
            page = 0;
            rebuildList();
        });
        addDrawableChild(search);
        rebuildList();
    }

    private void rebuildList() {
        for (ButtonWidget b : listButtons) remove(b);
        listButtons.clear();
        if (pageLabel != null) remove(pageLabel);

        int cx = this.width / 2;
        int perPage = perPage();
        int y = listY();

        List<Block> all = filtered();
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));

        int from = page * perPage;
        int to = Math.min(all.size(), from + perPage);
        for (int i = from; i < to; i++) {
            final Block block = all.get(i);
            final Identifier id = Registries.BLOCK.getId(block);
            ButtonWidget btn = ButtonWidget.builder(blockText(block, id), b -> {
                if (!EspConfig.blocks.remove(id)) EspConfig.blocks.add(id);
                EspConfig.save();
                b.setMessage(blockText(block, id));
                if (EspConfig.xrayEnabled) Xray.apply(client); else Xray.rebuild();
            }).dimensions(cx - 100, y, 200, 20).build();
            addDrawableChild(btn);
            listButtons.add(btn);
            y += 22;
        }

        int navY = listY() + perPage * 22 + 4;
        ButtonWidget prev = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; rebuildList(); })
                .dimensions(cx - 100, navY, 30, 20).build());
        pageLabel = addDrawableChild(ButtonWidget.builder(
                Text.literal((page + 1) + " / " + pages + "  (" + all.size() + ")"), b -> {})
                .dimensions(cx - 68, navY, 136, 20).build());
        ButtonWidget next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page++; rebuildList(); })
                .dimensions(cx + 70, navY, 30, 20).build());
        prev.active = page > 0;
        next.active = page < pages - 1;
        listButtons.add(prev);
        listButtons.add(next);
    }

    private List<Block> filtered() {
        List<Block> out = new ArrayList<>();
        for (Block b : Registries.BLOCK) {
            Identifier id = Registries.BLOCK.getId(b);
            if (id.getPath().equals("air") || id.getPath().endsWith("_air")) continue;
            if (!query.isEmpty() && !id.getPath().contains(query.replace(' ', '_'))
                    && !b.getName().getString().toLowerCase().contains(query)) continue;
            out.add(b);
        }
        out.sort(Comparator
                .comparing((Block b) -> !EspConfig.blocks.contains(Registries.BLOCK.getId(b)))
                .thenComparing(b -> Registries.BLOCK.getId(b).getPath()));
        return out;
    }

    private Text blockText(Block block, Identifier id) {
        boolean on = EspConfig.blocks.contains(id);
        return Text.literal(on ? "§a[ВКЛ] " : "§7[выкл] ").append(block.getName());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
