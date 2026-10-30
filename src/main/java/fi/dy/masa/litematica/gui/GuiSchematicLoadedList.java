package fi.dy.masa.litematica.gui;

import fi.dy.masa.litematica.gui.GuiMainMenu.ButtonListenerChangeMenu;
import fi.dy.masa.litematica.gui.widgets.WidgetListLoadedSchematics;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicEntry;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.litematica.gui.widgets.WidgetDashboardButton;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiSchematicLoadedList extends GuiListBase<LitematicaSchematic, WidgetSchematicEntry, WidgetListLoadedSchematics>
{
    public GuiSchematicLoadedList()
    {
        super(12, 52);

        this.title = StringUtils.translate("litematica.gui.title.manage_loaded_schematics");
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
                ButtonListenerChangeMenu.ButtonType.SCHEMATIC_PLACEMENTS,
                ButtonListenerChangeMenu.ButtonType.MAIN_MENU };
        String[] keys = { "load", "placements", "home" };
        for (int i = 0; i < targets.length; ++i)
        {
            WidgetDashboardButton button = new WidgetDashboardButton(12 + i * (width + 4), this.getScreenHeight() - 26,
                    width, 20, StringUtils.translate("litematica.gui.flow." + keys[i]), "", i == 0);
            this.addButton(button, new ButtonListenerChangeMenu(targets[i], this.getParent()));
        }
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawTitle(ctx, mouseX, mouseY, partialTicks);
        String hint = StringUtils.translate("litematica.gui.flow.loaded.intro");
        this.drawString(ctx, this.font.plainSubstrByWidth(hint, this.getScreenWidth() - 24), 12, 30, UiTheme.MUTED);
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        if (SchematicHolder.getInstance().getAllSchematics().isEmpty())
        {
            String hint = StringUtils.translate("litematica.gui.flow.loaded.empty");
            this.drawString(ctx, this.font.plainSubstrByWidth(hint, this.getScreenWidth() - 40), 20, 90, UiTheme.MUTED);
        }
    }

    @Override
    protected WidgetListLoadedSchematics createListWidget(int listX, int listY)
    {
        return new WidgetListLoadedSchematics(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), null);
    }
}
