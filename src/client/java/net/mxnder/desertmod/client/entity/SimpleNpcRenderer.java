package net.mxnder.desertmod.client.entity;

import com.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.mxnder.desertmod.client.NpcSkinLoader;
import net.mxnder.desertmod.entity.ModEntities;
import net.mxnder.desertmod.entity.SimpleNpcEntity;

import java.util.Map;
import java.util.WeakHashMap;

public class SimpleNpcRenderer extends GeoEntityRenderer<SimpleNpcEntity, LivingEntityRenderState> {

    // createRenderState() в GeckoLib 5 final — свои классы состояний не подставить.
    // Поэтому запоминаем пару «объект состояния -> текстура» при извлечении
    // и достаём её при рендере. WeakHashMap, чтобы старые состояния,
    // на которые никто не ссылается, вычищались сборщиком мусора сами.
    private final Map<LivingEntityRenderState, Identifier> stateTextures = new WeakHashMap<>();

    public SimpleNpcRenderer(EntityRendererProvider.Context context) {
        super(context, ModEntities.SIMPLE_NPC);
    }

    // Вызывается до рендера, и здесь ещё жива сама сущность — берём скин с неё
    @Override
    public void extractRenderState(SimpleNpcEntity entity, LivingEntityRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        Identifier skin = NpcSkinLoader.get(entity.getSkinName());
        stateTextures.put(state, skin != null ? skin : NpcSkinLoader.getDefault());
    }

    // Вызывается при отрисовке: отдаём текстуру, запомненную для ЭТОГО состояния
    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        Identifier id = stateTextures.get(state);
        return id != null ? id : NpcSkinLoader.getDefault();
    }
}