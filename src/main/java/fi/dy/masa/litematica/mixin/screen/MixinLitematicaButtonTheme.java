package fi.dy.masa.litematica.mixin.screen;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fi.dy.masa.litematica.gui.UiTheme;
import fi.dy.masa.malilib.gui.LeftRight;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.render.GuiContext;

@Mixin(value = ButtonGeneric.class, remap = false)
public abstract class MixinLitematicaButtonTheme extends ButtonBase
{
    @Shadow @Final protected IGuiIcon icon;
    @Shadow protected LeftRight alignment;
    @Shadow protected boolean textCentered;
    @Shadow protected boolean renderDefaultBackground;

    protected MixinLitematicaButtonTheme(int x, int y, int width, int height)
    {
        super(x, y, width, height);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void litematica_drawButton(GuiContext ctx, int mouseX, int mouseY, boolean selected, CallbackInfo ci)
    {
        if (!this.renderDefaultBackground || !UiTheme.appliesTo(this.mc.gui.screen()))
        {
            return;
        }
        ci.cancel();
        if (!this.visible) return;
        this.hovered = mouseX >= this.x && mouseX < this.x + this.width &&
                       mouseY >= this.y && mouseY < this.y + this.height;
        boolean highlight = this.enabled && this.hovered;
        UiTheme.surface(ctx, this.x, this.y, this.width, this.height, highlight ? UiTheme.ACCENT : UiTheme.BORDER);
        UiTheme.surface(ctx, this.x + 1, this.y + 1, this.width - 2, this.height - 2,
                        highlight ? UiTheme.HOVER : UiTheme.SURFACE);
        int left = this.x + 5;
        int textWidth = this.width - 10;
        if (this.icon != null)
        {
            int iconX = this.alignment.getLeft(this.x, this.x + this.width, this.icon.getWidth(), 4);
            this.icon.renderAt(ctx, iconX, this.y + (this.height - this.icon.getHeight()) / 2,
                               this.zLevel, this.enabled, highlight);
            textWidth -= this.icon.getWidth() + 3;
            if (this.alignment == LeftRight.LEFT) left += this.icon.getWidth() + 3;
        }
        String text = this.displayString;
        if (this.getStringWidth(text) > textWidth)
        {
            text = this.textRenderer.plainSubstrByWidth(text, Math.max(0, textWidth - this.getStringWidth("..."))) + "...";
        }
        int textX = this.textCentered ? left + (textWidth - this.getStringWidth(text)) / 2 : left;
        this.drawString(ctx, textX, this.y + (this.height - 8) / 2, this.enabled ? UiTheme.TEXT : 0xFF8996A5, text);
    }
}
