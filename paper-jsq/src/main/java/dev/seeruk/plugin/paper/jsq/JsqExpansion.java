package dev.seeruk.plugin.paper.jsq;


import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JsqExpansion extends PlaceholderExpansion {
    private final String placeholder;
    private final String fallback;

    public JsqExpansion(String placeholder, String fallback) {
        this.placeholder = placeholder;
        this.fallback = fallback;
    }

    @NotNull
    @Override
    public String getIdentifier() {
        return "jsq";
    }

    @NotNull
    @Override
    public String getAuthor() {
        return "seeruk";
    }

    @NotNull
    @Override
    public String getVersion() {
        return "1.1-SNAPSHOT";
    }

    @Nullable
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (!params.equalsIgnoreCase("player")) {
            // This is the only one we support for now, so let's bail early.
            return null;
        }

        if (player == null || player.getName() == null) {
            // Return internal placeholder to be replaced as fallback
            return fallback;
        }

        // TODO: Configurable placeholder to use
        return PlaceholderAPI.setPlaceholders(player, placeholder);
    }
}
