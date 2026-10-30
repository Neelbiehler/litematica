package fi.dy.masa.litematica.gui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.jspecify.annotations.NonNull;

import net.minecraft.core.BlockPos;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmFileDrop;
import fi.dy.masa.malilib.gui.GuiStringListSelection;
import fi.dy.masa.malilib.gui.GuiTextInputFeedback;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.interfaces.IStringListConsumer;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntry;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.file_ops.FileCopierMulti;
import fi.dy.masa.malilib.util.file_ops.FileRenamer;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.gui.GuiMainMenu.ButtonListenerChangeMenu;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicBrowser;
import fi.dy.masa.litematica.gui.widgets.WidgetDashboardButton;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.litematica.materials.MaterialListCustom;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.util.FileType;
import fi.dy.masa.litematica.util.WorldUtils;

public class GuiSchematicLoad extends GuiSchematicBrowserBase implements ISelectionListener<DirectoryEntry>
{
    public GuiSchematicLoad()
    {
        super(12, 52);

        this.title = StringUtils.translate("litematica.gui.title.load_schematic");
    }

    @Override
    public String getBrowserContext()
    {
        return "schematic_load";
    }

    @Override
    public Path getDefaultDirectory()
    {
        return DataManager.getSchematicsBaseDirectory();
    }

    @Override
    public int getMaxInfoHeight()
    {
        return this.getBrowserHeight() + 10;
    }

    @Override
    public void initGui()
    {
        super.initGui();

        this.createButtons();
    }

    @Override
    protected int getBrowserHeight()
    {
        return this.getScreenHeight() - 116;
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        this.drawString(ctx, StringUtils.translate("litematica.gui.flow.choose"), 12, 12, UiTheme.TEXT);
        String hint = StringUtils.translate("litematica.gui.flow.choose.help");
        this.drawString(ctx, this.font.plainSubstrByWidth(hint, this.getScreenWidth() - 24), 12, 30, UiTheme.MUTED);
    }

    private void createButtons()
    {
        DirectoryEntry entry = this.getListWidget().getLastSelectedEntry();
        FileType fileType = entry != null && Files.isRegularFile(entry.getFullPath()) ? FileType.fromFile(entry.getFullPath()) : null;
        boolean schematic = fileType == FileType.LITEMATICA_SCHEMATIC || fileType == FileType.SPONGE_SCHEMATIC ||
                            fileType == FileType.SCHEMATICA_SCHEMATIC || fileType == FileType.VANILLA_STRUCTURE;
        boolean material = schematic || fileType == FileType.TEXT || fileType == FileType.JSON;
        int width = (this.getScreenWidth() - 32) / 3;
        int y = this.getScreenHeight() - 52;
        this.addLoadAction(12, y, width, "place", ButtonListener.Type.LOAD_AND_PLACE, schematic && this.mc.player != null, true);
        this.addLoadAction(16 + width, y, width, "load_only", ButtonListener.Type.LOAD_SCHEMATIC, schematic, false);
        this.addLoadAction(20 + width * 2, y, width, "materials", ButtonListener.Type.MATERIAL_LIST, material, false);
        y += 26;
        ButtonListenerChangeMenu.ButtonType[] targets = {
                ButtonListenerChangeMenu.ButtonType.SCHEMATIC_MANAGER,
                ButtonListenerChangeMenu.ButtonType.LOADED_SCHEMATICS,
                ButtonListenerChangeMenu.ButtonType.MAIN_MENU };
        String[] keys = { "files", "loaded", "home" };
        for (int i = 0; i < targets.length; ++i)
        {
            WidgetDashboardButton button = new WidgetDashboardButton(12 + i * (width + 4), y, width, 20,
                    StringUtils.translate("litematica.gui.flow." + keys[i]), "", false);
            this.addButton(button, new ButtonListenerChangeMenu(targets[i], this.getParent()));
        }
    }

    private void addLoadAction(int x, int y, int width, String key, ButtonListener.Type type, boolean enabled, boolean primary)
    {
        WidgetDashboardButton button = new WidgetDashboardButton(x, y, width, 22,
                StringUtils.translate("litematica.gui.flow." + key), "", primary);
        button.setEnabled(enabled);
        button.setHoverStrings("litematica.gui.flow." + key + ".help");
        if (!enabled) button.setHoverStrings(this.mc.player == null && type == ButtonListener.Type.LOAD_AND_PLACE
                ? "litematica.gui.flow.enter_world" : "litematica.gui.flow.select_file");
        this.addButton(button, new ButtonListener(type, this));
    }

