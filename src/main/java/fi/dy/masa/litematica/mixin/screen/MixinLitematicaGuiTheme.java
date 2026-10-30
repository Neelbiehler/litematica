package fi.dy.masa.litematica.mixin.screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fi.dy.masa.litematica.gui.UiTheme;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;

@Mixin(value = GuiBase.class, remap = false)
public abstract class MixinLitematicaGuiTheme
{
    @Inject(method = "drawScreenBackground", at = @At("HEAD"), cancellable = true)
    private void litematica_drawBackground(GuiContext ctx, int mouseX, int mouseY, CallbackInfo ci)
    {
        GuiBase gui = (GuiBase) (Object) this;
        if (UiTheme.appliesTo(gui))
        {
            UiTheme.background(ctx, gui.getScreenWidth(), gui.getScreenHeight());
            ci.cancel();
        }
    }
}
