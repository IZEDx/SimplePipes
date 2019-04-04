package alexiil.mc.mod.pipes.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.DefaultedList;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import alexiil.mc.lib.attributes.Simulation;
import alexiil.mc.lib.attributes.item.ItemInsertable;
import alexiil.mc.lib.attributes.item.filter.ConstantItemFilter;
import alexiil.mc.lib.attributes.item.filter.ItemFilter;
import alexiil.mc.lib.attributes.item.impl.RejectingItemInsertable;
import alexiil.mc.mod.pipes.util.DelayedList;
import alexiil.mc.mod.pipes.util.TagUtil;

public class PipeFlowItem extends PipeFlow {

    final ItemInsertable[] insertables;

    private final DelayedList<TravellingItem> items = new DelayedList<>();

    public PipeFlowItem(TilePipe pipe) {
        super(pipe);

        this.insertables = new ItemInsertable[6];
        for (Direction dir : Direction.values()) {
            insertables[dir.getOpposite().ordinal()] = new ItemInsertable() {
                @Override
                public ItemStack attemptInsertion(ItemStack stack, Simulation simulation) {
                    return stack;
                }

                @Override
                public ItemFilter getInsertionFilter() {
                    return ConstantItemFilter.ANYTHING;
                }
            };
        }
    }

    @Override
    public void fromTag(CompoundTag tag) {

    }

    @Override
    public CompoundTag toTag() {
        return new CompoundTag();
    }

    @Override
    protected void fromClientTag(CompoundTag tag) {

        // tag.put("item", item.stack.toTag(new CompoundTag()));
        // tag.putBoolean("to_center", item.toCenter);
        // tag.put("side", TagUtil.writeEnum(item.side));
        // tag.put("colour", TagUtil.writeEnum(item.colour));
        // tag.putShort("time", item.timeToDest > Short.MAX_VALUE ? Short.MAX_VALUE :(short) item.timeToDest);

        TravellingItem item = new TravellingItem(ItemStack.fromTag(tag.getCompound("item")));
        item.toCenter = tag.getBoolean("to_center");
        item.side = TagUtil.readEnum(tag.getTag("side"), Direction.class);
        item.colour = TagUtil.readEnum(tag.getTag("colour"), DyeColor.class);
        item.timeToDest = Short.toUnsignedInt(tag.getShort("time"));
        item.tickStarted = pipe.getWorldTime() + 1;
        item.tickFinished = item.tickStarted + item.timeToDest;
        item.speed *= getSpeedModifier();
        items.add(item.timeToDest + 1, item);
    }

    @Override
    protected Object getInsertable(Direction searchDirection) {
        return insertables[searchDirection.getId()];
    }

    @Override
    protected boolean canConnect(Direction dir) {
        return pipe.getItemInsertable(dir) != RejectingItemInsertable.NULL;
    }

    @Override
    protected void tick() {
        World w = pipe.getWorld();
        if (w == null) {
            return;
        }

        List<TravellingItem> toTick = items.advance();
        long currentTime = pipe.getWorldTime();

        for (TravellingItem item : toTick) {
            if (item.tickFinished > currentTime) {
                // Can happen if something ticks this tile multiple times in a single real tick
                items.add((int) (item.tickFinished - currentTime), item);
                continue;
            }
            if (item.isPhantom) {
                continue;
            }
            if (w.isClient) {
                // TODO: Client item advancing/intelligent stuffs

                continue;
            }
            if (item.toCenter) {
                onItemReachCenter(item);
            } else {
                onItemReachEnd(item);
            }
        }
    }

    @Override
    public void removeItemsForDrop(DefaultedList<ItemStack> all) {
        for (List<TravellingItem> list : this.items.getAllElements()) {
            for (TravellingItem travel : list) {
                if (!travel.isPhantom) {
                    all.add(travel.stack);
                }
            }
        }
        this.items.clear();
    }

