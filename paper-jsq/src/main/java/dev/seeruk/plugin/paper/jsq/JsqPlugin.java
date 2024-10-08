package dev.seeruk.plugin.paper.jsq;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public final class JsqPlugin extends JavaPlugin {

    public static final String CHANNEL = "seers-jsq:main";

    private YamlDocument config;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private RedisClient redisClient;

    @Override
    public void onEnable() {
        // Initialise configuration
        try {
            this.config = YamlDocument.create(
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

            this.config.update();
            this.config.save();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.redisClient = RedisClient.create(this.config.getString("redisUri"));
        this.redisClient.connect();

        getLogger().info("initialised");

        // TODO: Config
        this.redisClient = RedisClient.create(this.config.getString("redisUri"));

        var conn = redisClient.connectPubSub();

        conn.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                getServer().sendMessage(miniMessage.deserialize(message));
            }
        });

        conn.sync().subscribe(CHANNEL);
    }

    @Override
    public void onDisable() {
        this.redisClient.shutdown();
        this.redisClient = null;
    }
}
