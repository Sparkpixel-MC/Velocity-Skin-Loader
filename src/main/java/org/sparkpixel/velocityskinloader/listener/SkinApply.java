package org.sparkpixel.velocityskinloader.listener;

import org.sparkpixel.velocityskinloader.VelocitySkinLoader;
import org.sparkpixel.velocityskinloader.cache.PlayerSkinCache;
import org.sparkpixel.velocityskinloader.config.Config;
import org.sparkpixel.velocityskinloader.config.GeneralConfig;
import org.sparkpixel.velocityskinloader.config.SkinProviderConfig;
import org.sparkpixel.velocityskinloader.exception.NoSuchSkinProviderException;
import org.sparkpixel.velocityskinloader.provider.OfficialSkinProvider;
import org.sparkpixel.velocityskinloader.provider.SkinProvider;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import org.sparkpixel.velocityskinloader.util.OfficalAccountCheckUtil;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

public class SkinApply {
    private final Config config;

    private final Logger logger = Logger.getLogger(VelocitySkinLoader.class.getSimpleName());
    private final ProxyServer proxyServer;
    private final Object plugin;

    public SkinApply(Config config, ProxyServer proxyServer, Object plugin) {
        this.config = config;
        this.proxyServer = proxyServer;
        this.plugin = plugin;
    }

    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        String username = event.getPlayer().getUsername();
        Player player = event.getPlayer();
        if (loadSkinFromCache(player, true)) {
            if (config.getGeneralConfig().isInitialBlockingLoading()) {
                loadSkinOnline(player);
            } else {
                if (loadSkinFromCache(player, false)) {
                    logger.info("Initial blocking loading is disabled.");
                    logger.info(username + " would has skin next login.");
                }
                proxyServer.getScheduler().buildTask(plugin, () -> loadSkinOnline(player)).schedule();
            }
        }


    }

    public boolean loadSkinFromCache(Player player, boolean checkExpire) {
        String username = player.getUsername();
        if (PlayerSkinCache.playerSkinCache().contains(username)) {
            PlayerSkinCache.TimeLimitedSkin timeLimitedSkin = PlayerSkinCache.playerSkinCache().get(username);
            if (!checkExpire || timeLimitedSkin.getExpireTime().toLocalDateTime().isAfter(LocalDateTime.now())) {
                player.setGameProfileProperties(List.of(timeLimitedSkin.getProperty()));
                logger.info("Load cached skin for " + username);
                return false;
            }
        }
        return true;
    }

    public void loadSkinOnline(Player player) {
        String username = player.getUsername();
        List<Integer> officialServiceIds = config.getGeneralConfig().getOfficialServiceIds();
        if (OfficalAccountCheckUtil.isOfficialPlayer(player , officialServiceIds)) {
            try {
                SkinProvider officialProvider = getOfficialSkinProvider();
                if (officialProvider != null) {
                    GameProfile.Property property = officialProvider.getProperty(username);
                    if (property != null) {
                        applySkin(player, property, "Mojang Official");
                        return;
                    }
                }
            } catch (Exception e) {
                if (config.getGeneralConfig().isPrintStackTracesIfSkinLoadFailed()) {
                    e.printStackTrace();
                }
            }
            logger.info("No official skin found for " + username);
            return;
        }

        for (SkinProviderConfig skinProviderConfig : config.getSkinProviderList()) {
            try {
                SkinProvider skinProvider = skinProviderConfig.toSkinProvider();
                GameProfile.Property property = skinProvider.getProperty(username);
                if (property != null) {
                    applySkin(player, property, skinProvider.getDisplay());
                    return;
                }
            } catch (NoSuchSkinProviderException e) {
                e.printStackTrace();
            } catch (Exception e) {
                if (config.getGeneralConfig().isPrintStackTracesIfSkinLoadFailed()) {
                    e.printStackTrace();
                }
            }
        }
        logger.info("No available skin for " + username);
    }

    private SkinProvider getOfficialSkinProvider() {
        for (SkinProviderConfig cfg : config.getSkinProviderList()) {
            try {
                SkinProvider provider = cfg.toSkinProvider();
                if (provider instanceof OfficialSkinProvider) {
                    return provider;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            return new OfficialSkinProvider();
        } catch (Exception e) {
            return null;
        }
    }

    private void applySkin(Player player, GameProfile.Property property, String providerName) {
        player.setGameProfileProperties(List.of(property));
        logger.info(player.getUsername() + " loaded skin from " + providerName);

        PlayerSkinCache.TimeLimitedSkin timeLimitedSkin = new PlayerSkinCache.TimeLimitedSkin();
        LocalDateTime localDateTime = LocalDateTime.now()
                .plusSeconds(config.getGeneralConfig().getPlayerSkinCacheTimeInSeconds());
        timeLimitedSkin.setExpireTime(Timestamp.valueOf(localDateTime));
        timeLimitedSkin.setProperty(property);
        PlayerSkinCache.playerSkinCache().set(player.getUsername(), timeLimitedSkin);
    }
}
