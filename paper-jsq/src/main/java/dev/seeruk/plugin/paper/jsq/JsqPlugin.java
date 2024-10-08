package dev.seeruk.plugin.paper.jsq;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public final class JsqPlugin extends JavaPlugin {

    private RedisClient redisClient;
    private StatefulRedisPubSubConnection<String, String> redisConn;

    @Override
    public void onEnable() {
        var config = this.getOrCreateConfig().orElseThrow();
        var pluginManager = getServer().getPluginManager();
        var serializer = MiniMessage.miniMessage();
        var scheduler = getServer().getScheduler();

        this.redisClient = RedisClient.create(config.getString("redisUri"));
        this.redisConn = this.redisClient.connectPubSub();

        this.redisConn.addListener(new JsqListener(this, serializer, scheduler));
        this.redisConn.sync().subscribe(config.getString("redisChannel"));

        pluginManager.registerEvents(new PlayerEventListener(), this);

        getLogger().info("Initialised successfully");
    }

    @Override
    public void onDisable() {
        this.redisClient.close();
        this.redisConn.close();
        this.redisClient = null;
        this.redisConn = null;
    }

    private Optional<YamlDocument> getOrCreateConfig() {
        // Ideally this would not throw, and would instead return an Optional itself.
        try {
            var config = YamlDocument.create(
                new File(this.getDataFolder(), "config.yml"),
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

            config.update();
            config.save();

            return Optional.of(config);
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
