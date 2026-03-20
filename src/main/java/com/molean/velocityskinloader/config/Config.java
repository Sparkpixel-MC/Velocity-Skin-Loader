package com.molean.velocityskinloader.config;

import com.google.gson.Gson;
import lombok.Data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.SortedSet;
import java.util.TreeSet;

@Data
public class Config {
    private GeneralConfig generalConfig = new GeneralConfig();
    private SortedSet<SkinProviderConfig> skinProviderList = initialList();


    public static SortedSet<SkinProviderConfig> initialList() {
        TreeSet<SkinProviderConfig> skinProviderConfigs = new TreeSet<>();
        SkinProviderConfig blessing = new SkinProviderConfig();

        SkinProviderConfig official = new SkinProviderConfig();
        official.setType("Official");
        official.setPriority(100);

        blessing.setType("BlessingSkin");
        blessing.setUrl("https://mcskin.bu7.top");
        blessing.setPriority(99);
        official.setUrl(null);

        skinProviderConfigs.add(official);
        skinProviderConfigs.add(blessing);
        return skinProviderConfigs;
    }

    public void save(Gson gson, File file) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            String s = gson.toJson(this);
            fileOutputStream.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static Config load(Gson gson, File file) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            byte[] bytes = fileInputStream.readAllBytes();
            Config config = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), Config.class);
            if (config == null) {
                config = new Config();
            }
            return config;
        }
    }
}
