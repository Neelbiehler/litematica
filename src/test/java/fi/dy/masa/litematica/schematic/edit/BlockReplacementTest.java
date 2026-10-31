package fi.dy.masa.litematica.schematic.edit;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.registries.BuiltInRegistries;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import static org.junit.jupiter.api.Assertions.*;

class BlockReplacementTest
{
    @BeforeAll
    static void bootstrap()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private LitematicaSchematic fixture()
    {
        AreaSelection area = new AreaSelection();
        area.addSubRegionBox(new Box(BlockPos.ZERO, new BlockPos(2, 0, 0), "room"), true);
        LitematicaSchematic schematic = LitematicaSchematic.createEmptySchematic(area, "test");
        var container = schematic.getSubRegionContainer("room");
        container.set(0, 0, 0, Blocks.STONE.defaultBlockState());
        container.set(1, 0, 0, Blocks.STONE.defaultBlockState());
        container.set(2, 0, 0, Blocks.DIRT.defaultBlockState());
        schematic.getMetadata().setTotalBlocks(3);
        return schematic;
    }

    @Test
    void singleBlockModeDoesNotReplaceOtherOccurrences()
    {
        var schematic = fixture();
        var edit = new BlockReplacement(schematic, Blocks.STONE, Blocks.GLASS, new BlockReplacement.Target("room", BlockPos.ZERO));
        edit.advance(1);
        assertTrue(edit.isReady());
        assertEquals(1, edit.getChangedCount());
        assertTrue(edit.apply());
        assertSame(Blocks.GLASS.defaultBlockState(), schematic.getSubRegionContainer("room").get(0, 0, 0));
        assertSame(Blocks.STONE.defaultBlockState(), schematic.getSubRegionContainer("room").get(1, 0, 0));
        assertTrue(edit.undo());
        assertSame(Blocks.STONE.defaultBlockState(), schematic.getSubRegionContainer("room").get(0, 0, 0));
    }

    @Test
    void growingAndCopyingPalettesPreservesEveryBlock()
    {
        var container = new LitematicaBlockStateContainer(40, 1, 1);
        var states = BuiltInRegistries.BLOCK.stream().filter(block -> !block.defaultBlockState().isAir()).limit(40)
                .map(Block::defaultBlockState).toList();
        for (int x = 0; x < 40; ++x) container.set(x, 0, 0, states.get(x));
        var copy = container.copy();
        for (int x = 0; x < 40; ++x) assertSame(states.get(x), copy.get(x, 0, 0));
        copy.set(20, 0, 0, Blocks.DIAMOND_BLOCK.defaultBlockState());
        assertSame(states.get(20), container.get(20, 0, 0));
        assertSame(Blocks.DIAMOND_BLOCK.defaultBlockState(), copy.get(20, 0, 0));
    }

    @Test
    void replacesAllRegionsAndRestoresPendingTicks()
    {
        AreaSelection area = new AreaSelection();
        area.addSubRegionBox(new Box(BlockPos.ZERO, BlockPos.ZERO, "first"), true);
        area.addSubRegionBox(new Box(new BlockPos(10, 0, 0), new BlockPos(10, 0, 0), "second"), true);
        var schematic = LitematicaSchematic.createEmptySchematic(area, "test");
        for (String region : schematic.getAreas().keySet()) schematic.getSubRegionContainer(region).set(0, 0, 0, Blocks.STONE.defaultBlockState());
        var blockTick = new ScheduledTick<>(Blocks.STONE, BlockPos.ZERO, 10, 1);
        var fluidTick = new ScheduledTick<Fluid>(Fluids.WATER, BlockPos.ZERO, 20, 2);
        schematic.getScheduledBlockTicksForRegion("first").put(BlockPos.ZERO, blockTick);
        schematic.getScheduledFluidTicksForRegion("first").put(BlockPos.ZERO, fluidTick);
        var edit = new BlockReplacement(schematic, Blocks.STONE, Blocks.GLASS);
        edit.advance(10);
        assertEquals(2, edit.getChangedCount());
        assertTrue(edit.apply());
        for (String region : schematic.getAreas().keySet()) assertSame(Blocks.GLASS.defaultBlockState(), schematic.getSubRegionContainer(region).get(0, 0, 0));
        assertTrue(schematic.getScheduledBlockTicksForRegion("first").isEmpty());
        assertTrue(schematic.getScheduledFluidTicksForRegion("first").isEmpty());
        assertTrue(edit.undo());
        assertSame(blockTick, schematic.getScheduledBlockTicksForRegion("first").get(BlockPos.ZERO));
        assertSame(fluidTick, schematic.getScheduledFluidTicksForRegion("first").get(BlockPos.ZERO));
    }

