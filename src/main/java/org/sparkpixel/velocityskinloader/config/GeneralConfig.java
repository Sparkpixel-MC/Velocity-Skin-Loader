package org.sparkpixel.velocityskinloader.config;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class GeneralConfig {
    private boolean initialBlockingLoading = true;
    private boolean printStackTracesIfSkinLoadFailed = false;
    private long playerSkinCacheTimeInSeconds = 600;
    private List<Integer> officialServiceIds = new ArrayList<>(List.of(0));
}