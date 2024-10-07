package dev.seeruk.plugin.velocity.jsq;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Random;

@Plugin(
    id = "seers-jsq",
    name = "Seer's Velocity Join, Switch, Quit",
    description = "Adds network-wide join/switch/quit messages to your server so players know what's going on",
    version = BuildConstants.VERSION
)
public class JsqPlugin {

    private YamlDocument config;

    @DataDirectory
    @Inject
    private Path dataDirectory;

    @Inject
    private Logger logger;

    @Inject
    private ProxyServer server;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) throws IOException {
        // Initialise configuration
        this.config = YamlDocument.create(
            new File(dataDirectory.toFile(), "config.yml"),
            Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")),
            GeneralSettings.DEFAULT,
            LoaderSettings.builder()
                .setAutoUpdate(true)
                .build(),
            DumperSettings.DEFAULT,
            UpdaterSettings.builder()
                .setVersioning(new BasicVersioning("configVersion"))
                .build()
        );

        this.config.update();
        this.config.save();

        logger.info("initialised");
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        var player = event.getPlayer();
        var nextServer = event.getServer();
        var previousServer = event.getPreviousServer();

        var placeholders = new Placeholders(
            player.getUsername(),
            nextServer.getServerInfo().getName(),
            previousServer
                .map(server -> server.getServerInfo().getName())
                .orElse("")
        );

        var messageType = previousServer.map(server -> "switch").orElse("join");

        var prefix = this.config.getString(messageType + "MessagePrefix");
        var suffix = this.config.getString(messageType + "MessageSuffix");
        var messages = this.config.getStringList(messageType + "Messages");

        var format = prefix + getRandomItem(messages) + suffix;
        var message = miniMessage.deserialize(replacePlaceholders(format, placeholders));

        this.server.sendMessage(message);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        var player = event.getPlayer();

        var placeholders = new Placeholders(
            player.getUsername(),
            "", // No next server on disconnect
            player.getCurrentServer()
                .map(server -> server.getServerInfo().getName())
                .orElse("unknown")
        );

        var prefix = this.config.getString("quitMessagePrefix");
        var suffix = this.config.getString("quitMessageSuffix");
        var messages = this.config.getStringList("quitMessages");

        var format = prefix + getRandomItem(messages) + suffix;
        var message = miniMessage.deserialize(replacePlaceholders(format, placeholders));

        this.server.sendMessage(message);
    }

    private String replacePlaceholders(String input, Placeholders placeholders) {
        return input.replace("{player}", placeholders.player())
            .replace("{next_server}", placeholders.nextServer())
            .replace("{previous_server}", placeholders.previousServer());
    }

    private <T> T getRandomItem(List<T> list) {
        Random random = new Random();
        int listSize = list.size();
        int randomIndex = random.nextInt(listSize);
        return list.get(randomIndex);
    }

    private record Placeholders(
        String player,
        String nextServer,
        String previousServer
    ) {}
}
