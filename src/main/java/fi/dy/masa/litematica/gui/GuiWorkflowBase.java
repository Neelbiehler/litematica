package fi.dy.masa.litematica.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import fi.dy.masa.litematica.gui.widgets.WidgetDashboardButton;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.input.ScanCodes;

public abstract class GuiWorkflowBase extends GuiBase
{
    protected final List<WidgetDashboardButton> dashboardButtons = new ArrayList<>();
    protected int focusedIndex = -1;

    @Override
    public void initGui()
    {
        super.initGui();
        this.dashboardButtons.clear();
    }

    protected WidgetDashboardButton addDashboardButton(int x, int y, int width, int height, String title,
            String description, boolean accent, IButtonActionListener listener)
    {
        WidgetDashboardButton button = new WidgetDashboardButton(x, y, width, height, title, description, accent);
        this.dashboardButtons.add(button);
        button.setKeyboardFocused(this.dashboardButtons.size() - 1 == this.focusedIndex);
        this.addButton(button, listener);
        return button;
    }

    @Override
    protected void drawButtonHoverTexts(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawButtonHoverTexts(ctx, mouseX, mouseY, partialTicks);
        if (this.shouldRenderHoverStuff() && this.focusedIndex >= 0 && this.focusedIndex < this.dashboardButtons.size() &&
            this.dashboardButtons.stream().noneMatch(WidgetDashboardButton::isMouseOver))
        {
            this.dashboardButtons.get(this.focusedIndex).drawKeyboardHelp(ctx);
        }
    }

    @Override
    public boolean onKeyTyped(KeyEvent input)
    {
        if (input.key() == ScanCodes.SCAN_TAB && !this.dashboardButtons.isEmpty())
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
        if (this.focusedIndex >= 0 && this.focusedIndex < this.dashboardButtons.size() && (input.key() == ScanCodes.SCAN_RETURN ||
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

    protected void updateKeyboardFocus()
    {
        for (int i = 0; i < this.dashboardButtons.size(); ++i)
        {
            this.dashboardButtons.get(i).setKeyboardFocused(i == this.focusedIndex);
        }
    }

}
