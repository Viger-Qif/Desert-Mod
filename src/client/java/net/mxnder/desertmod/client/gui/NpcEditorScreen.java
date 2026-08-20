package net.mxnder.desertmod.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.mxnder.desertmod.network.NpcSkinPayloads;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Редактор NPC: слева — секции и справка по сущности,
 * справа — контент секции (скролл-список или поля ввода).
 * Мир не на паузе: применяешь значение и сразу видишь результат.
 */
public class NpcEditorScreen extends Screen {

    private enum Mode { SKIN, ANIM, ROT, POS }

    // Левая панель — навигация и справка; правая — контент
    private static final int LEFT_X = 12;
    private static final int LEFT_W = 110;
    private static final int RIGHT_X = LEFT_X + LEFT_W + 8;
    private static final int RIGHT_W = 236;
    private static final int ROW_H = 22;
    private static final int VISIBLE = 7;    // строк списка справа
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

    private EditBox yawBox, xBox, yBox, zBox;

    public NpcEditorScreen(UUID npcId, List<String> skins, List<String> anims,
                           String currentSkin, String currentAnim) {
        super(Component.literal("Редактор NPC"));
        this.npcId = npcId;
        this.skins = skins;
        this.anims = anims;
        this.currentSkin = currentSkin.isEmpty() ? "—" : currentSkin;
        this.currentAnim = currentAnim.isEmpty() ? "idle" : currentAnim;
    }

    private List<String> items() {
        return mode == Mode.SKIN ? skins : anims;
    }

    /** Живая сущность на клиенте — из неё берём справку и префилл полей. */
    private Entity npc() {
        return this.minecraft.level != null ? this.minecraft.level.getEntity(npcId) : null;
    }

    @Override
    protected void init() {
        super.init();
        int top = this.height / 2 - 120;

        // === СЛЕВА: секции ===
        addLabel("Редактор NPC", LEFT_X, top, LEFT_W);
        int y = top + 24;
        for (Mode m : Mode.values()) {
            Button b = Button.builder(Component.literal(titleOf(m)), btn -> {
                mode = m;
                scroll = 0;
                rebuildWidgets();
            }).bounds(LEFT_X, y, LEFT_W, 20).build();
            b.active = mode != m; // активная секция подсвечена «неактивностью»
            addRenderableWidget(b);
            y += 22;
        }

        // === СЛЕВА: справка по NPC (обновляется при каждом rebuild) ===
        y += 6;
        Entity e = npc();
        addLabel("Скин: " + shortName(currentSkin, 11), LEFT_X, y, LEFT_W);
        y += 22;
        addLabel("Аним: " + shortName(currentAnim, 11), LEFT_X, y, LEFT_W);
        y += 22;
        if (e != null) {
            addLabel("Поворот: " + fmt(e.getYRot()), LEFT_X, y, LEFT_W);
            y += 22;
            addLabel(fmt(e.getX()) + " " + fmt(e.getY()) + " " + fmt(e.getZ()), LEFT_X, y, LEFT_W);
        }

        // === СПРАВА: контент секции ===
        if (mode == Mode.SKIN || mode == Mode.ANIM) {
            initList(top);
        } else if (mode == Mode.ROT) {
            initRot(top, e);
        } else {
            initPos(top, e);
        }
    }

    // --- правая панель: скролл-список (скины/анимации) ---
    private void initList(int top) {
        List<String> items = items();
        maxScroll = Math.max(0, items.size() - VISIBLE);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        int first = (int) scroll;

        for (int row = 0; row < VISIBLE; row++) {
            int idx = first + row;
            if (idx >= items.size()) break;
            String name = items.get(idx);
            addRenderableWidget(Button.builder(Component.literal(shortName(name, 24)), btn -> {
                if (mode == Mode.SKIN) {
                    ClientPlayNetworking.send(new NpcSkinPayloads.SetSkin(npcId.toString(), name));
                    currentSkin = name;
                } else {
                    ClientPlayNetworking.send(new NpcSkinPayloads.SetAnim(npcId.toString(), name));
                    currentAnim = name;
                }
                rebuildWidgets(); // справка слева обновится
            }).bounds(RIGHT_X + 2, top + row * ROW_H, RIGHT_W - 16, 20).build());
        }

        barX = RIGHT_X + RIGHT_W - 10;
        barY = top;
        barH = VISIBLE * ROW_H;
        thumbH = items.size() <= VISIBLE ? barH
                : Math.max(14, barH * VISIBLE / items.size());

        addRenderableWidget(Button.builder(Component.literal("Готово"), btn -> this.onClose())
                .bounds(RIGHT_X, top + VISIBLE * ROW_H + 6, RIGHT_W, 20).build());
    }

