package net.mxnder.desertmod.blockentity;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Хранит всю логику жаровни:
 * - заряд (0-7)
 * - привязка к 3 соседям + координата центра креста
 * - координата телепорта (только для мастера)
 * - тикер раз в 20 тиков, таймер активации на 3 секунды (60 тиков)
 *
 * Никакого постоянного сканирования мира: серверный тик проверяет только
 * заранее сохранённые в NBT координаты (центр + 3 соседа).
 * Поиск по радиусу происходит один раз, только в момент привязки (Shift+ПКМ).
 */
public class KifiBrazierBlockEntity extends BlockEntity {

    private static final int MAX_CHARGE = 7;
    private static final int SEARCH_RADIUS = 15;
    private static final int DISTANCE_FROM_CENTER = 7; // расстояние от центра до жаровни
    private static final int TICK_INTERVAL = 20;       // раз в 1 секунду
    private static final int TELEPORT_DELAY_TICKS = 140; // 7 секунд (140 тиков / 20 тиков в секунду)

    // ⚠ ПРОВЕРИТЬ: кодек для ResourceKey<Level> из ключа реестра измерений.
    // ResourceKey.codec(...) уже был в ванильном API — если тоже переименован, скажите.
    private static final Codec<ResourceKey<Level>> DIMENSION_CODEC = ResourceKey.codec(Registries.DIMENSION);

    private int charge = 0;
    private boolean linked = false;
    private boolean master = false;

    // Координата обычного блока в центре креста (игрок стоит НАД ним)
    private BlockPos centerPos = null;

    // Координаты трёх других жаровен группы (не включая себя)
    private final BlockPos[] neighborPositions = new BlockPos[3];

    // Точка назначения телепорта (задаётся только у мастера)
    private BlockPos teleportTarget = null;
    private ResourceKey<Level> teleportDimension = null;

    // Не персистентные — сбрасываются при перезагрузке чанка, это ок
    private int tickCounter = 0;
    private int activationTimer = 0;



