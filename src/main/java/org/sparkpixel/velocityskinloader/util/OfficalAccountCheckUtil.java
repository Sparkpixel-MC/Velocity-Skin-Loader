package org.sparkpixel.velocityskinloader.util;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class OfficalAccountCheckUtil {
    private static boolean multiLoginEnabled = false;
    private static boolean hasChecked = false;
    private static Logger logger;
    private static ProxyServer proxyServer;
    private static Object multiLoginPluginInstance;

    public static void init(Logger logger2, ProxyServer server) {
        logger = logger2;
        proxyServer = server;
    }

    private static void lazyCheck() {
        if (hasChecked) {
            return;
        }
        hasChecked = true;
        if (proxyServer != null) {
            for (Object plugin : proxyServer.getPluginManager().getPlugins()) {
                try {
                    Method getInstanceMethod = plugin.getClass().getMethod("getInstance");
                    Optional<?> instanceOpt = (Optional) getInstanceMethod.invoke(plugin, new Object[0]);
                    if (instanceOpt.isPresent()) {
                        Object instance = instanceOpt.get();
                        if (instance.getClass().getName().toLowerCase().contains("multilogin")) {
                            multiLoginPluginInstance = instance;
                            multiLoginEnabled = true;
                            logger.info("Multilogin detected and skin function start hooking");
                            return;
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error while checking for Multilogin plugin: {}", e.getMessage());
                }
            }
        }
        multiLoginEnabled = false;
    }

    public static boolean isMultiLoginEnabled() {
        lazyCheck();
        return multiLoginEnabled;
    }

    public static boolean isOfficialPlayer(Player player, List<Integer> officialServiceIds) {
        Integer serviceId;
        lazyCheck();
        if (!multiLoginEnabled || officialServiceIds == null || officialServiceIds.isEmpty()) {
            return false;
        }
        try {
            Object api = getMultiLoginApi();
            if (api == null || (serviceId = getPlayerServiceId(api, player)) == null) {
                return false;
            }
            boolean isOfficial = officialServiceIds.contains(serviceId);
            if (isOfficial) {
                logger.info("Player {} comes from offical, giving {} offical skin", player.getUsername(), serviceId);
            }
            return isOfficial;
        } catch (Exception e) {
            logger.warn("Error while getting Multilogin Data , errors {}", e.getMessage());
            return false;
        }
    }

    private static Object getMultiLoginApi() {
        if (multiLoginPluginInstance != null) {
            return multiLoginPluginInstance;
        }
        if (proxyServer != null) {
            for (Object plugin : proxyServer.getPluginManager().getPlugins()) {
                try {
                    Method getInstanceMethod = plugin.getClass().getMethod("getInstance");
                    Optional<?> instanceOpt = (Optional) getInstanceMethod.invoke(plugin, new Object[0]);
                    if (instanceOpt.isPresent()) {
                        Object instance = instanceOpt.get();
                        if (instance.getClass().getName().toLowerCase().contains("multilogin")) {
                            return instance;
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error while getting Multilogin API: {}", e.getMessage());
                }
            }
            return null;
        }
        return null;
    }

    private static Integer getPlayerServiceId(Object api, Player player) {
        Object result = null;
        try {
            Method getCoreApiMethod = api.getClass().getMethod("getMultiCoreAPI");
            Object coreApi = getCoreApiMethod.invoke(api);
            if (coreApi == null) {
                return null;
            }
            Method getPlayerDataMethod = coreApi.getClass().getMethod("getPlayerData", UUID.class);
            Object playerData = getPlayerDataMethod.invoke(coreApi, player.getUniqueId());
            if (playerData == null) {
                return null;
            }
            Method getLoginServiceMethod = playerData.getClass().getMethod("getLoginService");
            Object loginService = getLoginServiceMethod.invoke(playerData);
            if (loginService == null) {
                return null;
            }
            String[] methodNames = {"getId", "getID", "getServiceId", "getSid"};
            for (String methodName : methodNames) {
                try {
                    Method method = loginService.getClass().getMethod(methodName);
                    result = method.invoke(loginService);
                } catch (NoSuchMethodException e) {
                    logger.error("Error while getting player service ID: {}", e.getMessage());
                }
                if (result instanceof Integer) {
                    return (Integer) result;
                }
                if (result != null) {
                    return Integer.valueOf(result.toString());
                }
            }
            return null;
        } catch (Exception e2) {
            return null;
        }
    }
}