    @Test
    void previewIsIncrementalAndDoesNotMutateTheTemplate()
    {
        var schematic = fixture();
        var original = schematic.getSubRegionContainer("room");
        var edit = new BlockReplacement(schematic, Blocks.STONE, Blocks.OAK_PLANKS);
        edit.advance(1);
        assertFalse(edit.isReady());
        assertFalse(edit.apply());
        assertSame(Blocks.STONE.defaultBlockState(), original.get(0, 0, 0));
        edit.advance(10);
        assertEquals(2, edit.getChangedCount());
        assertTrue(edit.apply());
        assertSame(Blocks.OAK_PLANKS.defaultBlockState(), schematic.getSubRegionContainer("room").get(0, 0, 0));
        assertSame(Blocks.DIRT.defaultBlockState(), schematic.getSubRegionContainer("room").get(2, 0, 0));
        assertTrue(edit.undo());
        assertSame(original, schematic.getSubRegionContainer("room"));
        assertFalse(edit.apply());
    }

    @Test
    void preservesCompatibleStateProperties()
    {
        var source = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                .setValue(BlockStateProperties.WATERLOGGED, true);
        var replacement = BlockReplacement.replacementState(source, Blocks.STONE_STAIRS);
        assertEquals(Direction.WEST, replacement.getValue(BlockStateProperties.HORIZONTAL_FACING));
        assertTrue(replacement.getValue(BlockStateProperties.WATERLOGGED));
        assertThrows(IllegalArgumentException.class, () -> BlockReplacement.replacementState(Blocks.STONE.defaultBlockState(), Blocks.OAK_DOOR));
        assertThrows(IllegalArgumentException.class, () -> BlockReplacement.replacementState(Blocks.STONE.defaultBlockState(), BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:red_bed"))));
    }

    @Test
    void stalePreviewAndStaleUndoCannotOverwriteOtherEdits()
    {
        var schematic = fixture();
        var edit = new BlockReplacement(schematic, Blocks.STONE, Blocks.GLASS);
        edit.advance(10);
        schematic.getSubRegionContainer("room").set(2, 0, 0, Blocks.GOLD_BLOCK.defaultBlockState());
        assertFalse(edit.apply());
        edit = new BlockReplacement(schematic, Blocks.STONE, Blocks.GLASS);
        edit.advance(10);
        assertTrue(edit.apply());
        schematic.getSubRegionContainer("room").set(2, 0, 0, Blocks.DIAMOND_BLOCK.defaultBlockState());
        assertFalse(edit.undo());
        assertSame(Blocks.DIAMOND_BLOCK.defaultBlockState(), schematic.getSubRegionContainer("room").get(2, 0, 0));
    }

    @Test
    void removingBlocksUpdatesTotalsAndUndoRestoresBlockData()
    {
        var schematic = fixture();
        var data = new CompoundData();
        schematic.getBlockEntityMapForRegion("room").put(BlockPos.ZERO, data);
        var edit = new BlockReplacement(schematic, Blocks.STONE, Blocks.AIR);
        edit.advance(10);
        assertTrue(edit.apply());
        assertEquals(1, schematic.getMetadata().getTotalBlocks());
        assertFalse(schematic.getBlockEntityMapForRegion("room").containsKey(BlockPos.ZERO));
        assertTrue(edit.undo());
        assertEquals(3, schematic.getMetadata().getTotalBlocks());
        assertSame(data, schematic.getBlockEntityMapForRegion("room").get(BlockPos.ZERO));
    }

    @Test
    void refusesUndoWhenItWouldOverwriteNewBlockEntityData()
    {
        var schematic = fixture();
        schematic.getBlockEntityMapForRegion("room").put(BlockPos.ZERO, new CompoundData());
        var edit = new BlockReplacement(schematic, Blocks.STONE, Blocks.CHEST);
        edit.advance(10);
        assertTrue(edit.apply());
        var newData = new CompoundData();
        schematic.getBlockEntityMapForRegion("room").put(BlockPos.ZERO, newData);
        assertFalse(edit.canUndo());
        assertFalse(edit.undo());
        assertSame(newData, schematic.getBlockEntityMapForRegion("room").get(BlockPos.ZERO));
    }
}
