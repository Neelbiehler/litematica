package fi.dy.masa.litematica.gui;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import fi.dy.masa.litematica.Reference;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.gui.widgets.WidgetDashboardButton;
import fi.dy.masa.litematica.selection.SelectionMode;
import fi.dy.masa.litematica.tool.ToolMode;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.input.ScanCodes;

public class GuiMainMenu extends GuiBase
{
    private final List<WidgetDashboardButton> dashboardButtons = new ArrayList<>();
    private Section section = Section.BUILD;
    private int focusedIndex = -1;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int sidebarWidth;

    public GuiMainMenu()
    {
        this.title = StringUtils.translate("litematica.gui.title.litematica_main_menu", "v" + Reference.MOD_VERSION);
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.dashboardButtons.clear();
        this.panelWidth = Math.min(840, this.getScreenWidth() - 24);
        this.panelHeight = Math.min(440, this.getScreenHeight() - 24);
        this.panelX = (this.getScreenWidth() - this.panelWidth) / 2;
        this.panelY = (this.getScreenHeight() - this.panelHeight) / 2;
        this.sidebarWidth = this.panelWidth < 460 ? 88 : 128;
        int contentX = this.panelX + this.sidebarWidth + 12;
        int contentWidth = this.panelWidth - this.sidebarWidth - 24;
        int y = this.panelY + 54;
        int cardHeight = Math.min(68, (this.panelHeight - 108) / 3);

        for (Section item : Section.values())
        {
            this.addDashboardButton(this.panelX + 8, y, this.sidebarWidth - 16, 26,
                    text("nav." + item.key), "", this.section == item, (button, mouseButton) -> {
                        this.section = item;
                        this.initGui();
                    });
            y += 32;
        }

        this.addDashboardButton(this.panelX + 8, y, this.sidebarWidth - 16, 26,
                text("nav.settings"), "", false,
                new ButtonListenerChangeMenu(ButtonListenerChangeMenu.ButtonType.CONFIGURATION, this));
        this.addDashboardButton(this.panelX + 8, this.panelY + this.panelHeight - 34,
                this.sidebarWidth - 16, 24, text("back"), "", false,
                (button, mouseButton) -> this.closeGui(true));

        y = this.panelY + 54;
        switch (this.section)
        {
            case BUILD:
                this.createAction(contentX, y, contentWidth, cardHeight, "load", true,
                                  ButtonListenerChangeMenu.ButtonType.LOAD_SCHEMATICS);
                this.createAction(contentX, y + cardHeight + 6, contentWidth, cardHeight, "loaded", false,
                                  ButtonListenerChangeMenu.ButtonType.LOADED_SCHEMATICS);
                this.createAction(contentX, y + (cardHeight + 6) * 2, contentWidth, cardHeight, "placements", false,
                                  ButtonListenerChangeMenu.ButtonType.SCHEMATIC_PLACEMENTS);
                break;
            case CREATE:
                this.createAction(contentX, y, contentWidth, cardHeight, "select", true,
                                  ButtonListenerChangeMenu.ButtonType.AREA_EDITOR);
                this.createAction(contentX, y + cardHeight + 6, contentWidth, cardHeight, "selections", false,
                                  ButtonListenerChangeMenu.ButtonType.AREA_SELECTION_BROWSER);
                SelectionMode mode = DataManager.getSelectionManager().getSelectionMode();
                WidgetDashboardButton areaMode = this.addDashboardButton(contentX, y + (cardHeight + 6) * 2,
                        contentWidth, cardHeight, StringUtils.translate("litematica.gui.button.area_selection_mode", mode.getDisplayName()),
                        text("area_mode.description"), false, new ButtonListenerCycleAreaMode(this));
                areaMode.setHoverStrings("litematica.gui.main_menu.help.area_mode");
                break;
            case MANAGE:
                this.createAction(contentX, y, contentWidth, cardHeight, "files", false,
                                  ButtonListenerChangeMenu.ButtonType.SCHEMATIC_MANAGER);
                this.createAction(contentX, y + cardHeight + 6, contentWidth, cardHeight, "tasks", false,
                                  ButtonListenerChangeMenu.ButtonType.TASK_MANAGER);
                if (Configs.Generic.UNHIDE_SCHEMATIC_PROJECTS.getBooleanValue())
                {
                    this.createAction(contentX, y + (cardHeight + 6) * 2, contentWidth, cardHeight, "projects", false,
                                      ButtonListenerChangeMenu.ButtonType.SCHEMATIC_PROJECTS_MANAGER);
                }
                break;
        }

        String toolLabel = StringUtils.translate("litematica.gui.button.tool_mode", DataManager.getToolMode().getName());
        WidgetDashboardButton toolMode = this.addDashboardButton(contentX, this.panelY + this.panelHeight - 34,
                contentWidth, 24, toolLabel, "", false, new ButtonListenerCycleToolMode(this));
        toolMode.setHoverStrings(toolLabel, "litematica.gui.main_menu.help.tool_mode");
        if (this.focusedIndex >= this.dashboardButtons.size())
        {
            this.focusedIndex = -1;
        }
        this.updateKeyboardFocus();
    }

