package de.sunpacks;

import de.sunpacks.gui.PackBrowserScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_3675;
import org.lwjgl.glfw.GLFW;

/** Standalone Modrinth resource-pack downloader for Minecraft 1.21.11. */
public final class SunPackBrowser implements ClientModInitializer {
    private static class_304 openBrowserKey;

    @Override
    public void onInitializeClient() {
        openBrowserKey = KeyBindingHelper.registerKeyBinding(new class_304(
            "key.sunpacks.open",
            class_3675.class_307.field_1668,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            class_304.class_11900.field_62556
        ));

        ClientTickEvents.END_CLIENT_TICK.register(SunPackBrowser::onTick);
    }

    private static void onTick(class_310 mc) {
        while (openBrowserKey.method_1436()) {
            if (mc.field_1755 == null) {
                mc.method_1507(new PackBrowserScreen(null));
            }
        }
    }
}
