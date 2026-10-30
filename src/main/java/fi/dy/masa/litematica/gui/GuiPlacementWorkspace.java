package fi.dy.masa.litematica.gui;

import net.minecraft.core.BlockPos;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.widgets.WidgetDashboardButton;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.input.ScanCodes;

/** The everyday placement controls, with the full editor one click away. */
public class GuiPlacementWorkspace extends GuiWorkflowBase
{
    private final SchematicPlacement placement;
    private boolean buildStep;

    public GuiPlacementWorkspace(SchematicPlacement placement)
    {
        this.placement = placement;
        this.title = placement.getName();
    }

    @Override
    public void initGui()
    {
        super.initGui();
        int width = this.getScreenWidth() - 24;
        int half = (width - 6) / 2;
        this.addDashboardButton(12, 48, half, 24, text("align"), text("align.help"), !this.buildStep,
                (button, mouse) -> { this.buildStep = false; this.initGui(); });
        this.addDashboardButton(18 + half, 48, half, 24, text("build"), text("build.help"), this.buildStep,
                (button, mouse) -> { this.buildStep = true; this.initGui(); });
        this.addDashboardButton(this.getScreenWidth() - 108, 10, 96, 24,
                text(this.previewEnabled() ? "hide" : "show"), text("visibility.help"), !this.previewEnabled(),
                (button, mouse) -> {
                    if (this.previewEnabled()) this.placement.setRenderSchematic(false);
                    else
                    {
                        Configs.Visuals.ENABLE_RENDERING.setBooleanValue(true);
                        Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.setBooleanValue(true);
                        this.placement.setEnabled(true);
                        this.placement.setRenderSchematic(true);
                    }
                    this.initGui();
                });

        if (this.buildStep)
        {
            int height = Math.min(58, (this.getScreenHeight() - 132) / 3);
            this.addDashboardButton(12, 84, width, height, text("materials"), text("materials.help"), true,
                    (button, mouse) -> {
                        MaterialListBase materials = this.placement.getMaterialList();
                        materials.reCreateMaterialList();
                        DataManager.setMaterialList(materials);
                        this.openChild(new GuiMaterialList(materials));
                    });
            this.addDashboardButton(12, 90 + height, width, height, text("layers"), text("layers.help"), false,
                    (button, mouse) -> this.openChild(new GuiRenderLayer()));
            this.addDashboardButton(12, 96 + height * 2, width, height, text("verify"), text("verify.help"), false,
                    (button, mouse) -> this.openChild(new GuiSchematicVerifier(this.placement)));
        }
        else
        {
            for (int axis = 0; axis < 3; ++axis)
            {
                final int coordinate = axis;
                int y = 84 + axis * 28;
                this.addDashboardButton(this.getScreenWidth() - 80, y, 30, 24, "-", text("nudge.help"), false,
                        (button, mouse) -> this.nudge(coordinate, -1));
                this.addDashboardButton(this.getScreenWidth() - 44, y, 30, 24, "+", text("nudge.help"), false,
                        (button, mouse) -> this.nudge(coordinate, 1));
            }
            int third = (width - 8) / 3;
            WidgetDashboardButton move = this.addDashboardButton(12, 174, third, 24, text("move"), text("move.help"), false,
                    (button, mouse) -> {
                        if (this.mc.player != null)
                        {
                            this.placement.setOrigin(BlockPos.containing(this.mc.player.position()), this);
                            this.initGui();
                        }
                    });
            move.setEnabled(this.mc.player != null && !this.placement.isLocked());
            this.addDashboardButton(16 + third, 174, third, 24, text("rotate") + " " + this.rotationLabel(), text("rotate.help"), false,
                    (button, mouse) -> {
                        this.placement.setRotation(PositionUtils.cycleRotation(this.placement.getRotation(),
                                mouse == ScanCodes.OFFSET_MOUSE_RIGHT), this);
                        this.initGui();
                    });
            this.addDashboardButton(20 + third * 2, 174, third, 24, text("mirror") + " " + text("mirror." + this.placement.getMirror().name().toLowerCase(java.util.Locale.ROOT)), text("mirror.help"), false,
                    (button, mouse) -> {
                        this.placement.setMirror(PositionUtils.cycleMirror(this.placement.getMirror(),
                                mouse == ScanCodes.OFFSET_MOUSE_RIGHT), this);
                        this.initGui();
                    });
        }
        int third = (width - 8) / 3;
        int bottom = this.getScreenHeight() - 26;
        this.addDashboardButton(12, bottom, third, 20, text("back"), "", false,
                (button, mouse) -> this.closeGui(true));
        this.addDashboardButton(16 + third, bottom, third, 20, text("advanced"), text("advanced.help"), false,
                (button, mouse) -> this.openChild(new GuiPlacementConfiguration(this.placement)));
        this.addDashboardButton(20 + third * 2, bottom, third, 20, text(this.buildStep ? "finish" : "next"),
                text("finish.help"), true, (button, mouse) -> {
                    if (this.buildStep) GuiBase.openGui(null);
                    else { this.buildStep = true; this.initGui(); }
                });
    }

    private void nudge(int axis, int direction)
    {
        int amount = direction * (GuiBase.isShiftDown() ? 10 : 1);
        this.placement.setOrigin(this.placement.getOrigin().offset(axis == 0 ? amount : 0,
                axis == 1 ? amount : 0, axis == 2 ? amount : 0), this);
        this.initGui();
    }

    private String rotationLabel()
    {
        return switch (this.placement.getRotation())
        {
            case NONE -> "0°";
            case CLOCKWISE_90 -> "90°";
            case CLOCKWISE_180 -> "180°";
            case COUNTERCLOCKWISE_90 -> "270°";
        };
    }

    private boolean previewEnabled()
    {
        return this.placement.isEnabled() && this.placement.isRenderingEnabled() &&
               Configs.Visuals.ENABLE_RENDERING.getBooleanValue() && Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.getBooleanValue();
    }

    private void openChild(GuiBase gui)
    {
        gui.setParent(this);
        GuiBase.openGui(gui);
    }

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY)
    {
        // Keep the world visible while positioning the preview.
        RenderUtils.drawRect(ctx, 0, 0, this.getScreenWidth(), this.getScreenHeight(), 0x80101823);
        RenderUtils.drawRect(ctx, 0, 0, this.getScreenWidth(), 78, UiTheme.BACKGROUND);
        RenderUtils.drawRect(ctx, 0, this.getScreenHeight() - 32, this.getScreenWidth(), 32, UiTheme.BACKGROUND);
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        this.drawString(ctx, this.font.plainSubstrByWidth(this.placement.getName(), this.getScreenWidth() - 132), 12, 12, UiTheme.TEXT);
        String hint = text(this.placement.isLocked() ? "locked" : this.buildStep ? "build.help" : "align.help");
        this.drawString(ctx, this.font.plainSubstrByWidth(hint, this.getScreenWidth() - 24), 12, 34, UiTheme.MUTED);
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        if (!this.buildStep)
        {
            BlockPos origin = this.placement.getOrigin();
            int[] coordinates = { origin.getX(), origin.getY(), origin.getZ() };
            for (int axis = 0; axis < 3; ++axis)
            {
                String label = "XYZ".charAt(axis) + "   " + coordinates[axis];
                this.drawString(ctx, label, 22, 92 + axis * 28, UiTheme.TEXT);
            }
        }
    }

    private static String text(String key)
    {
        return StringUtils.translate("litematica.gui.workspace." + key);
    }
}
