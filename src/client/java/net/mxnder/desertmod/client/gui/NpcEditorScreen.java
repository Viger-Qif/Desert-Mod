package net.mxnder.desertmod.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.mxnder.desertmod.network.NpcSkinPayloads;

import java.util.List;
import java.util.UUID;

/**
 * Редактор NPC: панель слева, мир виден и не на паузе.
 * Сверху — переключатель секций «Скин/Анимация» и строки
 * «что сейчас надето / что сейчас играет», ниже — список
 * выбранной секции с ползунком. Клик по элементу применяет
 * его сразу и обновляет строку состояния.
 */
public class NpcEditorScreen extends Screen {

    private enum Mode { SKIN, ANIM }

    private static final int PANEL_X = 12;
    private static final int PANEL_W = 236;
    private static final int ROW_H = 22;
    private static final int VISIBLE = 7;    // строк списка видно
    private static final int BAR_W = 6;

    private final UUID npcId;
    private final List<String> skins;
    private final List<String> anims;
    private String currentSkin;
    private String currentAnim;
    private Mode mode = Mode.SKIN;

    private double scroll;
    private int maxScroll;
    private boolean dragging;
    private int barX, barY, barH, thumbH;

    public NpcEditorScreen(UUID npcId, List<String> skins, List<String> anims,
                           String currentSkin, String currentAnim) {
        super(Component.literal("Редактор NPC"));
        this.npcId = npcId;
        this.skins = skins;
        this.anims = anims;
        this.currentSkin = currentSkin.isEmpty() ? "—" : currentSkin;
        this.currentAnim = currentAnim.isEmpty() ? "idle" : currentAnim;
    }

    /** Что показывать в списке при текущей вкладке. */
    private List<String> items() {
        return mode == Mode.SKIN ? skins : anims;
    }

    @Override
    protected void init() {
        super.init();
        List<String> items = items();
        maxScroll = Math.max(0, items.size() - VISIBLE);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        int first = (int) scroll;
        int panelY = this.height / 2 - 150;

        // === Переключатель секций: активная подсвечена «неактивностью» ===
        Button tabSkin = Button.builder(Component.literal("Скин"), btn -> {
            mode = Mode.SKIN;
            scroll = 0;
            rebuildWidgets();
        }).bounds(PANEL_X, panelY, PANEL_W / 2 - 2, 20).build();
        tabSkin.active = mode != Mode.SKIN;
        addRenderableWidget(tabSkin);

        Button tabAnim = Button.builder(Component.literal("Анимация"), btn -> {
            mode = Mode.ANIM;
            scroll = 0;
            rebuildWidgets();
        }).bounds(PANEL_X + PANEL_W / 2 + 2, panelY, PANEL_W / 2 - 2, 20).build();
        tabAnim.active = mode != Mode.ANIM;
        addRenderableWidget(tabAnim);

        // === Строки состояния: что сейчас на NPC ===
        Button infoSkin = Button.builder(
                Component.literal("Скин: " + shortName(currentSkin, 26)), b -> {
                }).bounds(PANEL_X, panelY + 24, PANEL_W, 20).build();
        infoSkin.active = false;
        addRenderableWidget(infoSkin);

        Button infoAnim = Button.builder(
                Component.literal("Анимация: " + shortName(currentAnim, 20)), b -> {
                }).bounds(PANEL_X, panelY + 46, PANEL_W, 20).build();
        infoAnim.active = false;
        addRenderableWidget(infoAnim);

        int listY = panelY + 70;

        // === Список текущей секции ===
        for (int row = 0; row < VISIBLE; row++) {
            int idx = first + row;
            if (idx >= items.size()) break;
            String name = items.get(idx);
            addRenderableWidget(Button.builder(Component.literal(shortName(name, 24)), btn -> {
                if (mode == Mode.SKIN) {
                    ClientPlayNetworking.send(
                            new NpcSkinPayloads.SetSkin(npcId.toString(), name));
                    currentSkin = name; // строка состояния обновится сразу
                } else {
                    ClientPlayNetworking.send(
                            new NpcSkinPayloads.SetAnim(npcId.toString(), name));
                    currentAnim = name;
                }
                rebuildWidgets();
            }).bounds(PANEL_X + 2, listY + row * ROW_H, PANEL_W - 16, 20).build());
        }

        // === Геометрия ползунка ===
        barX = PANEL_X + PANEL_W - 10;
        barY = listY;
        barH = VISIBLE * ROW_H;
        thumbH = items.size() <= VISIBLE ? barH
                : Math.max(14, barH * VISIBLE / items.size());

        // === Готово ===
        addRenderableWidget(Button.builder(Component.literal("Готово"), btn -> this.onClose())
                .bounds(PANEL_X, listY + VISIBLE * ROW_H + 6, PANEL_W, 20).build());
    }

    // === ФОН: без блюра, только панель ===

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void extractTransparentBackground(GuiGraphicsExtractor graphics) {
        int panelY = this.height / 2 - 150;
        graphics.fillGradient(PANEL_X - 4, panelY - 4,
                PANEL_X + PANEL_W + 4, panelY + 70 + VISIBLE * ROW_H + 32,
                0xB4101010, 0xB4101010);
    }

    // === ПОЛЗУНОК ===

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (maxScroll <= 0) return;
        graphics.fillGradient(barX, barY, barX + BAR_W, barY + barH,
                0x66AAAAAA, 0x66AAAAAA);
        int ty = barY + (int) ((barH - thumbH) * (scroll / maxScroll));
        graphics.fillGradient(barX, ty, barX + BAR_W, ty + thumbH,
                0xFFDDDDDD, 0xFFDDDDDD);
    }

    // === МЫШЬ ===

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scroll = Mth.clamp(scroll - scrollY, 0, maxScroll);
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && overBar(event.x(), event.y())) {
            dragging = true;
            scrollFromMouse(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            scrollFromMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    private boolean overBar(double mx, double my) {
        return maxScroll > 0
                && mx >= barX - 2 && mx <= barX + BAR_W + 2
                && my >= barY && my <= barY + barH;
    }

    private void scrollFromMouse(double mouseY) {
        double t = (mouseY - barY - thumbH / 2.0) / (barH - thumbH);
        scroll = Mth.clamp(t * maxScroll, 0, maxScroll);
        rebuildWidgets();
    }

    private static String shortName(String name, int max) {
        return name.length() > max ? name.substring(0, max - 1) + "…" : name;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}