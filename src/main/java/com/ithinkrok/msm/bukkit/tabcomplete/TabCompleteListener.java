package com.ithinkrok.msm.bukkit.tabcomplete;

import com.comphenix.packetwrapper.WrapperPlayClientTabComplete;
import com.comphenix.packetwrapper.WrapperPlayServerTabComplete;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.ithinkrok.msm.common.command.CommandInfo;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by paul on 09/03/16.
 */
public class TabCompleteListener extends PacketAdapter {

    private final Map<String, CommandInfo> commandMap;
    private final Map<String, Set<String>> tabCompletionSets;

    private final Map<UUID, String> lastTabComplete = new ConcurrentHashMap<>();

    private final String[] EMPTY_STRING_ARRAY = new String[0];

    public TabCompleteListener(Plugin plugin, Map<String, CommandInfo> commandMap, Map<String, Set<String>> tabCompletionSets) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.TAB_COMPLETE,
                PacketType.Play.Server.TAB_COMPLETE);
        this.commandMap = commandMap;
        this.tabCompletionSets = tabCompletionSets;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.TAB_COMPLETE) return;

        WrapperPlayClientTabComplete packet = new WrapperPlayClientTabComplete(event.getPacket());

        lastTabComplete.put(event.getPlayer().getUniqueId(), packet.getText());
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.TAB_COMPLETE) return;

        WrapperPlayServerTabComplete packet = new WrapperPlayServerTabComplete(event.getPacket());

        Player player = event.getPlayer();
        String message = lastTabComplete.get(player.getUniqueId());

        Set<String> text = new TreeSet<>(Arrays.asList(packet.getText()));

        if(!message.contains(" ") && message.startsWith("/")) {
            String commandStub = message.substring(1).toLowerCase();

            addCommandNameCompletions(player, text, commandStub);
        }

        packet.setText(text.toArray(EMPTY_STRING_ARRAY));
    }

    public void addCommandNameCompletions(Player player, Set<String> text, String commandStub) {
        for(CommandInfo commandInfo : commandMap.values()) {
            if(commandInfo.getPermission() != null && !player.hasPermission(commandInfo.getPermission())){
                continue;
            }

            if(commandInfo.getName().startsWith(commandStub)) {
                text.add("/" + commandInfo.getName());
            }
        }
    }
}
