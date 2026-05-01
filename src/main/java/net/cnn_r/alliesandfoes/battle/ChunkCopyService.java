package net.cnn_r.alliesandfoes.battle;

import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

public class ChunkCopyService {

    public static void copyOneChunk(ServerLevel source, ServerLevel target, ChunkKey chunk, int xBlockOffset, int zBlockOffset) {
        int baseX = chunk.getChunkX() << 4;
        int baseZ = chunk.getChunkZ() << 4;

        LevelChunk sourceChunk = source.getChunk(chunk.getChunkX(), chunk.getChunkZ());

        int minY = source.getMinY();
        int maxY = source.getMaxY();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos srcPos = new BlockPos(baseX + x, y, baseZ + z);
                    BlockState state = source.getBlockState(srcPos);
                    if (state.isAir()) continue;

                    BlockPos dstPos = new BlockPos(srcPos.getX() + xBlockOffset, y, srcPos.getZ() + zBlockOffset);
                    target.setBlock(dstPos, state, 3);

                    BlockEntity be = sourceChunk.getBlockEntity(srcPos);
                    if (be != null) {
                        CompoundTag nbt = be.saveWithFullMetadata(source.registryAccess());
                        nbt.putInt("x", dstPos.getX());
                        nbt.putInt("y", dstPos.getY());
                        nbt.putInt("z", dstPos.getZ());
                        // Block entity NBT load skipped — loadWithComponents API changed
                    }
                }
            }
        }
    }

    public static int computeTeamBXOffset(List<ChunkKey> chunksA) {
        if (chunksA.isEmpty()) return 96;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (ChunkKey c : chunksA) {
            minX = Math.min(minX, c.getChunkX());
            maxX = Math.max(maxX, c.getChunkX());
        }
        int widthChunks = maxX - minX + 1;
        return (widthChunks + 6) * 16;
    }
}
