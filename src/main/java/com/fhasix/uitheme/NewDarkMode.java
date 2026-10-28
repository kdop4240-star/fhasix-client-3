package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Dark Mode: the "Default Dark Mode" resource pack by nebulr is bundled INSIDE this mod
 * (src/main/resources/resourcepacks/dark_mode, see its LICENSE.txt / CREDITS.txt) and registered as a
 * built-in pack. Nobody has to install or pick it: switching the Dark Mode mod ON enables the pack
 * and reloads the resources, switching it OFF disables it again. The setting is also re-applied on the
 * first tick after the game starts.
 *
 * (A built-in pack still shows up in the Resource Packs screen as "Fhasix Dark Mode"; the mod keeps it
 * in sync with the Dark Mode switch.)
 *
 * NOTE FOR WHOEVER COMPILES THIS: registerBuiltinResourcePack / ResourcePackActivationType may be
 * deprecated or moved in the Fabric API build for 1.21.11 (newer name: ResourceLoader.registerBuiltinPack
 * with a PackActivationType) - adapt. The pack's profile id is looked up by its display name, so it
 * doesn't matter how Fabric names it internally.
 */
public final class NewDarkMode {

    static final String ID = "dark_mode";
    private static final String MOD_ID = "fhasixuitheme";
    private static final String PACK_NAME = "Fhasix Dark Mode";

    private static boolean synced = false;

    /** True for the bundled pack's display name - the Resource Packs screen hides it (NewPackListMixin). */
    public static boolean isHiddenPack(String displayName) {
        return PACK_NAME.equals(displayName);
    }

    private NewDarkMode() {
    }

    static void init() {
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(mod ->
                ResourceManagerHelper.registerBuiltinResourcePack(
                        Identifier.of(MOD_ID, "dark_mode"), mod, Text.literal(PACK_NAME),
                        ResourcePackActivationType.NORMAL));
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("dark_mode", c -> {
            if (!synced) {
                synced = true;
                apply(c, NewModSettings.isEnabled(c, ID));
            }
        }));
    }

    public static boolean active() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c != null && NewModSettings.isEnabled(c, ID);
    }

    /** Called by NewModSettings.toggle after Dark Mode was switched. */
    public static void onToggled(MinecraftClient c, boolean on) {
        apply(c, on);
    }

    private static void apply(MinecraftClient c, boolean on) {
        try {
            ResourcePackManager manager = c.getResourcePackManager();
            manager.scanPacks();
            String id = findPackId(manager);
            if (id == null) {
                return;
            }
            boolean enabled = manager.getEnabledIds().contains(id);
            if (on == enabled) {
                return;
            }
            if (on) {
                manager.enable(id);
            } else {
                manager.disable(id);
            }
            c.options.refreshResourcePacks(manager); // saves the pack list and reloads resources
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static String findPackId(ResourcePackManager manager) {
        for (ResourcePackProfile p : manager.getProfiles()) {
            if (PACK_NAME.equals(p.getDisplayName().getString())
                    || (p.getId().contains(MOD_ID) && p.getId().contains("dark_mode"))) {
                return p.getId();
            }
        }
        return null;
    }
}
