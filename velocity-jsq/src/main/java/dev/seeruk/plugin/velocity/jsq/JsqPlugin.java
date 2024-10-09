package dev.seeruk.plugin.velocity.jsq;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import dev.seeruk.plugin.common.jsq.JsqEvent;
import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Random;

@Plugin(
    id = "velocity-jsq",
    name = "Seer's Velocity Join, Switch, Quit",
    description = "Adds network-wide join/switch/quit messages to your server so players know what's going on",
    version = BuildConstants.VERSION
)
public class JsqPlugin {

    private final Path dataDirectory;
    private final Logger logger;

    private YamlDocument config;
    private RedisPubSubAsyncCommands<String, byte[]> redisConn;

    @Inject
    public JsqPlugin(
        @DataDirectory Path dataDirectory,
        Logger logger
    ) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

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

        this.redisConn = RedisClient.create(this.config.getString("redisUri"))
            .connectPubSub(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE))
            .async();

        logger.info("Initialised successfully");
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

        var channel = this.config.getString("redisChannel");
        var prefix = this.config.getString(messageType + "MessagePrefix");
        var suffix = this.config.getString(messageType + "MessageSuffix");
        var messages = this.config.getStringList(messageType + "Messages");

        var format = prefix + getRandomItem(messages) + suffix;

        var protoEvent = JsqEvent.newBuilder()
            .setMessage(replacePlaceholders(format, placeholders))
            .setPlayerUuid(player.getUniqueId().toString())
            .setPlayerName(player.getUsername())
            .build();

        this.redisConn.publish(channel, protoEvent.toByteArray());
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

        var channel = this.config.getString("redisChannel");
        var prefix = this.config.getString("quitMessagePrefix");
        var suffix = this.config.getString("quitMessageSuffix");
        var messages = this.config.getStringList("quitMessages");

        var format = prefix + getRandomItem(messages) + suffix;

        var protoEvent = JsqEvent.newBuilder()
            .setMessage(replacePlaceholders(format, placeholders))
            .setPlayerUuid(player.getUniqueId().toString())
            .setPlayerName(player.getUsername())
            .build();

        this.redisConn.publish(channel, protoEvent.toByteArray());
    }

    private String replacePlaceholders(String input, Placeholders placeholders) {
        return input.replace("{player}", placeholders.player())
            .replace("{next_server}", placeholders.nextServer())
            .replace("{previous_server}", placeholders.previousServer());
    }

    private <T> T getRandomItem(List<T> list) {
        return list.get(new Random().nextInt(list.size()));
    }

    private record Placeholders(
        String player,
        String nextServer,
        String previousServer
    ) {}
}
