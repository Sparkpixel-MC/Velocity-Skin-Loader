package org.sparkpixel.velocityskinloader.config;

import org.sparkpixel.velocityskinloader.exception.NoSuchSkinProviderException;
import org.sparkpixel.velocityskinloader.provider.BlessingSkinProvider;
import org.sparkpixel.velocityskinloader.provider.OfficialSkinProvider;
import org.sparkpixel.velocityskinloader.provider.SkinProvider;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class SkinProviderConfig implements Comparable<SkinProviderConfig> {
    private String type;
    private String url;
    private int priority = 0;

    @Override
    public int compareTo(@NotNull SkinProviderConfig skinProviderConfig) {
        return skinProviderConfig.priority - this.priority;
    }

    public SkinProvider toSkinProvider() {
        switch (type) {
            case "Official" -> {
                return OfficialSkinProvider.instance();
            }
            case "BlessingSkin" -> {
                return BlessingSkinProvider.of(this);
            }
            default -> throw new NoSuchSkinProviderException(type);
        }
    }
}
