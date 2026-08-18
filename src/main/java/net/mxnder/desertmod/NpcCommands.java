package net.mxnder.desertmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.mxnder.desertmod.entity.SimpleNpcEntity;

import java.util.List;

/** Команды управления NPC: /npc remove [радиус] и /npc clear. */
public final class NpcCommands {

    // Бокс «весь мир» для поиска всех NPC:
    // getEntities() в 26.2 protected, поэтому идём через getEntitiesOfClass
    private static final AABB WHOLE_WORLD =
            new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("npc")
                .then(Commands.literal("remove")
                        .executes(ctx -> removeNearest(ctx.getSource(), 8))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> removeNearest(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("clear")
                        .executes(ctx -> removeAll(ctx.getSource()))));
    }

    // Удаляет ближайшего NPC в радиусе — напрямую, в обход урона и брони
    private static int removeNearest(CommandSourceStack source, int radius) {
        if (!allowed(source)) {
            source.sendFailure(Component.literal("§cКоманда доступна в креативе или с консоли"));
            return 0;
        }
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("§cКоманда доступна только игроку"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        List<SimpleNpcEntity> npcs = level.getEntitiesOfClass(
                SimpleNpcEntity.class, player.getBoundingBox().inflate(radius));
        if (npcs.isEmpty()) {
            source.sendFailure(Component.literal(
                    "§7Рядом нет NPC в радиусе " + radius + " блоков"));
            return 0;
        }
        // Ближайший ищется обычным циклом — без лямбд и effectively-final плясок
        SimpleNpcEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (SimpleNpcEntity npc : npcs) {
            double d = npc.distanceToSqr(player);
            if (d < best) {
                best = d;
                nearest = npc;
            }
        }
        if (nearest == null) return 0;
        nearest.discard(); // в старых маппингах может зваться remove()
        source.sendSuccess(() -> Component.literal("§cNPC удалён"), false);
        return 1;
    }

    // Удаляет ВСЕХ NPC в мире — для пересборки композиции беседки
    private static int removeAll(CommandSourceStack source) {
        if (!allowed(source)) {
            source.sendFailure(Component.literal("§cКоманда доступна в креативе или с консоли"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        List<SimpleNpcEntity> npcs = level.getEntitiesOfClass(SimpleNpcEntity.class, WHOLE_WORLD);
        for (SimpleNpcEntity npc : npcs) {
            npc.discard();
        }
        int count = npcs.size();
        source.sendSuccess(() -> Component.literal("§cУдалено NPC: " + count), false);
        return count;
    }

    // Консоль и командный блок (getPlayer() == null) пускаем всегда,
    // игроков — только в креативе: так случайный выживальщик не снесёт беседку.
    // hasPermission в 26.2 у CommandSourceStack поехал, а isCreative() стабилен.
    private static boolean allowed(CommandSourceStack source) {
        Player p = source.getPlayer();
        return p == null || p.isCreative();
    }
}