    void sendItemDataToClient(TravellingItem item) {
        // TODO :p
        // System.out.println(getPos() + " - " + item.stack + " - " + item.side);
        CompoundTag tag = new CompoundTag();

        tag.put("item", item.stack.toTag(new CompoundTag()));
        tag.putBoolean("to_center", item.toCenter);
        tag.put("side", TagUtil.writeEnum(item.side));
        tag.put("colour", TagUtil.writeEnum(item.colour));
        tag.putShort("time", item.timeToDest > Short.MAX_VALUE ? Short.MAX_VALUE : (short) item.timeToDest);

        pipe.sendFlowPacket(tag);
    }

    protected List<EnumSet<Direction>> getOrderForItem(TravellingItem item, EnumSet<Direction> validDirections) {
        List<EnumSet<Direction>> list = new ArrayList<>();

        if (pipe instanceof TilePipeItemClay) {
            EnumSet<Direction> invs = EnumSet.noneOf(Direction.class);
            EnumSet<Direction> others = EnumSet.noneOf(Direction.class);
            for (Direction dir : validDirections) {
                if (pipe.getNeighbourPipe(dir) != null) {
                    others.add(dir);
                } else {
                    invs.add(dir);
                }
            }
            list.add(invs);
            list.add(others);
        } else {
            if (!validDirections.isEmpty()) {
                list.add(validDirections);
            }
        }
        return list;
    }

    protected boolean canBounce() {
        return pipe instanceof TilePipeItemIron;
    }

    protected double getSpeedModifier() {
        return (pipe instanceof TilePipeItemGold || pipe instanceof TilePipeItemGoldDirected) ? 6 : 1;
    }

    private void onItemReachCenter(TravellingItem item) {

        if (item.stack.isEmpty()) {
            return;
        }

        EnumSet<Direction> dirs = EnumSet.allOf(Direction.class);
        dirs.remove(item.side);
        dirs.removeAll(item.tried);
        for (Direction dir : Direction.values()) {
            if (!pipe.isConnected(dir) || pipe.getItemInsertable(dir) == null) {
                dirs.remove(dir);
            }
        }

        List<EnumSet<Direction>> order = getOrderForItem(item, dirs);
        if (order.isEmpty()) {
            if (canBounce()) {
                order = ImmutableList.of(EnumSet.of(item.side));
            } else {
                dropItem(item.stack, null, item.side.getOpposite(), item.speed);
                return;
            }
        }

        long now = pipe.getWorldTime();
        // Saves effort :p
        final double newSpeed = 0.08 * getSpeedModifier();
        //
        // if (holder.fireEvent(modifySpeed)) {
        // double target = modifySpeed.targetSpeed;
        // double maxDelta = modifySpeed.maxSpeedChange;
        // if (item.speed < target) {
        // newSpeed = Math.min(target, item.speed + maxDelta);
        // } else if (item.speed > target) {
        // newSpeed = Math.max(target, item.speed - maxDelta);
        // } else {
        // newSpeed = item.speed;
        // }
        // } else {
        // // Nothing affected the speed
        // // so just fallback to a sensible default
        // if (item.speed > 0.03) {
        // newSpeed = Math.max(0.03, item.speed - PipeBehaviourStone.SPEED_DELTA);
        // } else {
        // newSpeed = item.speed;
        // }
        // }

        List<Direction> destinations = new ArrayList<>();

        for (EnumSet<Direction> set : order) {
            List<Direction> shuffled = new ArrayList<>();
            shuffled.addAll(set);
            Collections.shuffle(shuffled);
            destinations.addAll(shuffled);
        }

        if (destinations.size() == 0) {
            dropItem(item.stack, null, item.side.getOpposite(), newSpeed);
        } else {
            TravellingItem newItem = new TravellingItem(item.stack);
            newItem.tried.addAll(item.tried);
            newItem.toCenter = false;
            newItem.colour = item.colour;
            newItem.side = destinations.get(0);
            newItem.speed = newSpeed;
            newItem.genTimings(now, pipe.getPipeLength(newItem.side));
            items.add(newItem.timeToDest, newItem);
            sendItemDataToClient(newItem);
        }
    }

