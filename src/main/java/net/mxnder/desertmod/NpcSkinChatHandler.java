package net.mxnder.desertmod;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.mxnder.desertmod.entity.SimpleNpcEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NpcSkinChatHandler {

    // игрок -> NPC, для которого сейчас открыт выбор скина
    private static final Map<UUID, UUID> PENDING = new HashMap<>();

    public static void openSelection(UUID player, UUID npc) {
        PENDING.put(player, npc);
    }

    public static void init() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((chat, sender, params) -> {
            UUID npcId = PENDING.get(sender.getUUID());
            if (npcId == null) return true;              // выбор не открыт — обычный чат

            // Текст сообщения достаём из PlayerChatMessage
            String name = chat.signedBody().content().trim();
            PENDING.remove(sender.getUUID());            // любое сообщение закрывает выбор

            // Написал не имя скина — передумал, сообщение идёт в чат как обычно
            var rm = sender.level().getServer().getResourceManager();
            if (!NpcSkins.listAll(rm).contains(name)) return true;

            Entity npc = ((ServerLevel) sender.level()).getEntity(npcId);
            if (npc instanceof SimpleNpcEntity simpleNpc) {
                simpleNpc.setSkinName(name);
                sender.sendSystemMessage(Component.literal("§aNPC одет в скин: §f" + name));
            }
            return false; // гасим сообщение — это была команда, а не чат
        });
    }
}