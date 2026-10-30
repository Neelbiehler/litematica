package fi.dy.masa.litematica.gui.widgets;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.input.ScanCodes;

/** A flat action surface used by the main dashboard. */
public class WidgetDashboardButton extends ButtonBase
{
    private final String description;
    private final boolean accent;
    private boolean keyboardFocused;

    public WidgetDashboardButton(int x, int y, int width, int height, String title,
                                 String description, boolean accent)
    {
        super(x, y, width, height, title);
        this.description = description;
        this.accent = accent;
        this.setHoverStrings(title, description);
    }

    public boolean isEnabled()
    {
        return this.enabled;
    }

    public void setKeyboardFocused(boolean focused)
    {
        this.keyboardFocused = focused;
    }

    public void activate(boolean reverse)
    {
        if (this.enabled)
        {
            int button = reverse ? ScanCodes.OFFSET_MOUSE_RIGHT : ScanCodes.OFFSET_MOUSE_LEFT;
            this.onMouseClickedImpl(new MouseButtonEvent(this.x, this.y, new MouseButtonInfo(button, 0)), false);
        }
    }

    @Override
    public boolean onMouseScrolledImpl(double mouseX, double mouseY, double horizontal, double vertical)
    {
        // Scrolling across an action must not open a screen or change a mode.
        return false;
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected)
    {
        super.render(ctx, mouseX, mouseY, selected);
        if (this.visible == false)
        {
            this.hovered = false;
            return;
        }
        this.hovered = mouseX >= this.x && mouseX < this.x + this.width &&
                       mouseY >= this.y && mouseY < this.y + this.height;
        boolean highlighted = this.enabled && (this.hovered || this.keyboardFocused);
        int border = this.keyboardFocused ? 0xFFE2FFF8 : highlighted ? 0xFF6EDBC4 : 0xFF344250;
        int background = this.accent ? (highlighted ? 0xFF23685F : 0xFF194D48) :
                                      (highlighted ? 0xFF2B3B4B : 0xFF1C2937);
        if (this.enabled == false)
        {
            background = 0xFF18212B;
            border = 0xFF29333E;
        }

        drawSurface(ctx, this.x, this.y, this.width, this.height, border);
        drawSurface(ctx, this.x + 1, this.y + 1, this.width - 2, this.height - 2, background);
        boolean showDescription = this.height >= 36 && this.description.isEmpty() == false;
        int textY = showDescription ? this.y + 9 : this.y + (this.height - 8) / 2;
        int textColor = this.enabled ? 0xFFF1F5F9 : 0xFF8996A5;
        this.drawString(ctx, this.x + 10, textY, textColor, this.fit(this.displayString, this.width - 20));

        if (showDescription)
        {
            this.drawString(ctx, this.x + 10, textY + 15, 0xFFB1C0CF,
                            this.fit(this.description, this.width - 20));
        }
    }

    @Override
    public void setHoverStrings(List<String> strings)
    {
        super.setHoverStrings(strings);
        List<String> lines = new ArrayList<>();
        for (String line : this.hoverStrings)
        {
            lines.addAll(List.of(line.split("\n")));
        }
        this.hoverStrings.clear();
        this.hoverStrings.addAll(lines);
    }

    public void drawKeyboardHelp(GuiContext ctx)
    {
        if (this.hasHoverText())
        {
            RenderUtils.drawHoverText(ctx, this.x + this.width - 8, this.y + this.height, this.getHoverStrings());
        }
    }

    private String fit(String text, int width)
    {
        if (this.getStringWidth(text) <= width)
        {
            return text;
        }

        return this.textRenderer.plainSubstrByWidth(text, Math.max(0, width - this.getStringWidth("..."))) + "...";
    }

    public static void drawSurface(GuiContext ctx, int x, int y, int width, int height, int color)
    {
        RenderUtils.drawRect(ctx, x + 2, y, width - 4, height, color);
        RenderUtils.drawRect(ctx, x + 1, y + 1, 1, height - 2, color);
        RenderUtils.drawRect(ctx, x + width - 2, y + 1, 1, height - 2, color);
        RenderUtils.drawRect(ctx, x, y + 2, 1, height - 4, color);
        RenderUtils.drawRect(ctx, x + width - 1, y + 2, 1, height - 4, color);
    }
}
