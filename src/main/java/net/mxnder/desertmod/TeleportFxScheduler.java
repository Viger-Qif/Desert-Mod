package net.mxnder.desertmod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.mxnder.desertmod.blockentity.KifiBrazierBlockEntity;

/**
 * Глобальный серверный тикер очереди отложенных эффектов появления.
 *
 * Зачем он вообще нужен: после дальнего телепорта чанк со стартовой
 * жаровней выгружается, и её serverTick перестаёт вызываться — таймер,
 * живущий внутри блок-сущности, завис бы навсегда и эффект появления
 * не сработал бы. Поэтому очередь вынесена в статику блок-сущности
 * (см. PENDING_ARRIVALS / tickPending), а тикаем мы её отсюда —
 * от глобального серверного тика, который идёт всегда, пока запущен
 * сервер, независимо от того, загружены ли чанки с жаровнями.
 *
 * Под Fabric нет аннотаций-подписок (это придумка NeoForge/Forge).
 * Здесь события — это callback-реестры: мы вручную подписываемся на
 * END_SERVER_TICK методом register(), а сам register() вызываем один
 * раз из главного класса мода, в onInitialize.
 */
public final class TeleportFxScheduler {

    // Конструктор закрыт: класс живёт только статикой, экземпляры не нужны.
    private TeleportFxScheduler() {
    }

    /**
     * Подписка на глобальный серверный тик.
     * Вызвать ОДИН раз из ModInitializer.onInitialize главного класса мода.
     */
    public static void register() {
        // END_SERVER_TICK = конец каждого тика всего сервера целиком.
        // Лямбда получает MinecraftServer — из него tickPending сам достанет
        // нужный уровень через server.getLevel(...). Срабатывает всегда,
        // пока сервер жив, даже если все жаровни выгружены.
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            KifiBrazierBlockEntity.tickPending(server);
        });
    }
}