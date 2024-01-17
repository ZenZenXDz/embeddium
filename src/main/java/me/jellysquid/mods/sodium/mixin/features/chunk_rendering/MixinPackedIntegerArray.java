package me.jellysquid.mods.sodium.mixin.features.chunk_rendering;

import me.jellysquid.mods.sodium.client.world.cloned.PackedIntegerArrayExtended;
import me.jellysquid.mods.sodium.client.world.cloned.palette.ClonedPalette;
import net.minecraft.util.BitStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BitStorage.class)
public class MixinPackedIntegerArray implements PackedIntegerArrayExtended {
    @Shadow
    @Final
    private long[] data;

    @Shadow(remap = false)
    @Final
    private int field_232982_f_;

    @Shadow
    @Final
    private long mask;

    @Shadow
    @Final
    private int bits;

    @Shadow
    @Final
    private int size;

    @Override
    public <T> void copyUsingPalette(T[] out, ClonedPalette<T> palette) {
        int idx = 0;

        for (long word : this.data) {
            long l = word;

            for (int j = 0; j < this.field_232982_f_; ++j) {
                out[idx] = palette.get((int) (l & this.mask));
                l >>= this.bits;

                if (++idx >= this.size) {
                    return;
                }
            }
        }
    }
}