	@Override
	public void onSelectionChange(@Nullable WidgetFileBrowserBase.DirectoryEntry entry)
	{
		this.clearButtons();
		this.createButtons();
	}

	@Override
	protected ISelectionListener<DirectoryEntry> getSelectionListener()
	{
		return this;
	}

	@Override
	public boolean onMouseDropFiles(@NonNull List<Path> files)
	{
		if (this.getListWidget() != null)
		{
			Path dest;

			if (this.getListWidget().getLastSelectedEntry() != null && Files.isDirectory(this.getListWidget().getLastSelectedEntry().getFullPath()))
			{
				dest = this.getListWidget().getLastSelectedEntry().getFullPath();
			}
			else if (this.getListWidget().getCurrentDirectory() != null && Files.isDirectory(this.getListWidget().getCurrentDirectory()))
			{
				dest = this.getListWidget().getCurrentDirectory();
			}
			else
			{
				return false;
			}

			if (Files.isDirectory(dest) && Files.isWritable(dest))
			{
				FileCopierMulti copier = new FileCopierMulti(dest, this.getListWidget(), Configs.Generic.DISPLAY_FILE_OPS_FEEDBACK.getBooleanValue());
				GuiBase.openGui(new GuiConfirmFileDrop<>(256, "malilib.gui.title.file_drop_confirm", files, copier, WidgetSchematicBrowser.SCHEMATIC_FILTER, this,
				                                         "malilib.message.file_drop_confirm", files.size(), dest.toAbsolutePath().toString()));
				return true;
			}
		}

		return false;
	}

