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

/** Меню на клавишу Z: вкл/выкл ESP, радиус, выбор блоков (с поиском), кнопка FreeCam. */
public class MenuScreen extends Screen {
    private static final int PER_PAGE = 8;

    private String query = "";
    private int page = 0;
    private TextFieldWidget search;
    private ButtonWidget espButton;
    private ButtonWidget radiusButton;
    private ButtonWidget pageLabel;
    private final List<ButtonWidget> listButtons = new ArrayList<>();

    public MenuScreen() {
        super(Text.literal("DonutESP"));
    }

    @Override
    protected void init() {
        listButtons.clear();
        int cx = this.width / 2;
        int y = 28;

        espButton = addDrawableChild(ButtonWidget.builder(espText(), b -> {
            EspConfig.espEnabled = !EspConfig.espEnabled;
            EspConfig.save();
            b.setMessage(espText());
        }).dimensions(cx - 100, y, 98, 20).build());

        addDrawableChild(ButtonWidget.builder(freecamText(), b -> {
            FreeCam.toggle(client);
            b.setMessage(freecamText());
        }).dimensions(cx + 2, y, 98, 20).build());

        y += 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> changeRadius(-1))
                .dimensions(cx - 100, y, 20, 20).build());
        radiusButton = addDrawableChild(ButtonWidget.builder(radiusText(), b -> {})
                .dimensions(cx - 78, y, 156, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> changeRadius(1))
                .dimensions(cx + 80, y, 20, 20).build());

        y += 24;
        search = new TextFieldWidget(this.textRenderer, cx - 100, y, 200, 18, Text.literal("Поиск"));
        search.setPlaceholder(Text.literal("Поиск блока (diamond, chest, ...)"));
        search.setText(query);
        search.setChangedListener(s -> {
            query = s.trim().toLowerCase();
            page = 0;
            rebuildList();
        });
        addDrawableChild(search);

        // кнопки списка блоков и навигация создаются в rebuildList()
        rebuildList();
    }

    private void rebuildList() {
        for (ButtonWidget b : listButtons) remove(b);
        listButtons.clear();
        if (pageLabel != null) remove(pageLabel);

        int cx = this.width / 2;
        int y = 98;

        List<Block> all = filtered();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        int from = page * PER_PAGE;
        int to = Math.min(all.size(), from + PER_PAGE);
        for (int i = from; i < to; i++) {
            final Block block = all.get(i);
            final Identifier id = Registries.BLOCK.getId(block);
            ButtonWidget btn = ButtonWidget.builder(blockText(block, id), b -> {
                if (!EspConfig.blocks.remove(id)) EspConfig.blocks.add(id);
                EspConfig.save();
                b.setMessage(blockText(block, id));
            }).dimensions(cx - 100, y, 200, 20).build();
            addDrawableChild(btn);
            listButtons.add(btn);
            y += 22;
        }

        int navY = 98 + PER_PAGE * 22 + 4;
        ButtonWidget prev = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; rebuildList(); })
                .dimensions(cx - 100, navY, 30, 20).build());
        pageLabel = addDrawableChild(ButtonWidget.builder(Text.literal((page + 1) + " / " + pages + "  (" + all.size() + ")"), b -> {})
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
        // выбранные блоки — наверх, остальные по алфавиту
        out.sort(Comparator
                .comparing((Block b) -> !EspConfig.blocks.contains(Registries.BLOCK.getId(b)))
                .thenComparing(b -> Registries.BLOCK.getId(b).getPath()));
        return out;
    }

    private Text blockText(Block block, Identifier id) {
        boolean on = EspConfig.blocks.contains(id);
        return Text.literal(on ? "§a[ВКЛ] " : "§7[выкл] ").append(block.getName());
    }

    private void changeRadius(int d) {
        EspConfig.radiusChunks = Math.max(1, Math.min(12, EspConfig.radiusChunks + d));
        EspConfig.save();
        radiusButton.setMessage(radiusText());
    }

    private Text espText() {
        return Text.literal("ESP: " + (EspConfig.espEnabled ? "§aВКЛ" : "§cВЫКЛ"));
    }

    private Text freecamText() {
        return Text.literal("FreeCam (F4): " + (FreeCam.active ? "§aВКЛ" : "§cВЫКЛ"));
    }

    private Text radiusText() {
        return Text.literal("Радиус: " + EspConfig.radiusChunks + " чанк.");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
