package fi.dy.masa.litematica.gui;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiPreferences extends GuiWorkflowBase
{
    public GuiPreferences()
    {
        this.title = text("title");
    }

    @Override
    public void initGui()
    {
        super.initGui();
        int width = (this.getScreenWidth() - 32) / 2;
        int height = Math.min(60, (this.getScreenHeight() - 108) / 3);
        this.toggle(12, 52, width, height, "translucent", Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT);
        this.toggle(20 + width, 52, width, height, "boxes", Configs.Visuals.ENABLE_AREA_SELECTION_RENDERING);
        this.toggle(12, 58 + height, width, height, "easy_place", Configs.Generic.EASY_PLACE_MODE);
        this.toggle(20 + width, 58 + height, width, height, "overlay", Configs.Visuals.ENABLE_RENDERING);
        this.addDashboardButton(12, 64 + height * 2, width, height, text("keys"), text("keys.help"), false,
                (button, mouse) -> this.openConfig(GuiConfigs.ConfigGuiTab.HOTKEYS));
        this.addDashboardButton(20 + width, 64 + height * 2, width, height, text("advanced"), text("advanced.help"), false,
                (button, mouse) -> this.openConfig(GuiConfigs.ConfigGuiTab.GENERIC));
        this.addDashboardButton(12, this.getScreenHeight() - 26, 100, 20, text("back"), "", false,
                (button, mouse) -> this.closeGui(true));
    }

    private void toggle(int x, int y, int width, int height, String key, ConfigBoolean config)
    {
        String state = StringUtils.translate("litematica.message.value." + (config.getBooleanValue() ? "on" : "off"));
        this.addDashboardButton(x, y, width, height, text(key) + ": " + state, text(key + ".help"), config.getBooleanValue(),
                (button, mouse) -> { config.setBooleanValue(!config.getBooleanValue()); this.initGui(); });
    }

    private void openConfig(GuiConfigs.ConfigGuiTab tab)
    {
        DataManager.setConfigGuiTab(tab);
        GuiBase.openGui(new GuiConfigs().setParent(this));
    }

    @Override
    public void removed()
    {
        super.removed();
        Configs.saveToFile();
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        this.drawString(ctx, text("title"), 12, 12, UiTheme.TEXT);
        this.drawString(ctx, this.font.plainSubstrByWidth(text("intro"), this.getScreenWidth() - 24), 12, 30, UiTheme.MUTED);
    }

    private static String text(String key)
    {
        return StringUtils.translate("litematica.gui.preferences." + key);
    }
}
