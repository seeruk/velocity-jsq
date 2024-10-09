package dev.seeruk.plugin.paper.jsq;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.seeruk.plugin.common.jsq.JsqEvent;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import org.bukkit.Statistic;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * JsqListener is a Redis pub/sub adapter used to listen for join/switch/quit events on a Redis
 * pub/sub channel. Messages are formatted using the given component serializer.
 */
public class JsqListener extends RedisPubSubAdapter<String, byte[]> {
    private final Logger logger;
    private final JavaPlugin plugin;
    private final ComponentSerializer<Component, Component, String> serializer;
    private final BukkitScheduler scheduler;

    public JsqListener(
        JavaPlugin plugin,
        Logger logger,
        ComponentSerializer<Component, Component, String> serializer,
        BukkitScheduler scheduler
    ) {
        this.logger = logger;
        this.plugin = plugin;
        this.serializer = serializer;
        this.scheduler = scheduler;
    }

    /**
     * Handle an incoming message. We expect this to be a join/switch/quit message, ready to be
     * deserialized into a component that can be sent to the whole server.
     */
    @Override
    public void message(String channel, byte[] message) {
        this.scheduler.runTask(this.plugin, () -> {
            try {
                var event = JsqEvent.parseFrom(message);
                var server = this.plugin.getServer();

                var player = server.getOfflinePlayer(UUID.fromString(event.getPlayerUuid()));
                var output = PlaceholderAPI.setPlaceholders(player, event.getMessage());

                this.logger.info(player.getName());
                this.logger.info(String.format("%d", player.getStatistic(Statistic.DEATHS)));

                server.sendMessage(serializer.deserialize(output.replace("{player}", event.getPlayerName())));
            } catch (InvalidProtocolBufferException e) {
                this.logger.warning("failed to parse JSQ event proto message: " + e.getMessage());
            }
        });
    }
}