    private WidgetDashboardButton addDashboardButton(int x, int y, int width, int height, String title,
            String description, boolean accent, IButtonActionListener listener)
    {
        WidgetDashboardButton button = new WidgetDashboardButton(x, y, width, height, title, description, accent);
        this.dashboardButtons.add(button);
        this.addButton(button, listener);
        return button;
    }

    private void createAction(int x, int y, int width, int height, String key, boolean primary,
                              ButtonListenerChangeMenu.ButtonType type)
    {
        WidgetDashboardButton button = this.addDashboardButton(x, y, width, height, text(key + ".title"),
                text(key + ".description"), primary, new ButtonListenerChangeMenu(type, this));
        if (type == ButtonListenerChangeMenu.ButtonType.AREA_SELECTION_BROWSER &&
            DataManager.getSchematicProjectsManager().hasProjectOpen())
        {
            button.setEnabled(false);
            button.setHoverStrings("litematica.gui.button.hover.schematic_projects.area_browser_disabled_currently_in_projects_mode");
        }
        else if (type == ButtonListenerChangeMenu.ButtonType.SCHEMATIC_PROJECTS_MANAGER)
        {
            button.setHoverStrings("litematica.gui.button.hover.schematic_projects.menu_warning");
        }
        else
        {
            button.setHoverStrings(type.getLabelKey() + ".help");
        }
    }

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY)
    {
        RenderUtils.drawRect(ctx, 0, 0, this.getScreenWidth(), this.getScreenHeight(), 0xE80A1018);
        WidgetDashboardButton.drawSurface(ctx, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 0xFF121C28);
        RenderUtils.drawRect(ctx, this.panelX + this.sidebarWidth, this.panelY + 12,
                             1, this.panelHeight - 24, 0xFF2B3949);
        RenderUtils.drawRect(ctx, this.panelX + 12, this.panelY + 39, 20, 2, 0xFF6EDBC4);
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        this.drawString(ctx, "LITEMATICA", this.panelX + 12, this.panelY + 16, 0xFFF1F5F9);
        int x = this.panelX + this.sidebarWidth + 12;
        int width = this.panelWidth - this.sidebarWidth - 24;
        this.drawFittedLabel(ctx, text("heading." + this.section.key), x, this.panelY + 14, width, 0xFFF1F5F9);
        String subtitle = this.section == Section.BUILD
                ? StringUtils.translate("litematica.gui.dashboard.status",
                        SchematicHolder.getInstance().getAllSchematics().size(),
                        DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().size())
                : text("intro." + this.section.key);
        this.drawFittedLabel(ctx, subtitle, x, this.panelY + 30, width, 0xFFB1C0CF);
    }

    @Override
    protected void drawButtonHoverTexts(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawButtonHoverTexts(ctx, mouseX, mouseY, partialTicks);
        if (this.shouldRenderHoverStuff() && this.focusedIndex >= 0 &&
            this.dashboardButtons.stream().noneMatch(WidgetDashboardButton::isMouseOver))
        {
            this.dashboardButtons.get(this.focusedIndex).drawKeyboardHelp(ctx);
        }
    }

    private void drawFittedLabel(GuiContext ctx, String label, int x, int y, int width, int color)
    {
        if (this.getStringWidth(label) > width)
        {
            label = this.font.plainSubstrByWidth(label, Math.max(0, width - this.getStringWidth("..."))) + "...";
        }
        this.drawString(ctx, label, x, y, color);
    }

    @Override
    public boolean onKeyTyped(KeyEvent input)
    {
        if (input.key() == ScanCodes.SCAN_TAB)
        {
            int direction = input.hasShiftDown() ? -1 : 1;
            if (this.focusedIndex < 0)
            {
                this.focusedIndex = direction > 0 ? -1 : 0;
            }
            do
            {
                this.focusedIndex = Math.floorMod(this.focusedIndex + direction, this.dashboardButtons.size());
            }
            while (this.dashboardButtons.get(this.focusedIndex).isEnabled() == false);
            this.updateKeyboardFocus();
            return true;
        }
        if (this.focusedIndex >= 0 && (input.key() == ScanCodes.SCAN_RETURN ||
            input.key() == ScanCodes.SCAN_KP_ENTER || input.key() == ScanCodes.SCAN_SPACE))
        {
            this.dashboardButtons.get(this.focusedIndex).activate(input.hasShiftDown());
            return true;
        }
        return super.onKeyTyped(input);
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick)
    {
        this.focusedIndex = -1;
        this.updateKeyboardFocus();
        return super.onMouseClicked(click, doubleClick);
    }

    private void updateKeyboardFocus()
    {
        for (int i = 0; i < this.dashboardButtons.size(); ++i)
        {
            this.dashboardButtons.get(i).setKeyboardFocused(i == this.focusedIndex);
        }
    }

    private static String text(String key)
    {
        return StringUtils.translate("litematica.gui.dashboard." + key);
    }

    private enum Section
    {
        BUILD("build"), CREATE("create"), MANAGE("manage");

        private final String key;

        Section(String key)
        {
            this.key = key;
        }
    }

    public static class ButtonListenerChangeMenu implements IButtonActionListener
    {
        private final ButtonType type;
        @Nullable
        private final Screen parent;

        public ButtonListenerChangeMenu(ButtonType type, @Nullable Screen parent)
        {
            this.type = type;
            this.parent = parent;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            GuiBase gui = null;

            switch (this.type)
            {
                case AREA_EDITOR:
                    gui = DataManager.getSelectionManager().getEditGui();
                    break;
                case AREA_SELECTION_BROWSER:
                    gui = new GuiAreaSelectionManager();
                    break;
                case CONFIGURATION:
                    gui = new GuiConfigs();
                    break;
                case LOAD_SCHEMATICS:
                    gui = new GuiSchematicLoad();
                    break;
                case LOADED_SCHEMATICS:
                    gui = new GuiSchematicLoadedList();
                    break;
                case MAIN_MENU:
                    gui = new GuiMainMenu();
                    break;
                case SCHEMATIC_MANAGER:
                    gui = new GuiSchematicManager();
                    break;
                case SCHEMATIC_PLACEMENTS:
                    gui = new GuiSchematicPlacementsList();
                    break;
                case TASK_MANAGER:
                    gui = new GuiTaskManager();
                    break;
                case SCHEMATIC_PROJECTS_MANAGER:
                    DataManager.getSchematicProjectsManager().openSchematicProjectsGui();
                    return;
            }

            if (gui != null)
            {
                gui.setParent(this.parent);
                GuiBase.openGui(gui);
            }
        }

        public enum ButtonType
        {
            // List loaded Schematics in SchematicHolder
            LOADED_SCHEMATICS           ("litematica.gui.button.change_menu.show_loaded_schematics", ButtonIcons.LOADED_SCHEMATICS),
            // List Schematics placements
            SCHEMATIC_PLACEMENTS        ("litematica.gui.button.change_menu.show_schematic_placements", ButtonIcons.SCHEMATIC_PLACEMENTS),
            // Open the Area Selection browser
            AREA_SELECTION_BROWSER      ("litematica.gui.button.change_menu.show_area_selections", ButtonIcons.AREA_SELECTION),
            // Open the Area Editor GUI
            AREA_EDITOR                 ("litematica.gui.button.change_menu.area_editor", ButtonIcons.AREA_EDITOR),
            // Load Schematics from file to memory
            LOAD_SCHEMATICS             ("litematica.gui.button.change_menu.load_schematics_to_memory", ButtonIcons.SCHEMATIC_BROWSER),
            // Edit Schematics (description or icon), or convert between formats
            SCHEMATIC_MANAGER           ("litematica.gui.button.change_menu.schematic_manager", ButtonIcons.SCHEMATIC_MANAGER),
            // Open the Task Manager
            TASK_MANAGER                ("litematica.gui.button.change_menu.task_manager", ButtonIcons.TASK_MANAGER),
            // Open the Schematic Projects browser
            SCHEMATIC_PROJECTS_MANAGER  ("litematica.gui.button.change_menu.schematic_projects_manager", ButtonIcons.SCHEMATIC_PROJECTS),
            // In-game Configuration GUI
            CONFIGURATION               ("litematica.gui.button.change_menu.configuration_menu", ButtonIcons.CONFIGURATION),
            // Switch to the Litematica main menu
            MAIN_MENU                   ("litematica.gui.button.change_menu.to_main_menu", null);

            private final String labelKey;
            private final ButtonIcons icon;

            ButtonType(String labelKey, ButtonIcons icon)
            {
                this.labelKey = labelKey;
                this.icon = icon;
            }

            public String getLabelKey()
            {
                return this.labelKey;
            }

            public String getDisplayName()
            {
                return StringUtils.translate(this.getLabelKey());
            }

            public ButtonIcons getIcon()
            {
                return this.icon;
            }
        }
    }

    private record ButtonListenerCycleToolMode(GuiMainMenu gui) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            ToolMode mode = DataManager.getToolMode().cycle(Minecraft.getInstance().player, mouseButton == ScanCodes.OFFSET_MOUSE_LEFT);
            DataManager.setToolMode(mode);
            this.gui.initGui();
        }
    }

    private record ButtonListenerCycleAreaMode(GuiMainMenu gui) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            DataManager.getSelectionManager().switchSelectionMode();
            this.gui.initGui();
        }
    }
}
