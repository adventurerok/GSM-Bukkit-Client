package com.ithinkrok.msm.bukkit.util;

import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.util.config.Config;
import com.ithinkrok.util.config.MemoryConfig;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by paul on 18/02/16.
 */
public class MSMCommandSender implements CommandSender {

    private final Server server;
    private final Channel channel;
    private final Config sender;

    public MSMCommandSender(Server server, Channel channel, Config sender) {
        this.server = server;
        this.channel = channel;
        this.sender = sender;
    }

    @Override
    public void sendMessage(String message) {
        String type = sender.getString("type");

        Config payload = new MemoryConfig();
        payload.set("message", message);

        switch (type) {
            case "player":
                payload.set("mode", "Message");

                List<String> recipients = new ArrayList<>();
                recipients.add(sender.getString("uuid"));
                payload.set("recipients", recipients);
                break;
            case "msm_console":
                payload.set("mode", "ConsoleMessage");
                break;
            case "minecraft":
                payload.set("mode", "MinecraftConsoleMessage");
                payload.set("server", sender.getString("name"));
                break;
            case "external":
                payload.set("mode", "ExternalMessage");
                payload.set("name", sender.getString("name"));
                break;
        }

        channel.write(payload);
    }

    @Override
    public void sendMessage(String[] messages) {
        for(String message : messages) {
            sendMessage(message);
        }
    }

    @Override
    public Server getServer() {
        return server;
    }

    @Override
    public String getName() {
        return "msm_sender";
    }

    @Override
    public boolean isPermissionSet(String name) {
        return server.getConsoleSender().isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return server.getConsoleSender().isPermissionSet(perm);
    }

    @Override
    public boolean hasPermission(String name) {
        return server.getConsoleSender().hasPermission(name);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return server.getConsoleSender().hasPermission(perm);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        return server.getConsoleSender().addAttachment(plugin, name, value);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return server.getConsoleSender().addAttachment(plugin);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        return server.getConsoleSender().addAttachment(plugin, name, value, ticks);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        return server.getConsoleSender().addAttachment(plugin, ticks);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        server.getConsoleSender().removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        server.getConsoleSender().recalculatePermissions();
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return server.getConsoleSender().getEffectivePermissions();
    }

    @Override
    public boolean isOp() {
        return true;
    }

    @Override
    public void setOp(boolean value) {

    }
}
