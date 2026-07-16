package net.mxnder.desertmod.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.mxnder.desertmod.blockentity.KifiBrazierBlockEntity;

public class ModCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("desertmod")
                    .then(Commands.literal("settp")
                            .then(Commands.argument("x", IntegerArgumentType.integer())
                                    .then(Commands.argument("y", IntegerArgumentType.integer())
                                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                                    .executes(context -> {
                                                        CommandSourceStack source = context.getSource();
                                                        if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                            source.sendFailure(Component.literal("§cЭту команду может выполнить только игрок!"));
                                                            return 0;
                                                        }

                                                        int x = IntegerArgumentType.getInteger(context, "x");
                                                        int y = IntegerArgumentType.getInteger(context, "y");
                                                        int z = IntegerArgumentType.getInteger(context, "z");

                                                        BlockPos playerPos = player.blockPosition();
                                                        KifiBrazierBlockEntity master = null;

                                                        // Увеличенный радиус — ищем ЛЮБУЮ жаровню группы
                                                        int SEARCH_RADIUS = 20;

                                                        outer:
                                                        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                                                            for (int dy = -3; dy <= 3; dy++) {
                                                                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                                                                    BlockPos checkPos = playerPos.offset(dx, dy, dz);
                                                                    if (player.level().getBlockEntity(checkPos) instanceof KifiBrazierBlockEntity be) {
                                                                        if (be.isLinked() && be.isMaster()) {
                                                                            master = be;
                                                                            break outer;
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // ✅ ОТЛАДКА: покажи, что нашли
                                                        player.sendSystemMessage(Component.literal(
                                                                "§7[DEBUG] Поиск мастера завершён. Найдено: " + (master != null ? "ДА" : "НЕТ")
                                                        ));

                                                        if (master != null) {
                                                            player.sendSystemMessage(Component.literal(
                                                                    "§7[DEBUG] Мастер на: " + master.getBlockPos() +
                                                                            ", linked=" + master.isLinked() +
                                                                            ", master=" + master.isMaster()
                                                            ));
                                                        }

                                                        if (master != null) {
                                                            master.setTeleportTarget(new BlockPos(x, y, z), player.level().dimension());
                                                            player.sendSystemMessage(Component.literal(
                                                                    "§a✓ Точка телепорта установлена: X=" + x + ", Y=" + y + ", Z=" + z
                                                            ));
                                                            return 1;
                                                        } else {
                                                            player.sendSystemMessage(Component.literal(
                                                                    "§c✗ Рядом с вами (в радиусе 20 блоков) не найдена Мастер-жаровня.\n" +
                                                                            "§7Убедитесь, что вы привязали 4 жаровни (Shift+ПКМ) и стоите рядом с конструкцией."
                                                            ));
                                                            return 0;
                                                        }
                                                    })
                                            )
                                    )
                            )
                    )
            );
        });
    }
}