package net.cnn_r.alliesandfoes.alliance;

import net.cnn_r.alliesandfoes.roleslot.RoleSlotService;
import net.cnn_r.alliesandfoes.upgrade.RoleType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class AllianceAuraService {
    private static final Map<MinecraftServer, AllianceAuraService> INSTANCES = new WeakHashMap<>();

    private AllianceAuraService() {}

    public static AllianceAuraService get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, s -> new AllianceAuraService());
    }

    public void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) return;

        AllianceManager mgr = AllianceManager.get(server);

        for (Alliance alliance : mgr.getAlliances()) {
            List<ServerPlayer> online = server.getPlayerList().getPlayers().stream()
                    .filter(p -> alliance.getMemberUuids().contains(p.getUUID()))
                    .toList();
            if (online.isEmpty()) continue;

            long distinctRoles = Arrays.stream(RoleType.values())
                    .filter(role -> online.stream()
                            .anyMatch(p -> RoleSlotService.hasRoleInHand(p, role)))
                    .count();

            for (ServerPlayer member : online) {
                if (distinctRoles >= 2)
                    member.addEffect(new MobEffectInstance(MobEffects.HASTE, 200, 0, false, false));
                if (distinctRoles >= 3)
                    member.addEffect(new MobEffectInstance(MobEffects.LUCK, 200, 0, false, false));
                if (distinctRoles >= 4)
                    member.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 200, 0, false, false));
            }
        }
    }
}
