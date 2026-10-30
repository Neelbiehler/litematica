package fi.dy.masa.litematica.gui.widgets;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.gui.GuiSchematicSave;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.gui.GuiPlacementWorkspace;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;

public class WidgetSchematicEntry extends WidgetListEntryBase<LitematicaSchematic>
{
    private final WidgetListLoadedSchematics parent;
    private final LitematicaSchematic schematic;
    private final int typeIconX;
    private final int typeIconY;
    private final boolean isOdd;
    private final int buttonsStartX;

    public WidgetSchematicEntry(int x, int y, int width, int height, boolean isOdd,
            LitematicaSchematic schematic, int listIndex, WidgetListLoadedSchematics parent)
    {
        super(x, y, width, height, schematic, listIndex);

        this.parent = parent;
        this.schematic = schematic;
        this.isOdd = isOdd;
        y += width < 480 ? 24 : 4;

        int posX = x + width;

        posX -= this.addButton(posX, y, ButtonListener.Type.UNLOAD);
        posX -= this.addButton(posX, y, ButtonListener.Type.RELOAD);
        posX -= this.addButton(posX, y, ButtonListener.Type.SAVE_TO_FILE);
        posX -= this.addButton(posX, y, ButtonListener.Type.CREATE_PLACEMENT);

        this.buttonsStartX = posX;
        this.typeIconX = this.x + 2;
        this.typeIconY = this.y + 5;
    }

    private int addButton(int x, int y, ButtonListener.Type type)
    {
        ButtonListener listener = new ButtonListener(type, this);
        String label = this.width < 480 ? StringUtils.translate("litematica.gui.library." + type.name().toLowerCase(java.util.Locale.ROOT)) : type.getDisplayName();
        int width = this.width < 480 ? (this.width - 8) / 4 : -1;
        ButtonGeneric button = new ButtonGeneric(x, y, width, true, label);
        button.setHoverStrings(type.getDisplayName());

        if (type.getHoverKey() != null)
        {
            button.setHoverStrings(type.getHoverKey());
        }

        this.addButton(button, listener);

        return button.getWidth() + 2;
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected)
    {
        // Draw a lighter background for the hovered and the selected entry
        if (selected || this.isMouseOver(mouseX, mouseY))
        {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xFF2B3B4B);
        }
        else if (this.isOdd)
        {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xFF121C28);
        }
        // Draw a slightly lighter background for even entries
        else
        {
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, 0xFF1C2937);
        }

        boolean modified = this.schematic.getMetadata().wasModifiedSinceSaved();
        String schematicName = this.schematic.getMetadata().getName();
        int color = modified ? 0xFFFF9010 : 0xFFFFFFFF;
        int nameWidth = this.width < 480 ? this.width - 40 : this.buttonsStartX - this.x - 36;
        this.drawString(ctx, this.x + 20, this.y + 7, color,
                        this.textRenderer.plainSubstrByWidth(schematicName, Math.max(0, nameWidth)));

        Path schematicFile = this.schematic.getFile();
        String fileName = schematicFile != null ? schematicFile.getFileName().toString() : null;
        Icons icon;

        if (fileName != null)
        {
            icon = Icons.SCHEMATIC_TYPE_FILE;
        }
        else
        {
            icon = Icons.SCHEMATIC_TYPE_MEMORY;
        }

        icon.renderAt(ctx, this.typeIconX, this.typeIconY, this.zLevel, false, false);

        if (modified)
        {
            Icons.NOTICE_EXCLAMATION_11.renderAt(ctx, (this.width < 480 ? this.x + this.width - 14 : this.buttonsStartX - 13), this.y + 6, this.zLevel, false, false);
        }

        this.drawSubWidgets(ctx, mouseX, mouseY);
    }

    @Override
    public void postRenderHovered(GuiContext ctx, int mouseX, int mouseY, boolean selected)
    {
        if (this.schematic.getMetadata().wasModifiedSinceSaved() &&
            GuiBase.isMouseOver(mouseX, mouseY, (this.width < 480 ? this.x + this.width - 14 : this.buttonsStartX - 13), this.y + 6, 11, 11))
        {
            String str = WidgetFileBrowserBase.DATE_FORMAT.format(new Date(this.schematic.getMetadata().getTimeModified()));
            List<String> strs = ImmutableList.of(StringUtils.translate("litematica.gui.label.loaded_schematic.modified_on", str));
            RenderUtils.drawHoverText(ctx, mouseX, mouseY, strs);
        }
        else if (GuiBase.isMouseOver(mouseX, mouseY, this.x, this.y, (this.width < 480 ? this.width - 12 : this.buttonsStartX - this.x - 12), this.width < 480 ? 22 : this.height))
        {
            List<String> lines = new ArrayList<>();
            Path schematicFile = this.schematic.getFile();
            String fileName = schematicFile != null ? schematicFile.getFileName().toString() : null;

            if (fileName != null)
            {
                lines.add(fileName);
            }
            else
            {
                lines.add(StringUtils.translate("litematica.gui.label.schematic_placement.in_memory"));
            }

            RenderUtils.drawHoverText(ctx, mouseX, mouseY, lines);
        }

        super.postRenderHovered(ctx, mouseX, mouseY, selected);
    }

	private record ButtonListener(Type type, WidgetSchematicEntry widget)
			implements IButtonActionListener
	{
		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton)
		{
			if (this.type == Type.CREATE_PLACEMENT && this.widget.mc.player != null)
			{
				BlockPos pos = BlockPos.containing(this.widget.mc.player.position());
				LitematicaSchematic entry = this.widget.schematic;
				String name = entry.getMetadata().getName();
				boolean enabled = GuiBase.isShiftDown() == false;

				SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
				SchematicPlacement placement = SchematicPlacement.createFor(entry, pos, name, enabled, enabled);
				manager.addSchematicPlacement(placement, true);
				manager.setSelectedSchematicPlacement(placement);
                GuiPlacementWorkspace workspace = new GuiPlacementWorkspace(placement);
                workspace.setParent(GuiUtils.getCurrentScreen());
                GuiBase.openGui(workspace);
			}
			else if (this.type == Type.SAVE_TO_FILE)
			{
				GuiSchematicSave gui = new GuiSchematicSave(this.widget.schematic);
				gui.setParent(GuiUtils.getCurrentScreen());
				GuiBase.openGui(gui);
			}
			else if (this.type == Type.RELOAD)
			{
				boolean result = this.widget.schematic.readFromFile();

				if (result)
				{
					SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
					manager.getAllPlacementsOfSchematic(this.widget.schematic).forEach(manager::markChunksForRebuild);
				}
			}
			else if (this.type == Type.UNLOAD)
			{
				SchematicHolder.getInstance().removeSchematic(this.widget.schematic);
				this.widget.parent.refreshEntries();
			}
		}

		public enum Type
		{
			CREATE_PLACEMENT        ("litematica.gui.button.create_placement"),
			RELOAD                  ("litematica.gui.button.reload", "litematica.gui.button.hover.schematic_list.reload_schematic"),
			SAVE_TO_FILE            ("litematica.gui.button.save_to_file"),
			UNLOAD                  ("litematica.gui.button.unload");

			private final String translationKey;
			@Nullable
			private final String hoverKey;

			Type(String translationKey)
			{
				this(translationKey, null);
			}

			Type(String translationKey, @Nullable String hoverKey)
			{
				this.translationKey = translationKey;
				this.hoverKey = hoverKey;
			}

			@Nullable
			public String getHoverKey()
			{
				return this.hoverKey;
			}

			public String getDisplayName()
			{
				return StringUtils.translate(this.translationKey);
			}
		}
	}
}
