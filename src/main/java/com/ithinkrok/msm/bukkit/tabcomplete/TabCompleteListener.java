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
import java.util.regex.Pattern;

/**
 * Created by paul on 09/03/16.
 */
public class TabCompleteListener extends PacketAdapter {

    private final Map<String, CommandInfo> commandMap;
    private final Map<String, Set<String>> tabCompletionSets;

    private final Map<UUID, String> lastTabComplete = new ConcurrentHashMap<>();

    private final String[] EMPTY_STRING_ARRAY = new String[0];

    public TabCompleteListener(Plugin plugin, Map<String, CommandInfo> commandMap,
                               Map<String, Set<String>> tabCompletionSets) {
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

        if (message.startsWith("/")) {
            if (!message.contains(" ")) {
                String commandStub = message.substring(1).toLowerCase();

                addCommandNameCompletions(player, commandStub, text);
            } else {
                String[] parts = message.split(" ");

                String commandName = parts[0].substring(1).toLowerCase();
                CommandInfo command = commandMap.get(commandName);
                if (command == null) return;

                if (command.getPermission() != null && !player.hasPermission(command.getPermission())) {
                    return;
                }

                StringBuilder middle = new StringBuilder();

                for (int index = 1; index < parts.length - 1; ++index) {
                    if (middle.length() > 0) middle.append(" ");

                    middle.append(parts[index]);
                }

                addCommandPartCompletions(command, middle.toString(), parts[parts.length - 1], text);
            }
        }

        packet.setText(text.toArray(EMPTY_STRING_ARRAY));
    }

    public void addCommandNameCompletions(Player player, String commandStub, Set<String> text) {
        for (CommandInfo commandInfo : commandMap.values()) {
            if (commandInfo.getPermission() != null && !player.hasPermission(commandInfo.getPermission())) {
                continue;
            }

            if (commandInfo.getName().startsWith(commandStub)) {
                text.add("/" + commandInfo.getName());
            }
        }
    }

    private void addCommandPartCompletions(CommandInfo command, String middle, String end, Set<String> text) {
        for (Map.Entry<Pattern, List<String>> tabCompletion : command.getTabCompletion().entrySet()) {
            if (!tabCompletion.getKey().matcher(middle).matches()) continue;

            for (String item : tabCompletion.getValue()) {
                if (!item.startsWith("#")) {
                    if (item.startsWith(end)) {
                        text.add(item);
                    }
                    continue;
                }

                String setName = item.substring(1);
                if (setName.isEmpty()) continue;

                Set<String> set = tabCompletionSets.get(setName);
                if (set == null) continue;

                for (String setItem : set) {
                    if (setItem.startsWith(end)) {
                        text.add(setItem);
                    }
                }
            }
        }
    }
}
