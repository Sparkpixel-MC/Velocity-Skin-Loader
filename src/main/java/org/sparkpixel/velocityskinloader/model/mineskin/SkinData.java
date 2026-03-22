package org.sparkpixel.velocityskinloader.model.mineskin;

import com.velocitypowered.api.util.GameProfile;
import lombok.Data;

import java.util.List;

@Data
public class SkinData {
    private String uuid;
    private TextureInfo texture;
    private List<GameProfile.Property> signature;
}
