package org.sparkpixel.velocityskinloader.cache;

import com.google.common.hash.Hashing;
import com.velocitypowered.api.util.GameProfile;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public class KnownSkinCache implements Cache<String, GameProfile.Property> {

    private static final KnownSkinCache instance = new KnownSkinCache();
    private Map<String, GameProfile.Property> payload = new HashMap<>();


    public static KnownSkinCache knownSkinCache() {
        return instance;
    }

    @Override
    public @NotNull Map<String, GameProfile.Property> payload() {
        return payload;
    }

    @Override
    public void payload(Map<String, GameProfile.Property> payload) {
        this.payload = payload;
    }

    public String getHash(byte[] bytes) {
        return Hashing.sha256().hashBytes(bytes).toString();
    }

}
