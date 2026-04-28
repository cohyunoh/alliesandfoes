package net.cnn_r.alliesandfoes.prospector;

import net.cnn_r.alliesandfoes.item.ModItems;
import net.cnn_r.alliesandfoes.map.MapState;
import net.cnn_r.alliesandfoes.map.cache.ChunkValueCache;
import net.cnn_r.alliesandfoes.map.data.ChunkValueData;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

public class DowsingRodVfxController {
    private static final int SAMPLE_RADIUS = 2; // 5×5 = 25 chunks sampled
    private static int tickCounter = 0;

    public static void onClientTick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) return;
        if (!player.getMainHandItem().is(ModItems.DOWSING_ROD)) return;

        tickCounter++;

        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        String dimId = level.dimension().identifier().toString();
        ChunkValueCache valueCache = MapState.getChunkValueCache();

        int totalValue = 0;
        int count = 0;
        for (int dx = -SAMPLE_RADIUS; dx <= SAMPLE_RADIUS; dx++) {
            for (int dz = -SAMPLE_RADIUS; dz <= SAMPLE_RADIUS; dz++) {
                ChunkValueData data = valueCache.get(new ChunkKey(dimId, chunkX + dx, chunkZ + dz));
                if (data == null) continue;
                totalValue += data.getTotalValue();
                count++;
            }
        }

        if (count == 0) return;

        float signal = (float) totalValue / count; // 1.0–10.0
        int signalLevel = signal < 4.0f ? 0 : signal < 7.0f ? 1 : 2;

        // Action bar signal display
        if (minecraft.screen == null) {
            minecraft.gui.setOverlayMessage(buildSignalBar(signal, signalLevel), false);
        }

        // Particles: medium → every 20 ticks, high → every 6 ticks
        if (signalLevel == 1 && tickCounter % 20 == 0) {
            spawnGlowParticles(level, player, 1);
        } else if (signalLevel == 2 && tickCounter % 6 == 0) {
            spawnGlowParticles(level, player, 3);
        }
    }

    private static Component buildSignalBar(float signal, int signalLevel) {
        // Map signal 1–10 to 0–10 filled bars
        int filled = Math.round((signal - 1.0f) / 9.0f * 10);
        filled = Math.max(0, Math.min(10, filled));
        StringBuilder bar = new StringBuilder("⚡ ");
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? "▰" : "░");
        }
        ChatFormatting color = switch (signalLevel) {
            case 0 -> ChatFormatting.GRAY;
            case 1 -> ChatFormatting.YELLOW;
            default -> ChatFormatting.GOLD;
        };
        return Component.literal(bar.toString()).withStyle(color);
    }

    private static void spawnGlowParticles(ClientLevel level, LocalPlayer player, int count) {
        RandomSource rng = level.getRandom();
        for (int i = 0; i < count; i++) {
            double x = player.getX() + (rng.nextDouble() - 0.5) * 3.0;
            double y = player.getY() + rng.nextDouble() * 0.5;
            double z = player.getZ() + (rng.nextDouble() - 0.5) * 3.0;
            level.addParticle(ParticleTypes.GLOW, x, y, z, 0.0, 0.04, 0.0);
        }
    }
}
