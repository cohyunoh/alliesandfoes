package net.cnn_r.alliesandfoes.battle;

import net.cnn_r.alliesandfoes.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class ChunkCopyService {

    public static void copyOneChunk(ServerLevel source, ServerLevel target, ChunkKey chunk, int xBlockOffset, int zBlockOffset) {
        int minY = source.getMinY();
        int maxY = source.getMaxY();
        copyOneChunk(source, target, chunk, xBlockOffset, zBlockOffset, minY, maxY);
    }

    public static void copyOneChunk(ServerLevel source, ServerLevel target, ChunkKey chunk,
                                    int xBlockOffset, int zBlockOffset, int minY, int maxY) {
        int baseX = chunk.getChunkX() << 4;
        int baseZ = chunk.getChunkZ() << 4;

        LevelChunk sourceChunk = source.getChunk(chunk.getChunkX(), chunk.getChunkZ());

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

    public static int findGlobalSurfaceMin(ServerLevel src, Collection<ChunkKey> chunks) {
        int min = Integer.MAX_VALUE;
        for (ChunkKey chunk : chunks) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int wx = (chunk.getChunkX() << 4) + x;
                    int wz = (chunk.getChunkZ() << 4) + z;
                    BlockPos top = src.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(wx, 0, wz));
                    min = Math.min(min, top.getY() - 1);
                }
            }
        }
        return min == Integer.MAX_VALUE ? src.getMinY() : min;
    }

    public static int findGlobalSurfaceMax(ServerLevel src, Collection<ChunkKey> chunks) {
        int max = Integer.MIN_VALUE;
        for (ChunkKey chunk : chunks) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int wx = (chunk.getChunkX() << 4) + x;
                    int wz = (chunk.getChunkZ() << 4) + z;
                    BlockPos top = src.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(wx, 0, wz));
                    max = Math.max(max, top.getY() - 1);
                }
            }
        }
        return max == Integer.MIN_VALUE ? src.getMaxY() : max + 40;
    }

    public static void addFloatingBase(ServerLevel target, Collection<ChunkKey> chunks,
                                       int xOffset, int zOffset, int baseY) {
        for (ChunkKey chunk : chunks) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int dstX = (chunk.getChunkX() << 4) + xOffset + x;
                    int dstZ = (chunk.getChunkZ() << 4) + zOffset + z;
                    BlockPos colBase = new BlockPos(dstX, baseY, dstZ);
                    if (target.getBlockState(colBase).isAir()) continue;

                    // Deterministic tail based on column position
                    long seed = Objects.hash(dstX, dstZ);
                    Random rng = new Random(seed);

                    for (int i = 1; i <= 4; i++) {
                        float keepChance = 1.0f - (i * 0.2f);
                        if (rng.nextFloat() > keepChance) break;
                        BlockState block = (i == 4) ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE.defaultBlockState();
                        target.setBlock(new BlockPos(dstX, baseY - i, dstZ), block, 3);
                    }
                }
            }
        }
    }
}