	private record ButtonListener(Type type, GuiSchematicLoad gui) implements IButtonActionListener
	{
		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton)
		{
			if (this.gui.getListWidget() == null) { return; }
			DirectoryEntry entry = this.gui.getListWidget().getLastSelectedEntry();

			if (entry == null)
			{
				this.gui.addMessage(MessageType.ERROR, "litematica.error.schematic_load.no_schematic_selected");
			}
			else
			{
				Path file = entry.getFullPath();

				if (!Files.exists(file) || !Files.isReadable(file))
				{
					this.gui.addMessage(MessageType.ERROR, "litematica.error.schematic_load.cant_read_file", file.getFileName());
					return;
				}

				this.gui.setNextMessageType(MessageType.ERROR);
				LitematicaSchematic schematic = null;
				FileType fileType = FileType.fromFile(entry.getFullPath());
				boolean warnType = false;

				// Handle custom item list files for material list button
				if (this.type == Type.MATERIAL_LIST && (fileType == FileType.JSON || fileType == FileType.TEXT))
				{
					MaterialListCustom customList = MaterialListCustom.fromFile(file);

					if (customList != null)
					{
						DataManager.setMaterialList(customList);
						GuiBase.openGui(new GuiMaterialList(customList));
						this.gui.addMessage(MessageType.SUCCESS, "litematica.info.material_list.custom_loaded", file.getFileName());
					}
					else
					{
						this.gui.addMessage(MessageType.ERROR, "litematica.error.material_list.custom_load_failed", file.getFileName());
					}

					return;
				}

				if (fileType == FileType.LITEMATICA_SCHEMATIC)
				{
					schematic = LitematicaSchematic.createFromFile(entry.getDirectory(), entry.name());
				}
				else if (fileType == FileType.SCHEMATICA_SCHEMATIC)
				{
					schematic
							= WorldUtils.convertSchematicaSchematicToLitematicaSchematic(entry.getDirectory(), entry.name(), false, this.gui);
					warnType = true;
				}
				else if (fileType == FileType.VANILLA_STRUCTURE)
				{
					schematic = WorldUtils.convertStructureToLitematicaSchematic(entry.getDirectory(), entry.name());
					warnType = true;
				}
				else if (fileType == FileType.SPONGE_SCHEMATIC)
				{
					schematic
							= WorldUtils.convertSpongeSchematicToLitematicaSchematic(entry.getDirectory(), entry.name());
					warnType = true;
				}
				else
				{
					this.gui.addMessage(MessageType.ERROR, "litematica.error.schematic_load.unsupported_type", file.getFileName());
				}

				if (schematic != null)
				{
					if (this.type == Type.LOAD_SCHEMATIC || this.type == Type.LOAD_AND_PLACE)
					{
						SchematicHolder.getInstance().addSchematic(schematic, true);
						this.gui.addMessage(MessageType.SUCCESS, "litematica.info.schematic_load.schematic_loaded", file.getFileName());

						if (this.type == Type.LOAD_AND_PLACE && this.gui.mc.player != null)
						{
							BlockPos pos = BlockPos.containing(this.gui.mc.player.position());
							String name = schematic.getMetadata().getName();
							boolean enabled = true;

							SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
							SchematicPlacement placement
									= SchematicPlacement.createFor(schematic, pos, name, enabled, enabled);
							manager.addSchematicPlacement(placement, true);
							manager.setSelectedSchematicPlacement(placement);
                            GuiPlacementWorkspace workspace = new GuiPlacementWorkspace(placement);
                            workspace.setParent(this.gui);
                            GuiBase.openGui(workspace);
						}
					}
					else if (this.type == Type.MATERIAL_LIST)
					{
						if (GuiBase.isShiftDown())
						{
							MaterialListCreator creator = new MaterialListCreator(schematic);
							GuiStringListSelection gui = new GuiStringListSelection(schematic.getAreas()
							                                                                 .keySet(), creator);
							gui.setTitle(StringUtils.translate("litematica.gui.title.material_list.select_schematic_regions", schematic.getMetadata()
							                                                                                                           .getName()));
							gui.setParent(GuiUtils.getCurrentScreen());
							GuiBase.openGui(gui);
						}
						else
						{
							MaterialListSchematic materialList = new MaterialListSchematic(schematic, true);
							DataManager.setMaterialList(materialList); // Remember the last opened material list for the hotkey to (re-) open it
							GuiBase.openGui(new GuiMaterialList(materialList));
						}
					}
					else if (this.type == Type.RENAME_SCHEMATIC)
					{
						String oldName = schematic.getMetadata().getName();
						GuiBase.openGui(new GuiTextInputFeedback(256, "litematica.gui.title.rename_schematic", oldName, this.gui, new SchematicRenamer(entry.getDirectory(), entry.name(), this.gui)));
					}
					else if (this.type == Type.RENAME_FILE)
					{
						FileRenamer renamer = new FileRenamer(file, this.gui.getListWidget(), Configs.Generic.DISPLAY_FILE_OPS_FEEDBACK.getBooleanValue());
						GuiBase.openGui(new GuiTextInputFeedback(256, "litematica.gui.title.rename_file", entry.name(), this.gui, renamer));
					}

					if (warnType)
					{
						InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, 15000, "litematica.message.warn.schematic_load_non_litematica");
					}
				}
			}
		}

		private record SchematicRenamer(Path dir, String fileName, GuiSchematicLoad gui)
				implements IStringConsumerFeedback
		{
			@Override
			public boolean setString(String string)
			{
				LitematicaSchematic schematic = LitematicaSchematic.createFromFile(this.dir, this.fileName);

				if (schematic != null)
				{
					schematic.getMetadata().setName(string);
					schematic.getMetadata().setTimeModifiedToNow();

					if (schematic.writeToFile(this.dir, this.fileName, true))
					{
						if (this.gui.getListWidget() != null)
						{
							this.gui.getListWidget().clearSchematicMetadataCache();
						}

						return true;
					}
				}
				else
				{
					this.gui.setString(StringUtils.translate("litematica.error.schematic_rename.read_failed"));
				}

				return false;
			}
		}

		public enum Type
		{
			LOAD_AND_PLACE      ("litematica.gui.flow.place"),
            LOAD_SCHEMATIC      ("litematica.gui.button.load_schematic_to_memory"),
			MATERIAL_LIST       ("litematica.gui.button.material_list"),
			RENAME_SCHEMATIC    ("litematica.gui.button.rename_schematic"),
			RENAME_FILE         ("litematica.gui.button.rename_file"),
			;

			private final String translationKey;

			Type(String translationKey)
			{
				this.translationKey = translationKey;
			}

			public String getTranslationKey()
			{
				return this.translationKey;
			}
		}
	}

	private record MaterialListCreator(LitematicaSchematic schematic) implements IStringListConsumer
	{
		@Override
		public boolean consume(Collection<String> strings)
		{
			MaterialListSchematic materialList = new MaterialListSchematic(this.schematic, strings, true);
			DataManager.setMaterialList(materialList); // Remember the last opened material list for the hotkey to (re-) open it
			GuiBase.openGui(new GuiMaterialList(materialList));

			return true;
		}
	}
}
