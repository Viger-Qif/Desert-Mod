package net.mxnder.desertmod.blockentity;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Blocks;
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

    // Счётчики визуальной обратной связи (частицы/звук по уровню заряда).
    // Тоже не персистентные — при заходе в чанк просто начнут отсчёт заново, не страшно.
    private int visualParticleCounter = 0;
    private int visualSoundCounter = 0;
    private int nextVisualParticleDelay = 0;
    private int nextVisualSoundDelay = 0;

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

        // Визуальная обратная связь (частицы/звук) — работает у КАЖДОЙ жаровни
        // независимо от привязки/статуса мастера, каждый серверный тик (throttling
        // внутри самого метода через собственные счётчики с рандомным интервалом).
        be.tickVisuals(serverLevel, pos);

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
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0f, 0.5f);

        // ✅ ЧАСТИЦЫ НА СТАРТЕ: песчаный вихрь
        // Используем BlockParticleOption для эффекта падающего песка
        BlockState sandState = Blocks.SAND.defaultBlockState();
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.FALLING_DUST, sandState),
                player.getX(), player.getY() + 0.5, player.getZ(),
                80,  // больше частиц для плотности
                0.5, 1.5, 0.5,  // широкий столб песка
                0.15  // скорость падения
        );

        // Добавляем магический дым поверх песка
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                player.getX(), player.getY() + 0.3, player.getZ(),
                30, 0.3, 0.8, 0.3, 0.02);

        // Телепортация...
        ServerLevel targetLevel = teleportDimension != null
                ? level.getServer().getLevel(teleportDimension)
                : level;
        if (targetLevel == null) targetLevel = level;

        double x = teleportTarget.getX() + 0.5;
        double y = teleportTarget.getY();
        double z = teleportTarget.getZ() + 0.5;

        player.teleportTo(targetLevel, x, y, z,
                new HashSet<>(), player.getYRot(), player.getXRot(), false);

        // ✅ ЧАСТИЦЫ В ТОЧКЕ НАЗНАЧЕНИЯ: песчаное облако при появлении
        targetLevel.sendParticles(
                new BlockParticleOption(ParticleTypes.FALLING_DUST, sandState),
                x, y + 0.5, z,
                80, 0.5, 1.5, 0.5, 0.15);

        targetLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                x, y + 0.3, z,
                30, 0.3, 0.8, 0.3, 0.02);
    }

    // ---------------------------------------------------------------------
    // Визуальная обратная связь: 4 состояния по заряду.
    // 0 = пусто, 1 = 1-2 (тление), 2 = 3-5 (рабочий режим), 3 = 6-7 (ритуал)
    // ---------------------------------------------------------------------


    private void tickVisuals(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        double bowlY = pos.getY() + 0.65;

        // === ЧАСТИЦЫ: теперь работают на ВСЕХ уровнях включая 0 ===
        visualParticleCounter++;
        if (nextVisualParticleDelay <= 0) {
            nextVisualParticleDelay = rollParticleDelay(level, charge);
        }
        if (visualParticleCounter >= nextVisualParticleDelay) {
            visualParticleCounter = 0;
            nextVisualParticleDelay = rollParticleDelay(level, charge);
            spawnTierParticles(level, charge, x, bowlY, z);
        }

        // === ЗВУК: на нуле тоже есть тихий треск ===
        visualSoundCounter++;
        if (nextVisualSoundDelay <= 0) {
            nextVisualSoundDelay = rollSoundDelay(level, charge);
        }
        if (visualSoundCounter >= nextVisualSoundDelay) {
            visualSoundCounter = 0;
            nextVisualSoundDelay = rollSoundDelay(level, charge);
            playTierSound(level, charge, pos);
        }
    }

    private int rollParticleDelay(ServerLevel level, int charge) {
        return switch (charge) {
            case 0 -> 6 + level.getRandom().nextInt(4);   // 6-9 тиков (тихое тление)
            case 1 -> 5 + level.getRandom().nextInt(3);   // 5-7 тиков
            case 2 -> 4 + level.getRandom().nextInt(3);   // 4-6 тиков
            case 3 -> 3 + level.getRandom().nextInt(2);   // 3-4 тика
            default -> 2 + level.getRandom().nextInt(2);  // 2-3 тика (4+)
        };
    }

    private int rollSoundDelay(ServerLevel level, int charge) {
        return switch (charge) {
            case 0 -> 80 + level.getRandom().nextInt(40);   // 4-6 сек (редкий треск)
            case 1 -> 60 + level.getRandom().nextInt(30);   // 3-4.5 сек
            case 2, 3 -> 40 + level.getRandom().nextInt(20); // 2-3 сек
            default -> 20 + level.getRandom().nextInt(15);   // 1-1.75 сек
        };
    }

    private void spawnTierParticles(ServerLevel level, int charge, double x, double bowlY, double z) {
        var random = level.getRandom();

        // === ПЛАМЯ: количество растёт линейно с зарядом ===
        // Заряд 0: 2 частицы (едва тлеет)
        // Заряд 7: 6 частиц (полноценное пламя)
        int flameCount = 2 + (charge * 2) / 3;

        // Высота столба: 0.08 → 1.41 при заряде 7
        double maxFlameHeight = 0.08 + (charge * 0.19);

        // Ширина спреда позиции: 0.04 → 0.138 при заряде 7
        double spread = 0.04 + (charge * 0.014);

        for (int i = 0; i < flameCount; i++) {
            double dy = random.nextDouble() * maxFlameHeight;
            // Начальное смещение от центра — узкий столб
            double dx = (random.nextDouble() - 0.5) * 2.0 * spread;
            double dz = (random.nextDouble() - 0.5) * 2.0 * spread;
            // Скорость подъёма пламени: растёт с зарядом
            double speed = 0.006 + (charge * 0.002);

            level.sendParticles(ParticleTypes.FLAME,
                    x + dx,       // позиция X
                    bowlY + dy,   // позиция Y (случайная высота внутри столба)
                    z + dz,       // позиция Z
                    1,            // количество за вызов
                    spread * 0.3, // спред X (разброс вокруг точки спавна)
                    0.015,        // спред Y (минимальная вариация высоты)
                    spread * 0.3, // спред Z (разброс вокруг точки спавна)
                    speed);       // скорость движения вверх
        }

        // === ДЫМ SMOKE: серый, строго вверх, нарастающий ===
        // Появляется с заряда 1, шанс растёт от 30% до 58%
        if (charge >= 1) {
            float smokeChance = 0.3f + (charge * 0.04f);
            if (random.nextFloat() < smokeChance) {
                double smokeBaseY = bowlY + maxFlameHeight + 0.03;
                double smokeSpread = 0.02 + (charge * 0.005);

                level.sendParticles(ParticleTypes.SMOKE,
                        x + (random.nextDouble() - 0.5) * 0.08, // позиция X (узкий разброс)
                        smokeBaseY,                              // позиция Y (выше пламени)
                        z + (random.nextDouble() - 0.5) * 0.08, // позиция Z (узкий разброс)
                        1,              // количество за вызов
                        smokeSpread,    // спред X (ширина дымового столба)
                        0.0,            // спред Y = 0 → нет вертикального разброса
                        smokeSpread,    // спред Z (ширина дымового столба)
                        0.015 + (charge * 0.004)); // скорость ВВЕРХ (растёт с зарядом)
            }
        }

        // === ДЫМ WHITE_SMOKE: белый объём, строго вверх, нарастающий ===
        // Появляется с заряда 2, мягче чем LARGE_SMOKE, добавляет светлый слой
        if (charge >= 2) {
            // Шанс: 10% при заряде 2 → 30% при заряде 7
            float whiteChance = 0.07f + (charge * 0.033f);
            if (random.nextFloat() < whiteChance) {
                // Спавнится чуть выше серого дыма
                double whiteY = bowlY + maxFlameHeight + 0.08;
                double whiteSpread = 0.015 + (charge * 0.004);

                level.sendParticles(ParticleTypes.WHITE_SMOKE,
                        x + (random.nextDouble() - 0.5) * 0.06, // позиция X (уже чем SMOKE)
                        whiteY,                                  // позиция Y
                        z + (random.nextDouble() - 0.5) * 0.06, // позиция Z
                        1,           // количество = 1
                        whiteSpread, // спред X (компактный)
                        0.0,         // спред Y = 0 → строго вертикально
                        whiteSpread, // спред Z (компактный)
                        0.012 + (charge * 0.003)); // скорость ВВЕРХ (чуть медленнее серого)
            }
        }

        // === ДЫМ LARGE_SMOKE: крупные клубы, строго вверх, прям чуть-чуть ===
        // Появляется с заряда 3, очень редкий, создаёт объёмные акценты
        if (charge >= 3) {
            // Шанс: 5% при заряде 3 → 19% при заряде 7 (редкие акценты)
            float largeChance = 0.03f + (charge * 0.023f);
            if (random.nextFloat() < largeChance) {
                // Спавнится выше всех остальных слоёв дыма
                double largeY = bowlY + maxFlameHeight + 0.15;

                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        x + (random.nextDouble() - 0.5) * 0.04, // позиция X (почти точка)
                        largeY,                                  // позиция Y (верхушка столба)
                        z + (random.nextDouble() - 0.5) * 0.04, // позиция Z (почти точка)
                        1,     // количество = 1
                        0.02,  // спред X ≈ минимальный (не гигантское пятно)
                        0.0,   // спред Y = 0 → строго вертикально
                        0.02,  // спред Z ≈ минимальный
                        0.008 + (charge * 0.002)); // скорость ВВЕРХ (медленная, парит)
            }
        }

        // === ДЫМ CAMPFIRE_COSY_SMOKE: белый шлейф, СТРОГО ВВЕРХ на 1-2 блока ===
        // Появляется с заряда 2, тонкий верхний слой над всеми остальными
        if (charge >= 5) {
            // Шанс: 8% при заряде 2 → 22% при заряде 7
            float cosyChance = 0.05f + (charge * 0.01f);
            if (random.nextFloat() < cosyChance) {
                // Спавнится ВЫШЕ всех остальных слоёв
                double cosyY = bowlY + maxFlameHeight + 0.25;

                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        x + (random.nextDouble() - 0.5) * 0.03, // позиция X (точка)
                        cosyY + 0.5,                                   // позиция Y (самый верх)
                        z + (random.nextDouble() - 0.5) * 0.03, // позиция Z (точка)
                        1,     // количество = 1
                        0.01,  // спред X ≈ 0 → почти точка
                        1,   // ⚠️ ИСПРАВЛЕНО: было 2.0 (огромный разброс!)
                        // Теперь 0.0 → нет вертикального разброса позиций
                        0.01,  // спред Z ≈ 0 → почти точка
                        0.007); // скорость ВВЕРХ
                // При speed=0.04 частица пролетает ~0.8 блоков/сек
                // При speed=0.096 (заряд 7) — ~1.9 блоков/сек
                // Время жизни COSY_SMOKE ≈ 20 тиков (1 сек)
                // Итого: пролетает 0.8–1.9 блоков вверх, затем исчезает
                // Без бокового сноса, потому что спред X/Z = 0.01
            }
        }

        // === ЛАВА: только на 4+, одна точка, шанс 10% ===
        if (charge >= 4 && random.nextFloat() < 0.1f) {
            double angle = random.nextFloat() * (float) Math.PI * 2;
            double ex = x + Math.cos(angle) * 0.35;
            double ez = z + Math.sin(angle) * 0.35;

            level.sendParticles(ParticleTypes.LAVA,
                    ex,              // позиция X (на краю чаши)
                    bowlY + 0.05,    // позиция Y (чуть выше дна)
                    ez,              // позиция Z (на краю чаши)
                    1,               // количество = 1
                    0, 0, 0,         // спред X/Y/Z = 0 → точечная искра
                    0);              // скорость = 0 → стоит на месте
        }
    }


    private void playTierSound(ServerLevel level, int charge, BlockPos pos) {
        var random = level.getRandom();
        if (charge <= 0) {
            // Заряд 0: тишина или очень редкий тихий треск остывающих углей
            if (random.nextFloat() < 0.1f) {
                level.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                        0.15f, 0.5f + random.nextFloat() * 0.1f);
            }
            return;
        }
        if (charge <= 3) {
            // Заряды 1-3: спокойный треск костра, громкость растёт с зарядом
            float volume = 0.3f + (charge * 0.1f);
            float pitch = 0.7f + random.nextFloat() * 0.2f;
            level.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    volume, pitch);
        } else if (charge <= 5) {
            // Заряды 4-5: уверенный треск + редкий низкий гул
            level.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.7f, 0.9f + random.nextFloat() * 0.1f);
            if (random.nextFloat() < 0.2f) {
                level.playSound(null, pos, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                        0.4f, 0.8f);
            }
        } else {
            // Заряды 6-7: ритуальный гул + интенсивный треск
            level.playSound(null, pos, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                    1.0f, 0.85f + random.nextFloat() * 0.1f);
            level.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.9f, 1.0f);
        }
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