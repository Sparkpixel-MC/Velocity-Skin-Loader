package com.molean.velocityskinloader.model.mojang;

import com.velocitypowered.api.util.GameProfile;
import lombok.Data;

import java.util.List;

@Data
public class MojangSkin {
    private List<GameProfile.Property> name;
    private List<GameProfile.Property> properties;
    private List<GameProfile.Property> signature;
}