    private void onItemReachEnd(TravellingItem item) {
        ItemInsertable ins = pipe.getItemInsertable(item.side);
        ItemStack excess = item.stack;
        if (ins != null) {
            Direction oppositeSide = item.side.getOpposite();
            TilePipe oPipe = pipe.getNeighbourPipe(item.side);

            if (oPipe != null && oPipe.flow instanceof PipeFlowItem) {
                excess = ((PipeFlowItem) oPipe.flow).injectItem(excess, true, oppositeSide, item.colour, item.speed);
            } else {
                excess = ins.attemptInsertion(excess, Simulation.ACTION);
            }
        }
        if (excess.isEmpty()) {
            return;
        }
        item.tried.add(item.side);
        item.toCenter = true;
        item.stack = excess;
        item.genTimings(pipe.getWorldTime(), pipe.getPipeLength(item.side));
        items.add(item.timeToDest, item);
        sendItemDataToClient(item);
    }

    private void dropItem(ItemStack stack, Direction side, Direction motion, double speed) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        double x = pipe.getPos().getX() + 0.5 + motion.getOffsetX() * 0.5;
        double y = pipe.getPos().getY() + 0.5 + motion.getOffsetY() * 0.5;
        double z = pipe.getPos().getZ() + 0.5 + motion.getOffsetZ() * 0.5;
        speed += 0.01;
        speed *= 2;
        ItemEntity ent = new ItemEntity(world(), x, y, z, stack);
        ent.setVelocity(new Vec3d(motion.getVector()).multiply(speed));

        world().spawnEntity(ent);
    }

    public boolean canInjectItems(Direction from) {
        return pipe.isConnected(from);
    }

    public ItemStack injectItem(@Nonnull ItemStack stack, boolean doAdd, Direction from, DyeColor colour,
        double speed) {
        if (world().isClient) {
            throw new IllegalStateException("Cannot inject items on the client side!");
        }
        if (!canInjectItems(from)) {
            return stack;
        }

        if (speed < 0.01) {
            speed = 0.01;
        }

        // Try insert

        ItemStack toSplit = ItemStack.EMPTY;
        ItemStack toInsert = stack;

        if (doAdd) {
            insertItemEvents(toInsert, colour, speed, from);
        }

        if (toSplit.isEmpty()) {
            toSplit = ItemStack.EMPTY;
        }

        return toSplit;
    }

    public void insertItemsForce(@Nonnull ItemStack stack, Direction from, DyeColor colour, double speed) {
        if (world().isClient) {
            throw new IllegalStateException("Cannot inject items on the client side!");
        }
        if (stack.isEmpty()) {
            return;
        }
        if (speed < 0.01) {
            speed = 0.01;
        }
        long now = pipe.getWorldTime();
        TravellingItem item = new TravellingItem(stack);
        item.side = from;
        item.toCenter = true;
        item.speed = speed;
        item.colour = colour;
        item.genTimings(now, 0);
        item.tried.add(from);
        addItemTryMerge(item);
    }

    /** Used internally to split up manual insertions from controlled extractions. */
    private void insertItemEvents(@Nonnull ItemStack toInsert, DyeColor colour, double speed, Direction from) {
        long now = world().getTime();

        TravellingItem item = new TravellingItem(toInsert);
        item.side = from;
        item.toCenter = true;
        item.speed = speed;
        item.colour = colour;
        item.stack = toInsert;
        item.genTimings(now, pipe.getPipeLength(from));
        item.tried.add(from);
        addItemTryMerge(item);
    }

    private void addItemTryMerge(TravellingItem item) {
        // for (List<TravellingItem> list : items.getAllElements()) {
        // for (TravellingItem item2 : list) {
        // if (item2.mergeWith(item)) {
        // return;
        // }
        // }
        // }
        items.add(item.timeToDest, item);
        sendItemDataToClient(item);
    }

    @Nullable
    private static EnumSet<Direction> getFirstNonEmptySet(List<EnumSet<Direction>> possible) {
        for (EnumSet<Direction> set : possible) {
            if (set.size() > 0) {
                return set;
            }
        }
        return null;
    }

    public List<TravellingItem> getAllItemsForRender() {
        List<TravellingItem> all = new ArrayList<>();
        for (List<TravellingItem> innerList : items.getAllElements()) {
            all.addAll(innerList);
        }
        return all;
    }
}
