package me.jellysquid.mods.sodium.mixin.features.chunk_rendering;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListenerManager;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BooleanSupplier;

@Mixin(ClientChunkCache.class)
public abstract class MixinClientChunkManager implements ChunkStatusListenerManager {
    @Shadow
    @Nullable
    public abstract LevelChunk getChunk(int i, int j, ChunkStatus chunkStatus, boolean bl);

    private final LongOpenHashSet loadedChunks = new LongOpenHashSet();
    private boolean needsTrackingUpdate = false;

    private ChunkStatusListener listener;

    @Inject(method = "replaceWithPacketData", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;onChunkLoaded(II)V", shift = At.Shift.AFTER))
    private void afterLoadChunkFromPacket(int x, int z, ChunkBiomeContainer biomes, FriendlyByteBuf buf, CompoundTag tag, int verticalStripBitmask, boolean complete, CallbackInfoReturnable<LevelChunk> cir) {
        if (this.listener != null) {
            this.listener.onChunkAdded(x, z);
            this.loadedChunks.add(ChunkPos.asLong(x, z));
        }
    }

    @Inject(method = "drop", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;replace(ILnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/chunk/LevelChunk;)Lnet/minecraft/world/level/chunk/LevelChunk;", shift = At.Shift.AFTER))
    private void afterUnloadChunk(int x, int z, CallbackInfo ci) {
        if (this.listener != null) {
            this.listener.onChunkRemoved(x, z);
            this.loadedChunks.remove(ChunkPos.asLong(x, z));
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void afterTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        if (!this.needsTrackingUpdate) {
            return;
        }

        LongIterator it = this.loadedChunks.iterator();

        while (it.hasNext()) {
            long pos = it.nextLong();

            int x = ChunkPos.getX(pos);
            int z = ChunkPos.getZ(pos);

            if (this.getChunk(x, z, ChunkStatus.FULL, false) == null) {
                it.remove();

                if (this.listener != null) {
                    this.listener.onChunkRemoved(x, z);
                }
            }
        }

        this.needsTrackingUpdate = false;
    }

    @Inject(method = "updateViewCenter(II)V", at = @At("RETURN"))
    private void afterChunkMapCenterChanged(int x, int z, CallbackInfo ci) {
        this.needsTrackingUpdate = true;
    }

    @Inject(method = "updateViewRadius",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientChunkCache$Storage;replace(ILnet/minecraft/world/level/chunk/LevelChunk;)V",
                    shift = At.Shift.AFTER))
    private void afterLoadDistanceChanged(int loadDistance, CallbackInfo ci) {
        this.needsTrackingUpdate = true;
    }

    @Override
    public void setListener(ChunkStatusListener listener) {
        this.listener = listener;
    }

    @Mixin(targets = "net/minecraft/client/multiplayer/ClientChunkCache$Storage")
    public static class MixinClientChunkMap {
        @Mutable
        @Shadow
        @Final
        private AtomicReferenceArray<LevelChunk> chunks;

        @Mutable
        @Shadow
        @Final
        private int viewRange;

        @Mutable
        @Shadow
        @Final
        private int chunkRadius;

        private int factor;

        @Inject(method = "<init>", at = @At("RETURN"))
        private void reinit(ClientChunkCache outer, int loadDistance, CallbackInfo ci) {
            // This re-initialization is a bit expensive on memory, but it only happens when either the world is
            // switched or the render distance is changed;
            this.chunkRadius = loadDistance;

            // Make the diameter a power-of-two so we can exploit bit-wise math when computing indices
            this.viewRange = Mth.smallestEncompassingPowerOfTwo(loadDistance * 2 + 1);

            // The factor is used as a bit mask to replace the modulo in getIndex
            this.factor = this.viewRange - 1;

            this.chunks = new AtomicReferenceArray<>(this.viewRange * this.viewRange);
        }

        /**
         * @reason Avoid expensive modulo
         * @author JellySquid
         */
        @Overwrite
        private int getIndex(int chunkX, int chunkZ) {
            return (chunkZ & this.factor) * this.viewRange + (chunkX & this.factor);
        }
    }
}
