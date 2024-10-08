package dev.seeruk.plugin.paper.jsq;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

/**
 * JsqListener is a Redis pub/sub adapter used to listen for join/switch/quit events on a Redis
 * pub/sub channel. Messages are formatted using the given component serializer.
 */
public class JsqListener extends RedisPubSubAdapter<String, String> {
    private final JavaPlugin plugin;
    private final ComponentSerializer<Component, Component, String> serializer;
    private final BukkitScheduler scheduler;

    public JsqListener(
        JavaPlugin plugin,
        ComponentSerializer<Component, Component, String> serializer,
        BukkitScheduler scheduler
    ) {
        this.plugin = plugin;
        this.serializer = serializer;
        this.scheduler = scheduler;
    }

    /**
     * Handle an incoming message. We expect this to be a join/switch/quit message, ready to be
     * deserialized into a component that can be sent to the whole server.
     */
    @Override
    public void message(String channel, String message) {
        this.scheduler.runTask(this.plugin, () -> {
            this.plugin.getServer().sendMessage(
                serializer.deserialize(message)
            );
        });
    }
}
