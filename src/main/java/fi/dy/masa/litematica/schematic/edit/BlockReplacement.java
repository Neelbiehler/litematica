package fi.dy.masa.litematica.schematic.edit;

import java.util.Objects;
import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;

/** Builds replacement containers incrementally, then installs them as one undoable edit. */
public final class BlockReplacement
{
    private final LitematicaSchematic schematic;
    private final Block source;
    private final Block target;
    private final Target cell;
    private final List<Region> regions = new ArrayList<>();
    private final List<Runnable> restoreData = new ArrayList<>();
    private final List<BooleanSupplier> dataCanBeRestored = new ArrayList<>();
    private int regionIndex;
    private long scanned;
    private long volume;
    private long changed;
    private long nonAirDelta;
    private boolean applied;
    private boolean used;

    public record Target(String region, BlockPos position)
    {
        public Target
        {
            Objects.requireNonNull(region);
            position = position.immutable();
        }
    }

    public BlockReplacement(LitematicaSchematic schematic, Block source, Block target)
    {
        this(schematic, source, target, null);
    }

    public BlockReplacement(LitematicaSchematic schematic, Block source, Block target, @Nullable Target cell)
    {
        if (source == target || source.defaultBlockState().isAir()) throw new IllegalArgumentException("Choose different non-air source and target blocks");
        this.schematic = schematic;
        this.source = source;
        this.target = target;
        this.cell = cell;
        for (String name : schematic.getAreas().keySet())
        {
            if (cell != null && !cell.region().equals(name)) continue;
            LitematicaBlockStateContainer before = schematic.getSubRegionContainer(name);
            if (before == null || before.getPalette().fromMapping().stream().noneMatch(state -> state.is(source))) continue;
            if (cell != null && (source.defaultBlockState().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) ||
                                 source.defaultBlockState().hasProperty(BlockStateProperties.BED_PART)))
                throw new IllegalArgumentException("Multi-block structures must be replaced together");
            // Validate shape compatibility before allocating copies or changing anything.
            before.getPalette().fromMapping().stream().filter(state -> state.is(source)).forEach(state -> replacementState(state, target));
            if (cell != null)
            {
                BlockPos pos = cell.position();
                Vec3i size = before.getSize();
                if (pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0 || pos.getX() >= size.getX() || pos.getY() >= size.getY() || pos.getZ() >= size.getZ())
                    throw new IllegalArgumentException("Block position is outside the selected region");
            }
            Region region = new Region(name, before, cell != null);
            this.regions.add(region);
            this.volume += region.volume;
        }
    }

    public static BlockState replacementState(BlockState source, Block target)
    {
        BlockState result = target.defaultBlockState();
        if ((result.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && !source.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) ||
            (result.hasProperty(BlockStateProperties.BED_PART) && !source.hasProperty(BlockStateProperties.BED_PART)))
        {
            throw new IllegalArgumentException("This target requires a matching multi-block shape");
        }
        for (Property<?> property : result.getProperties()) result = copyProperty(source, result, property);
        return result;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState source, BlockState target, Property<T> property)
    {
        if (source.hasProperty(property) && property.getPossibleValues().contains(source.getValue(property)))
        {
            return target.setValue(property, source.getValue(property));
        }
        return target;
    }

    public void advance(int budget)
    {
        while (budget-- > 0 && !this.isReady())
        {
            Region region = this.regions.get(this.regionIndex);
            Vec3i size = region.before.getSize();
            int x = this.cell == null ? (int) (region.cursor % size.getX()) : this.cell.position().getX();
            int z = this.cell == null ? (int) ((region.cursor / size.getX()) % size.getZ()) : this.cell.position().getZ();
            int y = this.cell == null ? (int) (region.cursor / ((long) size.getX() * size.getZ())) : this.cell.position().getY();
            BlockState previous = region.before.get(x, y, z);
            if (previous.is(this.source))
            {
                BlockState next = replacementState(previous, this.target);
                region.after.set(x, y, z, next);
                ++this.changed;
                if (next.isAir()) --this.nonAirDelta;
            }
            ++this.scanned;
            if (++region.cursor == region.volume) ++this.regionIndex;
        }
    }

    public boolean isReady() { return this.regionIndex == this.regions.size(); }
    public long getChangedCount() { return this.changed; }
    public int getProgress() { return this.volume == 0 ? 100 : (int) (100 * this.scanned / this.volume); }
    public LitematicaSchematic getSchematic() { return this.schematic; }

    public boolean apply()
    {
        if (!this.isReady() || this.changed == 0 || this.used) return false;
        for (Region region : this.regions)
        {
            if (this.schematic.getSubRegionContainer(region.name) != region.before || region.before.getRevision() != region.beforeRevision) return false;
        }
        for (Region region : this.regions)
        {
            this.removeChangedData(this.schematic.getBlockEntityMapForRegion(region.name), region);
            this.removeChangedData(this.schematic.getScheduledBlockTicksForRegion(region.name), region);
            this.removeChangedData(this.schematic.getScheduledFluidTicksForRegion(region.name), region);
            this.schematic.replaceSubRegionContainer(region.name, region.after);
            region.afterRevision = region.after.getRevision();
        }
        this.applied = true;
        this.used = true;
        this.markModified(this.nonAirDelta);
        return true;
    }

    private <T> void removeChangedData(Map<BlockPos, T> data, Region region)
    {
        if (data == null) return;
        Map<BlockPos, T> removed = new HashMap<>();
        Vec3i size = region.before.getSize();
        data.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            if (pos.getX() >= 0 && pos.getY() >= 0 && pos.getZ() >= 0 && pos.getX() < size.getX() && pos.getY() < size.getY() && pos.getZ() < size.getZ() &&
                region.before.get(pos.getX(), pos.getY(), pos.getZ()) != region.after.get(pos.getX(), pos.getY(), pos.getZ()))
            {
                removed.put(pos, entry.getValue());
                return true;
            }
            return false;
        });
        this.dataCanBeRestored.add(() -> removed.keySet().stream().noneMatch(data::containsKey));
        this.restoreData.add(() -> data.putAll(removed));
    }

    public boolean canUndo()
    {
        if (!this.applied) return false;
        for (Region region : this.regions)
        {
            if (this.schematic.getSubRegionContainer(region.name) != region.after || region.after.getRevision() != region.afterRevision) return false;
        }
        return this.dataCanBeRestored.stream().allMatch(BooleanSupplier::getAsBoolean);
    }

    public boolean undo()
    {
        if (!this.canUndo()) return false;
        for (Region region : this.regions) this.schematic.replaceSubRegionContainer(region.name, region.before);
        this.restoreData.forEach(Runnable::run);
        this.markModified(-this.nonAirDelta);
        this.applied = false;
        return true;
    }

    private void markModified(long delta)
    {
        this.schematic.getMetadata().setTotalBlocks((int) Math.max(0, Math.min(Integer.MAX_VALUE, this.schematic.getMetadata().getTotalBlocks() + delta)));
        this.schematic.getMetadata().setModifiedSinceSaved();
        this.schematic.getMetadata().setTimeModifiedToNow();
    }

    private static final class Region
    {
        private final String name;
        private final LitematicaBlockStateContainer before;
        private final LitematicaBlockStateContainer after;
        private final long beforeRevision;
        private final long volume;
        private long cursor;
        private long afterRevision;

        private Region(String name, LitematicaBlockStateContainer before, boolean singleBlock)
        {
            this.name = name;
            this.before = before;
            this.beforeRevision = before.getRevision();
            this.after = before.copy();
            Vec3i size = before.getSize();
            this.volume = singleBlock ? 1 : (long) size.getX() * size.getY() * size.getZ();
        }
    }
}
