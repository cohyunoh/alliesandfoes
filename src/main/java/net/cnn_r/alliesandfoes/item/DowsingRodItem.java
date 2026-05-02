package net.cnn_r.alliesandfoes.item;

import net.cnn_r.alliesandfoes.protect.BlockOwnerService;
import net.cnn_r.alliesandfoes.territory.ChunkFragmentSavedData;
import net.cnn_r.alliesandfoes.territory.ChunkFragmentService;
import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DowsingRodItem extends Item {

    public DowsingRodItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        if (world.isClientSide()) return InteractionResult.PASS;
        ServerLevel level = (ServerLevel) world;

        net.minecraft.world.level.ChunkPos center = player.chunkPosition();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                ChunkKey chunk = new ChunkKey(
                        level.dimension().identifier().toString(),
                        center.x() + dx, center.z() + dz);
                List<BlockPos> fragments = ChunkFragmentService.getFragmentPositions(level, chunk);
                for (BlockPos fp : fragments) {
                    String key = BlockOwnerService.toKey(level, fp);
                    if (ChunkFragmentSavedData.get(level.getServer()).isExcavated(key)) continue;
                    double dist = player.blockPosition().distSqr(fp);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = fp;
                    }
                }
            }
        }

        Holder<Biome> biome = level.getBiome(player.blockPosition());
        String flavor = getBiomeFlavor(biome);

        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (nearest == null) {
                sp.sendSystemMessage(Component.literal("§7" + flavor + " No fragments within range."), true);
            } else {
                Vec3 dir = Vec3.atCenterOf(nearest).subtract(player.position()).normalize();
                String compass = toCompassDir(dir);
                int dist = (int) Math.sqrt(nearestDist);
                sp.sendSystemMessage(Component.literal("§e" + flavor + " Fragment sensed §f" + dist + "§e blocks §f" + compass + "§e."), true);
            }
        }

        player.getCooldowns().addCooldown(player.getItemInHand(hand), 40);
        return InteractionResult.SUCCESS;
    }

    private String getBiomeFlavor(Holder<Biome> biome) {
        float temp = biome.value().getBaseTemperature();
        if (temp >= 1.5f) return "The air shimmers with heat.";
        if (temp >= 0.9f) return "Warm earth meets your feet.";
        if (temp >= 0.5f) return "The soil smells rich and earthy.";
        if (temp >= 0.2f) return "A crisp breeze stirs the grass.";
        return "Frost hangs in the still air.";
    }

    private String toCompassDir(Vec3 dir) {
        double angle = Math.toDegrees(Math.atan2(dir.x, dir.z));
        if (angle < 0) angle += 360;
        if (angle < 22.5 || angle >= 337.5) return "S";
        if (angle < 67.5) return "SW";
        if (angle < 112.5) return "W";
        if (angle < 157.5) return "NW";
        if (angle < 202.5) return "N";
        if (angle < 247.5) return "NE";
        if (angle < 292.5) return "E";
        return "SE";
    }
}
