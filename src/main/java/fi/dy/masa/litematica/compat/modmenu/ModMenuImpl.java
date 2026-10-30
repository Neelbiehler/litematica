package fi.dy.masa.litematica.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import fi.dy.masa.litematica.gui.GuiPreferences;

public class ModMenuImpl implements ModMenuApi
{
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory()
    {
        return (screen) -> {
            GuiPreferences gui = new GuiPreferences();
            gui.setParent(screen);
            return gui;
        };
    }
}
