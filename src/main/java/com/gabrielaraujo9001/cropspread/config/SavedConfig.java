package com.gabrielaraujo9001.cropspread.config;

import com.gabrielaraujo9001.cropspread.CropSpread;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class SavedConfig extends SavedData {
    private boolean isModEnabled = false;

    public SavedConfig() {}

    public SavedConfig(boolean enabled) {
        this.isModEnabled = enabled;
    }

    public boolean getIsModEnabled() {
        return this.isModEnabled;
    }

    public void setModEnabled(boolean enabled) {
        this.isModEnabled = enabled;
        setDirty(true);
    }

    private static final Codec<SavedConfig> CODEC = Codec.BOOL.fieldOf("isModEnabled").codec().xmap(
            SavedConfig::new,
            SavedConfig::getIsModEnabled
    );

    private static final SavedDataType<SavedConfig> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CropSpread.MOD_ID, "crops_spread_config_data"),
            SavedConfig::new,
            CODEC,
            null
    );

    public static SavedConfig getSavedConfig(MinecraftServer server) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        assert level != null;

        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
