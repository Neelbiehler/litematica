package fi.dy.masa.litematica.materials;

import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.ChunkSchematicState;
import fi.dy.masa.litematica.world.ChunkSchematic;
import net.minecraft.world.level.ChunkPos;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.scheduler.TaskScheduler;
import fi.dy.masa.litematica.scheduler.tasks.TaskCountBlocksPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;

public class MaterialListPlacement extends MaterialListBase
{
    private final SchematicPlacement placement;
    private long generation;
    private final Map<ChunkPos, ChunkSchematic> previousChunks = new HashMap<>();

    public long getGeneration() { return this.generation; }

    /** Called before queuing rebuilds, so a count cannot consume an old preview chunk. */
    public void invalidateForSchematicEdit()
    {
        ++this.generation;
        this.previousChunks.clear();
        var world = SchematicWorldHandler.getSchematicWorld();
        if (world != null) for (var pos : this.placement.getTouchedChunks(RequiredEnabled.PLACEMENT_ENABLED))
            this.previousChunks.put(pos, world.getChunkSource().getChunkIfExists(pos.x(), pos.z()));
        this.setMaterialListEntries(List.of());
    }

    public boolean isChunkReady(ChunkPos pos, ChunkSchematic chunk)
    {
        if (!this.previousChunks.containsKey(pos)) return true;
        if (chunk == null || chunk == this.previousChunks.get(pos) || !chunk.getState().atLeast(ChunkSchematicState.FILLED)) return false;
        this.previousChunks.remove(pos);
        return true;
    }

    public MaterialListPlacement(SchematicPlacement placement)
    {
        this(placement, false);
    }

    public MaterialListPlacement(SchematicPlacement placement, boolean reCreate)
    {
        super();

        this.placement = placement;

        if (reCreate)
        {
            this.reCreateMaterialList();
        }
    }

    @Override
    public LitematicaSchematic getEditableSchematic() { return this.placement.getSchematic(); }

    @Override
    public boolean supportsRenderLayers()
    {
        return true;
    }

    @Override
    public String getName()
    {
        return this.placement.getName();
    }

    @Override
    public String getTitle()
    {
        return StringUtils.translate("litematica.gui.title.material_list.placement", this.getName());
    }

    @Override
    public void reCreateMaterialList()
    {
        ++this.generation;
        boolean ignoreState = Configs.Generic.MATERIAL_LIST_IGNORE_STATE.getBooleanValue();
        TaskCountBlocksPlacement task = new TaskCountBlocksPlacement(this.placement, this, ignoreState);
        TaskScheduler.getInstanceClient().scheduleTask(task, 20);
        InfoUtils.showGuiOrInGameMessage(MessageType.INFO, "litematica.message.scheduled_task_added");
    }
}
