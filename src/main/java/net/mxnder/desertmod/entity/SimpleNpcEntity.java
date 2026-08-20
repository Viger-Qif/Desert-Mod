package net.mxnder.desertmod.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
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
import net.mxnder.desertmod.NpcSkins;
import net.mxnder.desertmod.network.NpcSkinPayloads;

import java.util.ArrayList;
import java.util.List;

/**
 * NPC-статуя для арт-беседки: не двигается, не умирает ни от чего, кроме /kill,
 * редактируется через окно (скин + анимация), в будущем пойдёт по пути к точкам.
 */
public class SimpleNpcEntity extends PathfinderMob implements GeoEntity {

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // Кэш текущей RawAnimation: пересобирается только при смене имени
    private String cachedAnimName;
    private RawAnimation cachedAnim;

    public SimpleNpcEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        // Не деспавнится: NPC — часть мира, а не случайный моб
        this.setPersistenceRequired();
        // НЕ ставим setInvulnerable(true) — неуязвимость держим через
        // isInvulnerableTo ниже, иначе в 26.2 ломается /kill.
    }

    /** Вызвать один раз из DesertMod.onInitialize(). */
    public static void registerEvents() {
        // Второй слой брони: даже если урон дошёл до конвейера,
        // пропускаем только /kill, всё остальное гасим.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof SimpleNpcEntity)) return true; // чужих не трогаем
            return source == entity.level().damageSources().genericKill();
        });
    }

    // === БРОНЯ: умирает только от /kill ===

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        // true = блокировать. Единственная «дырка» — команда /kill
        return source != level.damageSources().genericKill();
    }

    // === АНИМАЦИЯ: имя хранится в сущности, контроллер берёт его отсюда ===

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(state ->
                state.setAndContinue(currentAnim())));
    }

    private RawAnimation currentAnim() {
        String name = getAnimName();
        if (name == null || name.isEmpty()) name = "idle";
        if (!name.equals(cachedAnimName)) {
            cachedAnimName = name;
            cachedAnim = RawAnimation.begin().thenLoop(name);
        }
        return cachedAnim;
    }

    // === ПКМ: открываем редактор на клиенте ===

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide() && player instanceof ServerPlayer sp) {
            var server = sp.level().getServer();
            ServerPlayNetworking.send(sp, new NpcSkinPayloads.OpenEditor(
                    this.getUUID().toString(),
                    new ArrayList<>(NpcSkins.listAll(server.getResourceManager())),
                    listAnimations(server),
                    getSkinName(),
                    getAnimName()));
        }
        return InteractionResult.SUCCESS;
    }

    private static final Identifier ANIM_FILE = Identifier.fromNamespaceAndPath(
            "desertmod", "geckolib/animations/entity/simple_npc.animation.json");

    /** Имена анимаций — ключи "animations" из json модели.
     *  Добавишь анимацию в Blockbench — она сама появится в редакторе. */
    public static List<String> listAnimations(MinecraftServer server) {
        var res = server.getResourceManager().getResource(ANIM_FILE);
        if (res.isEmpty()) return List.of();
        try (var reader = res.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject anims = root.has("animations") ? root.getAsJsonObject("animations") : null;
            return anims == null ? List.of() : new ArrayList<>(anims.keySet());
        } catch (Exception e) {
            return List.of();
        }
    }

    // === ДАННЫЕ: скин и анимация, синхронизируются и сохраняются ===

    private static final EntityDataAccessor<String> DATA_SKIN =
            SynchedEntityData.defineId(SimpleNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_ANIM =
            SynchedEntityData.defineId(SimpleNpcEntity.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, "");
        builder.define(DATA_ANIM, "idle");
    }

    public String getSkinName() {
        return this.entityData.get(DATA_SKIN);
    }

    public void setSkinName(String name) {
        this.entityData.set(DATA_SKIN, name);
    }

    public String getAnimName() {
        return this.entityData.get(DATA_ANIM);
    }

    public void setAnimName(String name) {
        this.entityData.set(DATA_ANIM, name);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Skin", getSkinName());
        output.putString("Anim", getAnimName());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setSkinName(input.getStringOr("Skin", ""));
        setAnimName(input.getStringOr("Anim", "idle"));
    }

    // === ИИ: целей пока нет, но движок жив для будущего pathfinding ===

    @Override
    protected void registerGoals() {
        // Пусто: NPC стоит на месте.
        // Когда поведём его к точкам — сюда встанут цели навигации.
    }

    // === ОТКЛЮЧЁННАЯ «ЛИШНЯЯ» ЖИЗНЬ МОБА ===

    @Override
    public boolean fireImmune() {
        return true; // не горит
    }

    @Override
    public boolean isAffectedByPotions() {
        return false; // зелья не действуют
    }

    @Override
    public boolean isPushable() {
        return false; // не толкается игроками и мобами
    }

    @Override
    public boolean isPushedByFluid() {
        return false; // не сносится водой/лавой
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}