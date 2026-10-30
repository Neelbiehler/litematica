package fi.dy.masa.litematica.gui;

import javax.annotation.Nullable;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMainMenu.ButtonListenerChangeMenu;
import fi.dy.masa.litematica.gui.widgets.WidgetListSchematicPlacements;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.litematica.gui.widgets.WidgetDashboardButton;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiSchematicPlacementsList extends GuiListBase<SchematicPlacement, WidgetSchematicPlacement, WidgetListSchematicPlacements> implements ISelectionListener<SchematicPlacement>
{
    public final SchematicPlacementManager manager;

    public GuiSchematicPlacementsList()
    {
        super(12, 52);

        this.title = StringUtils.translate("litematica.gui.title.manage_schematic_placements");
        this.manager = DataManager.getSchematicPlacementManager();
    }

    @Override
    protected int getBrowserWidth()
    {
        return this.getScreenWidth() - 20;
    }

    @Override
    protected int getBrowserHeight()
    {
        return this.getScreenHeight() - 90;
    }

    @Override
    public void initGui()
    {
        super.initGui();

        int width = (this.getScreenWidth() - 32) / 3;
        ButtonListenerChangeMenu.ButtonType[] targets = {
                ButtonListenerChangeMenu.ButtonType.LOAD_SCHEMATICS,
                ButtonListenerChangeMenu.ButtonType.LOADED_SCHEMATICS,
                ButtonListenerChangeMenu.ButtonType.MAIN_MENU };
        String[] keys = { "load", "loaded", "home" };
        for (int i = 0; i < targets.length; ++i)
        {
            WidgetDashboardButton button = new WidgetDashboardButton(12 + i * (width + 4), this.getScreenHeight() - 26,
                    width, 20, StringUtils.translate("litematica.gui.flow." + keys[i]), "", i == 0);
            this.addButton(button, new ButtonListenerChangeMenu(targets[i], this.getParent()));
        }
    }

    @Override
    public void onSelectionChange(@Nullable SchematicPlacement entry)
    {
        this.manager.setSelectedSchematicPlacement(entry != this.manager.getSelectedSchematicPlacement() ? entry : null);
    }

    @Override
    protected ISelectionListener<SchematicPlacement> getSelectionListener()
    {
        return this;
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawTitle(ctx, mouseX, mouseY, partialTicks);
        String hint = StringUtils.translate("litematica.gui.flow.placements.intro");
        this.drawString(ctx, this.font.plainSubstrByWidth(hint, this.getScreenWidth() - 24), 12, 30, UiTheme.MUTED);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        if (this.manager.getAllSchematicsPlacements().isEmpty())
        {
            String hint = StringUtils.translate("litematica.gui.flow.placements.empty");
            this.drawString(ctx, this.font.plainSubstrByWidth(hint, this.getScreenWidth() - 40), 20, 90, UiTheme.MUTED);
        }
    }

    @Override
    protected WidgetListSchematicPlacements createListWidget(int listX, int listY)
    {
        return new WidgetListSchematicPlacements(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this);
    }
}
