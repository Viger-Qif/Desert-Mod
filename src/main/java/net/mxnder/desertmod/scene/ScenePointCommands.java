package net.mxnder.desertmod.scene;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Сценические команды под общей крышей /desertmod:
 *  /desertmod scenelist            — все точки из scene_points.json со всеми данными
 *  /desertmod sceneedit info|move|set|yaw — точная доводка ближайшей точки. */
public final class ScenePointCommands {

    private static final double EDIT_RADIUS = 3.0; // не обязательно стоять ровно на точке

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("desertmod")
                        .requires(src -> src.getPlayer() != null)
                        // /desertmod scenelist — список всех точек
                        .then(Commands.literal("scenelist")
                                .executes(ScenePointCommands::list))
                        // /desertmod sceneedit ... — правка ближайшей точки
                        .then(Commands.literal("sceneedit")
                                .then(Commands.literal("info")
                                        .executes(ScenePointCommands::info))
                                .then(Commands.literal("move")
                                        .then(Commands.argument("dx", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("dy", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("dz", DoubleArgumentType.doubleArg())
                                                                .executes(ctx -> move(ctx,
                                                                        DoubleArgumentType.getDouble(ctx, "dx"),
                                                                        DoubleArgumentType.getDouble(ctx, "dy"),
                                                                        DoubleArgumentType.getDouble(ctx, "dz")))))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                .executes(ctx -> set(ctx,
                                                                        DoubleArgumentType.getDouble(ctx, "x"),
                                                                        DoubleArgumentType.getDouble(ctx, "y"),
                                                                        DoubleArgumentType.getDouble(ctx, "z")))))))
                                .then(Commands.literal("yaw")
                                        .then(Commands.argument("yaw", DoubleArgumentType.doubleArg())
                                                .executes(ctx -> yaw(ctx,
                                                        (float) DoubleArgumentType.getDouble(ctx, "yaw"))))))));
    }

    // === /desertmod scenelist ===
    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        var all = ScenePoints.all();
        if (all.isEmpty()) {
            p.sendSystemMessage(Component.literal("§7Точек сцен нет — создай клавишей H"));
            return 0;
        }
        p.sendSystemMessage(Component.literal("§eТочки сцен (" + all.size() + "):"));
        for (ScenePoints.ScenePoint pt : all) {
            p.sendSystemMessage(Component.literal(String.format(
                    "§f%s §7| §f%.3f %.3f %.3f §7| yaw §f%.1f §7| %s §7| сцена %s",
                    pt.id().toString().substring(0, 8), // короткий id, чтобы строка не расползалась
                    pt.x(), pt.y(), pt.z(),
                    pt.yaw(),
                    pt.dim().replace("minecraft:", ""),
                    pt.scene())));
        }
        return Command.SINGLE_SUCCESS;
    }

    // === /desertmod sceneedit ... ===
    private static int info(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        var pt = ScenePoints.nearestForPlayer(p, EDIT_RADIUS);
        if (pt == null) {
            p.sendSystemMessage(Component.literal("§7Рядом нет точки сцены"));
            return 0;
        }
        p.sendSystemMessage(Component.literal(String.format(
                "§eТочка: %.3f %.3f %.3f | yaw %.1f | сцена %s",
                pt.x(), pt.y(), pt.z(), pt.yaw(), pt.scene())));
        return Command.SINGLE_SUCCESS;
    }

    private static int move(CommandContext<CommandSourceStack> ctx, double dx, double dy, double dz) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        var upd = ScenePoints.moveNearest(p.getX(), p.getY(), p.getZ(),
                p.level().dimension().identifier().toString(), EDIT_RADIUS, dx, dy, dz);
        return feedback(p, upd);
    }

    private static int set(CommandContext<CommandSourceStack> ctx, double x, double y, double z) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        var upd = ScenePoints.setNearest(p.getX(), p.getY(), p.getZ(),
                p.level().dimension().identifier().toString(), EDIT_RADIUS, x, y, z, null);
        return feedback(p, upd);
    }

    private static int yaw(CommandContext<CommandSourceStack> ctx, float yaw) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) return 0;
        var upd = ScenePoints.setNearest(p.getX(), p.getY(), p.getZ(),
                p.level().dimension().identifier().toString(), EDIT_RADIUS,
                null, null, null, yaw);
        return feedback(p, upd);
    }

    private static int feedback(ServerPlayer p, ScenePoints.ScenePoint upd) {
        if (upd == null) {
            p.sendSystemMessage(Component.literal("§7Рядом нет точки сцены"));
            return 0;
        }
        ScenePoints.syncAll(p.level().getServer());
        p.sendSystemMessage(Component.literal(String.format(
                "§aТочка: %.3f %.3f %.3f | yaw %.1f",
                upd.x(), upd.y(), upd.z(), upd.yaw())));
        return Command.SINGLE_SUCCESS;
    }
}