    public KifiBrazierBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KIFI_BRAZIER_BLOCK_ENTITY, pos, state);
    }


    // ---------------------------------------------------------------------
    // Геттеры / базовые сеттеры
    // ---------------------------------------------------------------------

    public boolean isLinked() {
        return linked;
    }

    public boolean isMaster() {
        return master;
    }

    public int getCharge() {
        return charge;
    }

    public void setCharge(int value) {
        this.charge = Math.max(0, Math.min(MAX_CHARGE, value));
        setChanged();
    }

    // ---------------------------------------------------------------------
    // Привязка (Shift+ПКМ по любой ещё не привязанной жаровне)
    // ---------------------------------------------------------------------

    /**
     * Ищет ровно 3 других жаровни в радиусе SEARCH_RADIUS и проверяет,
     * что все 4 (включая себя) стоят строго крестом (С/Ю/В/З) вокруг
     * общего центрального блока на одной высоте.
     * Выполняется ОДИН РАЗ по действию игрока — не в тикере.
     */
    public boolean tryBind(ServerLevel level) {
        BlockPos self = this.getBlockPos();
        List<BlockPos> found = new ArrayList<>();
        found.add(self);

        // Ищем ЛЮБЫЕ другие жаровни в радиусе, даже если они уже linked
        BlockPos.betweenClosedStream(
                self.offset(-SEARCH_RADIUS, -2, -SEARCH_RADIUS),
                self.offset(SEARCH_RADIUS, 2, SEARCH_RADIUS)
        ).forEach(pos -> {
            if (!pos.equals(self) && level.getBlockEntity(pos) instanceof KifiBrazierBlockEntity other) {
                found.add(pos.immutable());
            }
        });

        // ✅ ОТЛАДКА: покажи, что нашли
        if (!level.isClientSide()) {
            net.minecraft.server.level.ServerPlayer nearestPlayer = level.players().stream()
                    .min(Comparator.comparingDouble(p -> p.distanceToSqr(
                            self.getX() + 0.5,
                            self.getY() + 0.5,
                            self.getZ() + 0.5
                    )))
                    .orElse(null);

            if (nearestPlayer != null) {
                nearestPlayer.sendSystemMessage(Component.literal(
                        "§7[DEBUG] Найдено жаровен: " + found.size() +
                                ". Координаты: " + found
                ));
            }
        }

        if (found.size() != 4) {
            return false; // Нашли не 4 блока
        }

        int minX = found.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxX = found.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minZ = found.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxZ = found.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        int y = found.get(0).getY();

        int expectedSize = DISTANCE_FROM_CENTER * 2; // 14
        if (maxX - minX != expectedSize || maxZ - minZ != expectedSize) {
            return false; // Не крест нужного размера
        }
        if (found.stream().anyMatch(p -> p.getY() != y)) {
            return false; // Не на одной высоте
        }

        BlockPos center = new BlockPos(minX + DISTANCE_FROM_CENTER, y, minZ + DISTANCE_FROM_CENTER);
        BlockPos north = center.offset(0, 0, -DISTANCE_FROM_CENTER);
        BlockPos south = center.offset(0, 0, DISTANCE_FROM_CENTER);
        BlockPos east = center.offset(DISTANCE_FROM_CENTER, 0, 0);
        BlockPos west = center.offset(-DISTANCE_FROM_CENTER, 0, 0);

        Set<BlockPos> expected = new HashSet<>(List.of(north, south, east, west));
        Set<BlockPos> actual = new HashSet<>(found);

        if (!actual.equals(expected)) {
            return false; // Геометрия не совпадает
        }

        // Всё верно — ПЕРЕЗАПИСЫВАЕМ связь для всей группы
        for (BlockPos p : found) {
            if (level.getBlockEntity(p) instanceof KifiBrazierBlockEntity be) {
                be.linked = true;
                be.master = p.equals(north);
                be.centerPos = center;

                List<BlockPos> others = new ArrayList<>(found);
                others.remove(p);
                be.neighborPositions[0] = others.get(0);
                be.neighborPositions[1] = others.get(1);
                be.neighborPositions[2] = others.get(2);

                be.setChanged();
                level.sendBlockUpdated(p, be.getBlockState(), be.getBlockState(), 3);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Задание точки назначения (Shift+ПКМ пустой рукой по Мастеру)
    // ---------------------------------------------------------------------

    public void setTeleportTarget(BlockPos target, ResourceKey<Level> dimension) {
        if (!master) {
            return;
        }
        this.teleportTarget = target.immutable();
        this.teleportDimension = dimension;
        setChanged();
    }

    // ---------------------------------------------------------------------
    // Тикер — вызывается каждый игровой тик движком, но реальная логика
    // выполняется раз в TICK_INTERVAL (20) тиков, и только у мастера.
    // ---------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, KifiBrazierBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        be.tickCounter++;
        if (be.tickCounter < TICK_INTERVAL) {
            return;
        }
        be.tickCounter = 0;

        if (!be.master || !be.linked || be.centerPos == null || be.teleportTarget == null) {
            be.activationTimer = 0;
            return;
        }

        // Заряд самой жаровни-мастера
        if (be.charge <= 0) {
            be.activationTimer = 0;
            return;
        }

        // Заряд трёх соседей — читаем только по сохранённым координатам, без сканирования
        for (BlockPos neighborPos : be.neighborPositions) {
            if (neighborPos == null) {
                be.activationTimer = 0;
                return;
            }
            if (!(serverLevel.getBlockEntity(neighborPos) instanceof KifiBrazierBlockEntity neighbor)
                    || neighbor.getCharge() <= 0) {
                be.activationTimer = 0;
                return;
            }
        }

        // Стоит ли игрок ровно на центральном блоке
        AABB standingBox = new AABB(be.centerPos.above());
        List<Player> players = serverLevel.getEntitiesOfClass(Player.class, standingBox);

        if (players.isEmpty()) {
            be.activationTimer = 0;
            return;
        }

        be.activationTimer += TICK_INTERVAL;

        if (be.activationTimer >= TELEPORT_DELAY_TICKS) {
            be.activationTimer = 0;

            // ✅ ДОБАВЛЕНО: Списываем заряд со всех 4 жаровен ОДИН раз
            be.consumeCharge(serverLevel);

            for (Player player : players) {
                if (player instanceof ServerPlayer serverPlayer) {
                    be.teleportPlayer(serverLevel, serverPlayer);
                }
            }
        }
    }

    private void teleportPlayer(ServerLevel level, ServerPlayer player) {

        // Звук телепортации — замедленный эндермен
        level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);

        // Частицы на старте
        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.4, 0.6, 0.4, 0.02);

        ServerLevel targetLevel = teleportDimension != null
                ? level.getServer().getLevel(teleportDimension)
                : level;

        if (targetLevel == null) {
            targetLevel = level;
        }

        double x = teleportTarget.getX() + 0.5;
        double y = teleportTarget.getY();
        double z = teleportTarget.getZ() + 0.5;

        // Для NeoForge 26.2 добавлен boolean параметр в конце
        player.teleportTo(
                targetLevel,
                x, y, z,
                new HashSet<>(),  // Set<Relative>
                player.getYRot(),
                player.getXRot(),
                false  // <-- последний параметр (сброс камеры)
        );

        targetLevel.sendParticles(ParticleTypes.POOF,
                x, y + 1.0, z,
                40, 0.4, 0.6, 0.4, 0.02);
    }

    /**
     * Списывает по 1 заряду с мастера и всех 3 соседей.
     * Вызывается ровно один раз за цикл активации.
     */
    private void consumeCharge(ServerLevel level) {
        // 1. Списываем заряд с мастера
        this.setCharge(this.getCharge() - 1);
        level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);

        // 2. Списываем заряд с 3 соседей по сохранённым координатам
        for (BlockPos neighborPos : this.neighborPositions) {
            if (neighborPos != null && level.getBlockEntity(neighborPos) instanceof KifiBrazierBlockEntity neighbor) {
                neighbor.setCharge(neighbor.getCharge() - 1);
                level.sendBlockUpdated(neighborPos, neighbor.getBlockState(), neighbor.getBlockState(), 3);
            }
        }
    }

    // ---------------------------------------------------------------------
    // NBT
    // В вашей версии CompoundTag.get* возвращает Optional<T>, а не примитив,
    // а запись/чтение сложных типов (BlockPos, ResourceKey) идёт через кодеки
    // методами tag.store(key, Codec, value) / tag.read(key, Codec).
    // ⚠ ПРОВЕРИТЬ: если store/read/getIntOr/getBooleanOr/getStringOr называются
    // иначе в вашей версии — Ctrl+клик на CompoundTag и пришлите мне сигнатуры,
    // которые реально есть (список методов на *Or и store/read).
    // ---------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        output.putInt("Charge", charge);
        output.putBoolean("Linked", linked);
        output.putBoolean("Master", master);

        if (centerPos != null) {
            output.store("CenterPos", BlockPos.CODEC, centerPos);
        }

        if (linked) {
            List<BlockPos> neighbors = List.of(neighborPositions[0], neighborPositions[1], neighborPositions[2]);
            output.store("Neighbors", BlockPos.CODEC.listOf(), neighbors);
        }

        if (teleportTarget != null) {
            output.store("TeleportTarget", BlockPos.CODEC, teleportTarget);
        }
        if (teleportDimension != null) {
            output.store("TeleportDimension", DIMENSION_CODEC, teleportDimension);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        charge = input.getIntOr("Charge", 0);
        linked = input.getBooleanOr("Linked", false);
        master = input.getBooleanOr("Master", false);

        centerPos = input.read("CenterPos", BlockPos.CODEC).orElse(null);

        neighborPositions[0] = null;
        neighborPositions[1] = null;
        neighborPositions[2] = null;

        input.read("Neighbors", BlockPos.CODEC.listOf()).ifPresent(list -> {
            for (int i = 0; i < Math.min(3, list.size()); i++) {
                neighborPositions[i] = list.get(i);
            }
        });

        teleportTarget = input.read("TeleportTarget", BlockPos.CODEC).orElse(null);
        teleportDimension = input.read("TeleportDimension", DIMENSION_CODEC).orElse(null);
    }
}