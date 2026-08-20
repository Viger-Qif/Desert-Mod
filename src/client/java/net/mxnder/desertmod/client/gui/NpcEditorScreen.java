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
 * Редактор NPC: слева — голые кнопки секций, справа у края экрана —
 * контент секции. Центр пустой, мир виден и не на паузе.
 */
public class NpcEditorScreen extends Screen {

    private enum Mode { SKIN, ANIM, LOC, DEL }

    private static final int LEFT_X = 12;
    private static final int LEFT_W = 110;
    private static final int RIGHT_W = 190;
    private static final int ROW_H = 22;
    private static final int VISIBLE = 7;
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

    /** Правая панель прилегает к правому краю; центр остаётся под NPC. */
    private int rightX() {
        return Math.max(this.width - RIGHT_W - 12, LEFT_X + LEFT_W + 8);
    }

    private List<String> items() {
        return mode == Mode.SKIN ? skins : anims;
    }

    private boolean isListMode() {
        return mode == Mode.SKIN || mode == Mode.ANIM;
    }

    /** Живая сущность на клиенте — для префилла полей. */
    private Entity npc() {
        return this.minecraft.level != null ? this.minecraft.level.getEntity(npcId) : null;
    }

    @Override
    protected void init() {
        super.init();
        int top = this.height / 2 - 120;

        // === СЛЕВА: только кнопки секций, без фона и справки ===
        int y = top;
        for (Mode m : Mode.values()) {
            if (m == Mode.DEL) y += 8; // опасная зона отдельно от настроек
            Button b = Button.builder(Component.literal(titleOf(m)), btn -> {
                mode = m;
                scroll = 0;
                rebuildWidgets();
            }).bounds(LEFT_X, y, LEFT_W, 20).build();
            b.active = mode != m;
            addRenderableWidget(b);
            y += 22;
        }

        // === СПРАВА: контент секции (выбор живёт ЗДЕСЬ, а не в initList) ===
        Entity e = npc();
        if (isListMode()) {
            initList(top);
        } else if (mode == Mode.LOC) {
            initLoc(top, e);
        } else {
            initDelete(top);
        }
    }

    // --- СПРАВА: скролл-список (скины/анимации) ---
    private void initList(int top) {
        List<String> items = items();
        maxScroll = Math.max(0, items.size() - VISIBLE);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        int first = (int) scroll;

        for (int row = 0; row < VISIBLE; row++) {
            int idx = first + row;
            if (idx >= items.size()) break;
            String name = items.get(idx);
            addRenderableWidget(Button.builder(Component.literal(shortName(name, 18)), btn -> {
                if (mode == Mode.SKIN) {
                    ClientPlayNetworking.send(new NpcSkinPayloads.SetSkin(npcId.toString(), name));
                    currentSkin = name;
                } else {
                    ClientPlayNetworking.send(new NpcSkinPayloads.SetAnim(npcId.toString(), name));
                    currentAnim = name;
                }
                rebuildWidgets();
            }).bounds(rightX() + 2, top + row * ROW_H, RIGHT_W - 16, 20).build());
        }

        barX = rightX() + RIGHT_W - 10;
        barY = top;
        barH = VISIBLE * ROW_H;
        thumbH = items.size() <= VISIBLE ? barH
                : Math.max(14, barH * VISIBLE / items.size());

        addRenderableWidget(Button.builder(Component.literal("Готово"), btn -> this.onClose())
                .bounds(rightX(), top + VISIBLE * ROW_H + 6, RIGHT_W, 20).build());
    }

    // --- СПРАВА: расположение (позиция + поворот в одной секции) ---
    private void initLoc(int top, Entity e) {
        double px = e != null ? e.getX() : 0;
        double py = e != null ? e.getY() : 0;
        double pz = e != null ? e.getZ() : 0;
        float yaw = e != null ? e.getYRot() : 0f;

        yawBox = addField("Поворот:", top, fmt(yaw));
        xBox = addField("X:", top + 26, fmt(px));
        yBox = addField("Y:", top + 52, fmt(py));
        zBox = addField("Z:", top + 78, fmt(pz));

        addRenderableWidget(Button.builder(Component.literal("Применить"), btn -> applyLoc())
                .bounds(rightX(), top + 108, RIGHT_W - 60, 20).build());
    }

    private EditBox addField(String label, int y, String value) {
        addLabel(label, rightX(), y + 2, 60);
        EditBox box = new EditBox(this.font, rightX() + 64, y, 120, 20, Component.literal(""));
        box.setMaxLength(12);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private void applyLoc() {
        try {
            double x = Double.parseDouble(xBox.getValue().trim());
            double y = Double.parseDouble(yBox.getValue().trim());
            double z = Double.parseDouble(zBox.getValue().trim());
            float yaw = Float.parseFloat(yawBox.getValue().trim());

            String id = npcId.toString();
            ClientPlayNetworking.send(new NpcSkinPayloads.SetPosition(id, x, y, z));
            ClientPlayNetworking.send(new NpcSkinPayloads.SetRotation(id, yaw));

            Entity e = npc(); // мгновенный отклик, сервер подтвердит
            if (e != null) {
                e.setPos(x, y, z);
                e.setYRot(yaw);
                e.setYHeadRot(yaw);
                e.setYBodyRot(yaw);
            }
            rebuildWidgets();
        } catch (Exception ignored) {
        }
    }

    // --- СПРАВА: подтверждение удаления ---
    private void initDelete(int top) {
        addLabel("§cУдалить этого NPC?", rightX(), top + 4, RIGHT_W);
        // Одна кнопка на всю ширину, как надпись выше.
        // «Отмена» — это клик по любой другой секции слева, ESC — закрыть всё окно.
        addRenderableWidget(Button.builder(Component.literal("§cДа, удалить"), btn -> {
            ClientPlayNetworking.send(new NpcSkinPayloads.DeleteNpc(npcId.toString()));
            this.onClose();
        }).bounds(rightX(), top + 32, RIGHT_W, 20).build());
    }

    // === ФОН: только правая панель, без блюра ===

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void extractTransparentBackground(GuiGraphicsExtractor graphics) {
        int top = this.height / 2 - 120;
        int rightH = VISIBLE * ROW_H + 40;
        graphics.fillGradient(rightX() - 4, top - 4, rightX() + RIGHT_W + 4, top + rightH,
                0xB4101010, 0xB4101010);
    }

    // === ПОЛЗУНОК (только в списках) ===

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!isListMode() || maxScroll <= 0) return;
        graphics.fillGradient(barX, barY, barX + BAR_W, barY + barH, 0x66AAAAAA, 0x66AAAAAA);
        int ty = barY + (int) ((barH - thumbH) * (scroll / maxScroll));
        graphics.fillGradient(barX, ty, barX + BAR_W, ty + thumbH, 0xFFDDDDDD, 0xFFDDDDDD);
    }

    // === МЫШЬ ===

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isListMode() || maxScroll <= 0)
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scroll = Mth.clamp(scroll - scrollY, 0, maxScroll);
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (isListMode() && event.button() == 0 && overBar(event.x(), event.y())) {
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
            case LOC -> "Расположение";
            case DEL -> "§cУдалить";
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