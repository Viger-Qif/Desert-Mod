package net.mxnder.desertmod.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.mxnder.desertmod.NpcSkinChatHandler;
import net.mxnder.desertmod.NpcSkins;

public class SimpleNpcEntity extends PathfinderMob implements GeoEntity {

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // Idle-анимация. ⚠ Имя "idle" должно совпадать с ключом в simple_npc.animation.json
    // (открой json и проверь, как называется анимация внутри "animations": { ... })
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    public SimpleNpcEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        // Не деспавнится, даже если игрок ушёл за тысячи блоков
        this.setPersistenceRequired();
        // НЕ ставим setInvulnerable(true) — оно в 26.2 блокирует и /kill.
        // Бессмертие обеспечивается событием ALLOW_DAMAGE ниже.
    }

    /** Вызвать один раз из DesertMod.onInitialize(). */
    public static void registerEvents() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof SimpleNpcEntity)) return true;
            // Только /kill (genericKill) может убить NPC.
            // Всё остальное — мечи, взрывы, огонь, падение, пустота — blocked.
            return source == entity.level().damageSources().genericKill();
        });
    }

    // === АНИМАЦИЯ ===

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    // === ИИ: сейчас нет целей, но можно добавить позже для pathfinding ===

    @Override
    protected void registerGoals() {
        // Пусто: NPC стоит на месте.
        // Когда понадобится pathfinding к точке, добавь сюда цели, например:
        // this.goalSelector.addGoal(1, new MoveToBlockGoal(this, targetPos, 1.0));
        // PathfinderMob уже имеет все нужные методы для навигации.
    }

    // === ДИАЛОГ: ПКМ по NPC ===

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide() && player instanceof ServerPlayer sp) {
            var names = NpcSkins.listAll(sp.level().getServer().getResourceManager());
            if (names.isEmpty()) {
                player.sendSystemMessage(Component.literal("§7Папка скинов пуста: config/desertmod/skins/"));
            } else {
                player.sendSystemMessage(Component.literal("§6Доступные скины: §f" + String.join(", ", names)));
                player.sendSystemMessage(Component.literal("§7Напиши имя в чат — этот NPC переоденется."));
                NpcSkinChatHandler.openSelection(sp.getUUID(), this.getUUID());
            }
        }
        return InteractionResult.SUCCESS;
    }

    // === СКИН: синхронизируемое поле ===

    private static final EntityDataAccessor<String> DATA_SKIN =
            SynchedEntityData.defineId(SimpleNpcEntity.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, "");
    }

    public String getSkinName() {
        return this.entityData.get(DATA_SKIN);
    }

    public void setSkinName(String name) {
        this.entityData.set(DATA_SKIN, name);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Skin", getSkinName());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setSkinName(input.getStringOr("Skin", ""));
    }

    // === ОТКЛЮЧЁННЫЕ ФУНКЦИИ ===

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return source != level.damageSources().genericKill();
    }
}