    // --- правая панель: поворот ---
    private void initRot(int top, Entity e) {
        addLabel("Угол:", RIGHT_X, top + 2, 60);
        yawBox = new EditBox(this.font, RIGHT_X + 64, top + 2, 120, 20, Component.literal(""));
        yawBox.setMaxLength(8);
        yawBox.setValue(fmt(e != null ? e.getYRot() : 0f));
        addRenderableWidget(yawBox);
        addRenderableWidget(Button.builder(Component.literal("Применить"), btn -> applyRot())
                .bounds(RIGHT_X, top + 30, RIGHT_W - 60, 20).build());
    }

    private void applyRot() {
        try {
            float yaw = Float.parseFloat(yawBox.getValue().trim());
            ClientPlayNetworking.send(new NpcSkinPayloads.SetRotation(npcId.toString(), yaw));
            Entity e = npc(); // мгновенный отклик на клиенте, сервер подтвердит
            if (e != null) {
                e.setYRot(yaw);
                e.setYHeadRot(yaw);
                e.setYBodyRot(yaw);
            }
            rebuildWidgets();
        } catch (Exception ignored) {
        }
    }

    // --- правая панель: позиция ---
    private void initPos(int top, Entity e) {
        double px = e != null ? e.getX() : 0, py = e != null ? e.getY() : 0, pz = e != null ? e.getZ() : 0;
        xBox = addCoordBox("X:", top, fmt(px));
        yBox = addCoordBox("Y:", top + 26, fmt(py));
        zBox = addCoordBox("Z:", top + 52, fmt(pz));
        addRenderableWidget(Button.builder(Component.literal("Применить"), btn -> applyPos())
                .bounds(RIGHT_X, top + 80, RIGHT_W - 60, 20).build());
    }

    private EditBox addCoordBox(String label, int y, String value) {
        addLabel(label, RIGHT_X, y, 30);
        EditBox box = new EditBox(this.font, RIGHT_X + 34, y, 140, 20, Component.literal(""));
        box.setMaxLength(12);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private void applyPos() {
        try {
            double x = Double.parseDouble(xBox.getValue().trim());
            double y = Double.parseDouble(yBox.getValue().trim());
            double z = Double.parseDouble(zBox.getValue().trim());
            ClientPlayNetworking.send(new NpcSkinPayloads.SetPosition(npcId.toString(), x, y, z));
            Entity e = npc();
            if (e != null) e.setPos(x, y, z);
            rebuildWidgets();
        } catch (Exception ignored) {
        }
    }

    // === ФОН: две полупрозрачные панели, мир виден, без блюра ===

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void extractTransparentBackground(GuiGraphicsExtractor graphics) {
        int top = this.height / 2 - 120;
        int leftH = 24 + 4 * 22 + 6 + 4 * 22 + 8;
        int rightH = VISIBLE * ROW_H + 40;
        graphics.fillGradient(LEFT_X - 4, top - 4, LEFT_X + LEFT_W + 4, top + leftH,
                0xB4101010, 0xB4101010);
        graphics.fillGradient(RIGHT_X - 4, top - 4, RIGHT_X + RIGHT_W + 4, top + rightH,
                0xB4101010, 0xB4101010);
    }

    // === ПОЛЗУНОК (только в секциях-списках) ===

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (maxScroll <= 0 || (mode != Mode.SKIN && mode != Mode.ANIM)) return;
        graphics.fillGradient(barX, barY, barX + BAR_W, barY + barH, 0x66AAAAAA, 0x66AAAAAA);
        int ty = barY + (int) ((barH - thumbH) * (scroll / maxScroll));
        graphics.fillGradient(barX, ty, barX + BAR_W, ty + thumbH, 0xFFDDDDDD, 0xFFDDDDDD);
    }

    // === МЫШЬ ===

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mode != Mode.SKIN && mode != Mode.ANIM)
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scroll = Mth.clamp(scroll - scrollY, 0, maxScroll);
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if ((mode == Mode.SKIN || mode == Mode.ANIM)
                && event.button() == 0 && overBar(event.x(), event.y())) {
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

    // === МЕЛОЧИ ===

    private void addLabel(String text, int x, int y, int w) {
        Button b = Button.builder(Component.literal(text), btn -> {
        }).bounds(x, y, w, 20).build();
        b.active = false;
        addRenderableWidget(b);
    }

    private static String titleOf(Mode m) {
        return switch (m) {
            case SKIN -> "Скин";
            case ANIM -> "Анимация";
            case ROT -> "Поворот";
            case POS -> "Позиция";
        };
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String shortName(String name, int max) {
        return name.length() > max ? name.substring(0, max - 1) + "…" : name;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}