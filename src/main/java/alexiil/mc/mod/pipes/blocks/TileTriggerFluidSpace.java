package alexiil.mc.mod.pipes.blocks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.Direction;

import alexiil.mc.lib.attributes.fluid.FluidInvStats;
import alexiil.mc.lib.attributes.fluid.FluidInvStats.FluidInvStatistic;
import alexiil.mc.lib.attributes.fluid.filter.ConstantFluidFilter;
import alexiil.mc.lib.attributes.fluid.filter.ExactFluidFilter;
import alexiil.mc.lib.attributes.fluid.filter.FluidFilter;
import alexiil.mc.lib.attributes.fluid.impl.EmptyFluidInvStats;
import alexiil.mc.lib.attributes.fluid.volume.FluidKey;
import alexiil.mc.lib.attributes.fluid.volume.FluidKeys;

public class TileTriggerFluidSpace extends TileTrigger {

    public FluidKey filter = FluidKeys.EMPTY;

    public TileTriggerFluidSpace() {
        super(SimplePipeBlocks.TRIGGER_FLUID_INV_SPACE_TILE);
    }

    @Override
    public void fromTag(CompoundTag tag) {
        super.fromTag(tag);
        filter = FluidKey.fromTag(tag.getCompound("filter"));
    }

    @Override
    public CompoundTag toTag(CompoundTag tag) {
        tag = super.toTag(tag);
        if (!filter.isEmpty()) {
            tag.put("filter", filter.toTag());
        }
        return tag;
    }

    @Override
    protected EnumTriggerState getTriggerState(Direction dir) {
        FluidInvStats invStats = getNeighbourFluidStats(dir);
        if (invStats == EmptyFluidInvStats.INSTANCE) {
            return EnumTriggerState.NO_TARGET;
        }
        final FluidFilter fluidFilter;
        if (filter.isEmpty()) {
            fluidFilter = ConstantFluidFilter.ANYTHING;
        } else {
            fluidFilter = new ExactFluidFilter(filter);
        }

        FluidInvStatistic stats = invStats.getStatistics(fluidFilter);
        assert stats.spaceTotal >= 0 : "ItemInvStatistic should have checked this for ExactItemStackFilter and ConstantItemFilter!";
        return EnumTriggerState.of(stats.spaceAddable + stats.spaceTotal > 0);
    }
}
