package com.fhasix.uitheme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-side ownership/equip state for FHASIX cosmetics. */
public final class FhasixCosmetics {
    private static final Path STATE_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("fhasix_cosmetics.json");

    private static boolean loaded;
    private static boolean firstOptixOwned;
    private static boolean firstOptixEquipped;
    private static boolean secondCapeOwned;
    private static boolean secondCapeEquipped;

    private FhasixCosmetics() {}

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(STATE_FILE)) {
                String json = Files.readString(STATE_FILE, StandardCharsets.UTF_8);
                JsonObject object = JsonParser.parseString(json).getAsJsonObject();
                firstOptixOwned = object.has("first_optix_owned") && object.get("first_optix_owned").getAsBoolean();
                firstOptixEquipped = firstOptixOwned && object.has("first_optix_equipped")
                        && object.get("first_optix_equipped").getAsBoolean();
                secondCapeOwned = object.has("second_cape_owned") && object.get("second_cape_owned").getAsBoolean();
                secondCapeEquipped = secondCapeOwned && object.has("second_cape_equipped")
                        && object.get("second_cape_equipped").getAsBoolean();
            }
        } catch (Exception ignored) {
            firstOptixOwned = false;
            firstOptixEquipped = false;
            secondCapeOwned = false;
            secondCapeEquipped = false;
        }
    }

    public static boolean ownsFirstOptix() {
        ensureLoaded();
        return firstOptixOwned;
    }

    public static boolean isFirstOptixEquipped() {
        ensureLoaded();
        return firstOptixEquipped;
    }

    public static void unlockFirstOptix() {
        ensureLoaded();
        firstOptixOwned = true;
        save();
    }

    public static boolean ownsSecondCape() {
        ensureLoaded();
        return secondCapeOwned;
    }

    public static boolean isSecondCapeEquipped() {
        ensureLoaded();
        return secondCapeEquipped;
    }

    public static void unlockSecondCape() {
        ensureLoaded();
        secondCapeOwned = true;
        save();
    }

    public static void toggleSecondCape() {
        ensureLoaded();
        if (!secondCapeOwned) return;
        secondCapeEquipped = !secondCapeEquipped;
        save();
    }

    public static void toggleFirstOptix() {
        ensureLoaded();
        if (!firstOptixOwned) return;
        firstOptixEquipped = !firstOptixEquipped;
        save();
    }

    private static void save() {
        JsonObject object = new JsonObject();
        object.addProperty("first_optix_owned", firstOptixOwned);
        object.addProperty("first_optix_equipped", firstOptixEquipped);
        object.addProperty("second_cape_owned", secondCapeOwned);
        object.addProperty("second_cape_equipped", secondCapeEquipped);
        try {
            Files.createDirectories(STATE_FILE.getParent());
            Files.writeString(STATE_FILE, object.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Cosmetic state is client-side convenience data; rendering continues if persistence fails.
        }
    }
}
