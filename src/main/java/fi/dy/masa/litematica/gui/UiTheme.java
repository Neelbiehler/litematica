package fi.dy.masa.litematica.gui;

import net.minecraft.client.gui.screens.Screen;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;

public final class UiTheme
{
    public static final int BACKGROUND = 0xF5101823;
    public static final int SURFACE = 0xFF1C2937;
    public static final int HOVER = 0xFF2B3B4B;
    public static final int BORDER = 0xFF344250;
    public static final int ACCENT = 0xFF6EDBC4;
    public static final int TEXT = 0xFFF1F5F9;
    public static final int MUTED = 0xFFB1C0CF;

    private UiTheme() {}

    public static boolean appliesTo(Screen screen)
    {
        // Include library-owned confirmation/input dialogs opened from this mod,
        // without changing other mods which also use MaLiLib.
        for (int depth = 0; screen != null && depth < 16; ++depth)
        {
            if (screen.getClass().getName().startsWith("fi.dy.masa.litematica.gui."))
            {
                return true;
            }
            screen = screen instanceof GuiBase gui ? gui.getParent() : null;
        }
        return false;
    }

    public static void background(GuiContext ctx, int width, int height)
    {
        RenderUtils.drawRect(ctx, 0, 0, width, height, BACKGROUND);
        RenderUtils.drawRect(ctx, 0, 0, 3, height, ACCENT);
    }

    public static void surface(GuiContext ctx, int x, int y, int width, int height, int color)
    {
        if (width < 5 || height < 5)
        {
            RenderUtils.drawRect(ctx, x, y, width, height, color);
            return;
        }
        RenderUtils.drawRect(ctx, x + 2, y, width - 4, height, color);
        RenderUtils.drawRect(ctx, x + 1, y + 1, 1, height - 2, color);
        RenderUtils.drawRect(ctx, x + width - 2, y + 1, 1, height - 2, color);
        RenderUtils.drawRect(ctx, x, y + 2, 1, height - 4, color);
        RenderUtils.drawRect(ctx, x + width - 1, y + 2, 1, height - 4, color);
    }
}
