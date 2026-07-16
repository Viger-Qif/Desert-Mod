package net.mxnder.desertmod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.mxnder.desertmod.blockentity.KifiBrazierBlockEntity;
import net.mxnder.desertmod.blockentity.ModBlockEntities;
import net.mxnder.desertmod.item.ModItems;
import org.jetbrains.annotations.Nullable;

public class KifiBrazierBlock extends BaseEntityBlock {
    public static final MapCodec<KifiBrazierBlock> CODEC = simpleCodec(KifiBrazierBlock::new);

    public KifiBrazierBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KifiBrazierBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.KIFI_BRAZIER_BLOCK_ENTITY, KifiBrazierBlockEntity::serverTick);
    }



    // === НАСТРОЙКА ХИТБОКСА ДЛЯ СЛОЖНОЙ ФИГУРЫ ===
    // Формат: Shapes.box(minX, minY, minZ, maxX, maxY, maxZ)
    // Значения от 0.0 до 16.0.
    // Пример для жаровни: чуть уже полного блока (2 пикселя с краёв) и высотой 10 пикселей.
    private static final VoxelShape BRAZIER_SHAPE = Shapes.box(0, 0, 0, 1.0, 0.685, 1.0);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BRAZIER_SHAPE; // Определяет, куда попадает луч мыши (подсветка)
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BRAZIER_SHAPE; // Определяет физическую коллизию (нельзя пройти сквозь)
    }



    // ==========================================================
    // КЛИК ПУСТОЙ РУКОЙ
    // ==========================================================
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof KifiBrazierBlockEntity brazier)) return InteractionResult.PASS;

        net.minecraft.server.level.ServerPlayer serverPlayer = (net.minecraft.server.level.ServerPlayer) player;

        // Shift+ПКМ → перепривязка + инструкция
        if (player.isShiftKeyDown()) {
            boolean success = brazier.tryBind((net.minecraft.server.level.ServerLevel) level);
            // Звук привязки — зачаровывание
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.8f);
            if (success) {
                serverPlayer.sendSystemMessage(Component.literal(
                        "§a✓ Жаровни перепривязаны.\n" +
                                "§eУстановить точку телепорта: §b/desertmod settp <X> <Y> <Z>\n" +
                                "§7Пример: §b/desertmod settp 5000 70 -3000"
                ));
            } else {
                serverPlayer.sendSystemMessage(Component.literal(
                        "§c✗ Ошибка привязки!\n" +
                                "§7Проверьте:\n" +
                                "• Ровно 4 жаровни рядом\n" +
                                "• Расстояние между ними 14 блоков (7 в каждую сторону)\n" +
                                "• Все на одной высоте (Y)\n" +
                                "• Образуют крест (С/Ю/В/З)"
                ));
            }
            return InteractionResult.CONSUME;
        }

        // Обычный ПКМ → заряд
        serverPlayer.sendOverlayMessage(Component.literal("Заряд жаровни: " + brazier.getCharge() + "/7"));
        return InteractionResult.CONSUME;
    }

    // ==========================================================
    // КЛИК С ПРЕДМЕТОМ В РУКЕ
    // ==========================================================
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof KifiBrazierBlockEntity brazier)) return InteractionResult.PASS;

        // ✅ ВАЖНО: объявляем serverPlayer здесь тоже
        net.minecraft.server.level.ServerPlayer serverPlayer = (net.minecraft.server.level.ServerPlayer) player;

        // KIFI в руке → зарядить
        if (stack.is(ModItems.KIFI)) {
            int charge = brazier.getCharge();
            if (charge >= 7) {
                serverPlayer.sendOverlayMessage(Component.literal("Жаровня уже полностью заряжена (7/7)"));
                return InteractionResult.FAIL;
            }
            brazier.setCharge(charge + 1);

            // Звук зарядки — огонь
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRE_AMBIENT,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.2f);

            stack.shrink(1);
            level.sendBlockUpdated(pos, state, state, 3);
            serverPlayer.sendOverlayMessage(Component.literal("Заряд жаровни: " + brazier.getCharge() + "/7"));
            return InteractionResult.CONSUME;
        }

        // Любой другой предмет — та же логика, что и с пустой рукой
        if (player.isShiftKeyDown()) {
            brazier.tryBind((net.minecraft.server.level.ServerLevel) level);
            serverPlayer.sendSystemMessage(Component.literal(
                    "§aЖаровни перепривязаны.\n" +
                            "§eУстановить точку телепорта: §b/desertmod settp <X> <Y> <Z>\n" +
                            "§7Пример: §b/desertmod settp 5000 70 -3000"
            ));
            return InteractionResult.CONSUME;
        }

        serverPlayer.sendOverlayMessage(Component.literal("Заряд жаровни: " + brazier.getCharge() + "/7"));
        return InteractionResult.CONSUME;
    }
}