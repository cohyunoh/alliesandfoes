package net.cnn_r.alliesandfoes.feedback;

import net.cnn_r.alliesandfoes.alliance.Alliance;
import net.cnn_r.alliesandfoes.alliance.AllianceManager;
import net.cnn_r.alliesandfoes.network.MapScreenMessagePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/**
 * Central hub for sending audio/visual feedback to players when meaningful game events occur.
 */
public final class FeedbackService {

    private FeedbackService() {}

    /** Soft clink + golden sparkle when a player earns role currency. */
    public static void onRoleCurrencyEarned(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        double x = player.getX(), y = player.getY() + 1.2, z = player.getZ();
        level.sendParticles(ParticleTypes.WAX_ON, x, y, z, 6, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, player.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.3f, 1.4f);
    }

    /** Bell chime + green shimmer when alliance influence is gained at the Tribute Altar. */
    public static void onInfluenceGained(ServerPlayer player, int amount) {
        ServerLevel level = (ServerLevel) player.level();
        double x = player.getX(), y = player.getY() + 1.0, z = player.getZ();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, amount / 5 + 3, 0.5, 0.5, 0.5, 0.02);
        level.playSound(null, player.blockPosition(),
                SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.6f, 1.0f);
    }

    /** Milestone toast + sound broadcast to all online alliance members every 50 influence. */
    public static void checkInfluenceMilestone(MinecraftServer server, UUID allianceId, int oldBalance, int newBalance) {
        int oldMilestone = oldBalance / 50;
        int newMilestone = newBalance / 50;
        if (newMilestone <= oldMilestone) return;

        Alliance alliance = AllianceManager.get(server).getAllianceById(allianceId);
        if (alliance == null) return;

        String msg = "★ Alliance influence: " + newBalance;
        MapScreenMessagePayload toast = new MapScreenMessagePayload(msg);
        for (UUID memberId : alliance.getMemberUuids()) {
            ServerPlayer p = server.getPlayerList().getPlayer(memberId);
            if (p == null) continue;
            ServerPlayNetworking.send(p, toast);
            p.level().playSound(null, p.blockPosition(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.5f, 1.2f);
        }
    }

    /** Alliance-colored particle ring + stone thud when a chunk is claimed. */
    public static void onChunkClaimed(ServerLevel level, ChunkPos chunk) {
        double cx = chunk.getMiddleBlockX();
        double cz = chunk.getMiddleBlockZ();
        double y  = level.getSeaLevel() + 1.0;
        level.sendParticles(ParticleTypes.END_ROD, cx, y, cz, 20, 8.0, 0.5, 8.0, 0.01);
        level.playSound(null, (int) cx, (int) y, (int) cz,
                SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0f, 0.9f);
    }

    /** War-horn blast when the active phase begins. */
    public static void onWarActive(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0f, 0.7f);
    }

    /** Victory chord + particles when a capture point is taken. */
    public static void onCapturePointTaken(ServerLevel level, int chunkX, int chunkZ) {
        double cx = (chunkX << 4) + 8.0;
        double cz = (chunkZ << 4) + 8.0;
        double y  = level.getSeaLevel() + 2.0;
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, cx, y, cz, 40, 3.0, 1.5, 3.0, 0.3);
        level.playSound(null, (int) cx, (int) y, (int) cz,
                SoundEvents.UI_TOAST_IN, SoundSource.MASTER, 1.0f, 1.2f);
    }
}
