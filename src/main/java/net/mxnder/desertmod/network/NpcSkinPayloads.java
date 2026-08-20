package net.mxnder.desertmod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class NpcSkinPayloads {

    /** Клиент -> сервер: «вот новый скин, сохрани и раздай всем». */
    public record Upload(String name, byte[] data) implements CustomPacketPayload {
        public static final Type<Upload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("desertmod", "npc_skin_upload"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Upload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Upload::name,
                ByteBufCodecs.byteArray(4 * 1024 * 1024), Upload::data,
                Upload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Сервер -> клиент: «вот скин, поставь себе». */
    public record Sync(String name, byte[] data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("desertmod", "npc_skin_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Sync::name,
                ByteBufCodecs.byteArray(4 * 1024 * 1024), Sync::data,
                Sync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Сервер -> клиент: открыть редактор: списки + текущие значения. */
    public record OpenEditor(String npcId, List<String> skins, List<String> anims,
                             String currentSkin, String currentAnim) implements CustomPacketPayload {
        public static final Type<OpenEditor> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("desertmod", "npc_editor_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenEditor> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenEditor::npcId,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenEditor::skins,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenEditor::anims,
                ByteBufCodecs.STRING_UTF8, OpenEditor::currentSkin,
                ByteBufCodecs.STRING_UTF8, OpenEditor::currentAnim,
                OpenEditor::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Клиент -> сервер: «поставь этому NPC вот эту анимацию». */
    public record SetAnim(String npcId, String anim) implements CustomPacketPayload {
        public static final Type<SetAnim> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("desertmod", "npc_editor_set_anim"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetAnim> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SetAnim::npcId,
                ByteBufCodecs.STRING_UTF8, SetAnim::anim,
                SetAnim::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Клиент -> сервер: «поставь этому NPC вот этот скин». */
    public record SetSkin(String npcId, String skin) implements CustomPacketPayload {
        public static final Type<SetSkin> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("desertmod", "npc_editor_set_skin"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetSkin> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SetSkin::npcId,
                ByteBufCodecs.STRING_UTF8, SetSkin::skin,
                SetSkin::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Вызвать один раз в DesertMod.onInitialize(). */
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Upload.TYPE, Upload.CODEC); // клиент -> сервер
        PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);    // сервер -> клиент
        PayloadTypeRegistry.serverboundPlay().register(SetSkin.TYPE, SetSkin.CODEC);      // клиент -> сервер
        PayloadTypeRegistry.clientboundPlay().register(OpenEditor.TYPE, OpenEditor.CODEC); // сервер -> клиент
        PayloadTypeRegistry.serverboundPlay().register(SetAnim.TYPE, SetAnim.CODEC);
    }
}