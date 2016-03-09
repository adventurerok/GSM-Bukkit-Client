package com.ithinkrok.msm.bukkit.tabcomplete;

import com.comphenix.packetwrapper.WrapperPlayClientTabComplete;
import com.comphenix.packetwrapper.WrapperPlayServerTabComplete;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.ithinkrok.msm.common.command.CommandInfo;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Created by paul on 09/03/16.
 */
public class TabCompleteListener extends PacketAdapter {

    private final Map<String, CommandInfo> commandMap;
    private final Map<String, Set<String>> tabCompletionSets;

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

        System.out.println(packet.getText());
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.TAB_COMPLETE) return;

        WrapperPlayServerTabComplete packet = new WrapperPlayServerTabComplete(event.getPacket());

        System.out.println(Arrays.toString(packet.getText()));
    }
}
