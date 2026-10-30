package fi.dy.masa.litematica.gui;

import net.minecraft.core.BlockPos;
import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.widgets.WidgetDashboardButton;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.SelectionManager;
import fi.dy.masa.litematica.util.PositionUtils.Corner;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiCaptureWorkspace extends GuiWorkflowBase
{
    public GuiCaptureWorkspace()
    {
        this.title = text("title");
    }

    @Override
    public void initGui()
    {
        super.initGui();
        int width = this.getScreenWidth() - 24;
        int height = Math.min(64, (this.getScreenHeight() - 110) / 3);
        boolean inWorld = this.mc.player != null;
        AreaSelection area = DataManager.getSelectionManager().getCurrentSelection();
        boolean hasRegion = area != null && area.getSelectedSubRegionBox() != null;
        WidgetDashboardButton first = this.addDashboardButton(12, 54, width, height, text("first"), text("first.help"), true,
                (button, mouse) -> this.setCorner(Corner.CORNER_1));
        first.setEnabled(inWorld && (area != null || !DataManager.getSchematicProjectsManager().hasProjectOpen()));
        WidgetDashboardButton second = this.addDashboardButton(12, 60 + height, width, height, text("second"), text("second.help"), false,
                (button, mouse) -> this.setCorner(Corner.CORNER_2));
        second.setEnabled(inWorld && hasRegion);
        WidgetDashboardButton save = this.addDashboardButton(12, 66 + height * 2, width, height, text("save"), text("save.help"), false,
                (button, mouse) -> GuiBase.openGui(new GuiSchematicSave().setParent(this)));
        save.setEnabled(inWorld && hasRegion);
        if (!hasRegion)
        {
            second.setHoverStrings("litematica.gui.capture.first_required");
            save.setHoverStrings("litematica.gui.capture.first_required");
        }
        if (!inWorld) first.setHoverStrings("litematica.gui.flow.enter_world");
        int third = (width - 8) / 3;
        int y = this.getScreenHeight() - 26;
        this.addDashboardButton(12, y, third, 20, text("back"), "", false, (button, mouse) -> this.closeGui(true));
        this.addDashboardButton(16 + third, y, third, 20, text("edit"), text("edit.help"), false,
                (button, mouse) -> GuiBase.openGui(DataManager.getSelectionManager().getEditGui().setParent(this)));
        WidgetDashboardButton selections = this.addDashboardButton(20 + third * 2, y, third, 20, text("selections"), "", false,
                (button, mouse) -> GuiBase.openGui(new GuiAreaSelectionManager().setParent(this)));
        if (DataManager.getSchematicProjectsManager().hasProjectOpen())
        {
            selections.setEnabled(false);
            selections.setHoverStrings("litematica.gui.button.hover.schematic_projects.area_browser_disabled_currently_in_projects_mode");
        }
    }

    private void setCorner(Corner corner)
    {
        if (this.mc.player == null) return;
        SelectionManager manager = DataManager.getSelectionManager();
        AreaSelection area = manager.getCurrentSelection();
        if (area == null)
        {
            if (DataManager.getSchematicProjectsManager().hasProjectOpen()) return;
            manager.createNewSelection(DataManager.getAreaSelectionsBaseDirectory(), text("name"));
            area = manager.getCurrentSelection();
        }
        if (area != null)
        {
            BlockPos position = BlockPos.containing(this.mc.player.position());
            if (area.getSelectedSubRegionBox() == null)
            {
                area.createNewSubRegionBox(position, text("name"));
            }
            area.setSelectedSubRegionCornerPos(position, corner);
            GuiBase.openGui(null);
        }
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        this.drawString(ctx, text("title"), 12, 12, UiTheme.TEXT);
        String hint = StringUtils.translate("litematica.gui.capture.intro", Hotkeys.OPEN_GUI_MAIN_MENU.getKeybind().getStringValue());
        this.drawString(ctx, this.font.plainSubstrByWidth(hint, this.getScreenWidth() - 24), 12, 30, UiTheme.MUTED);
    }

    private static String text(String key)
    {
        return StringUtils.translate("litematica.gui.capture." + key);
    }
}
