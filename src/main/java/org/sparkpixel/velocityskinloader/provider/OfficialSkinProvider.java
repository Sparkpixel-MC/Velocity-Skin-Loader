package org.sparkpixel.velocityskinloader.provider;

import org.sparkpixel.velocityskinloader.client.MojangClient;
import org.sparkpixel.velocityskinloader.model.mojang.MojangSkin;
import org.sparkpixel.velocityskinloader.model.mojang.UUIDProfile;
import com.velocitypowered.api.util.GameProfile;

import java.util.List;

public class OfficialSkinProvider implements SkinProvider {
    private static OfficialSkinProvider instance = new OfficialSkinProvider();

    public static OfficialSkinProvider instance() {
        return instance;
    }

    public OfficialSkinProvider() {

    }

    @Override
    public GameProfile.Property getProperty(String name) {
        try {
            UUIDProfile uuidByName = MojangClient.instance().getUUIDByName(name);
            MojangSkin skinByUUIDProfile = MojangClient.instance().getSkinByUUIDProfile(uuidByName);
            List<GameProfile.Property> propertyList = skinByUUIDProfile.getProperties();
            return !propertyList.isEmpty() ? propertyList.get(0) : null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getDisplay() {
        return "Official";
    }
}
