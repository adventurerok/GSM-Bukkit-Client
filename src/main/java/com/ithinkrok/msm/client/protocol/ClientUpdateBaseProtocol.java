package com.ithinkrok.msm.client.protocol;

import com.ithinkrok.msm.client.Client;
import com.ithinkrok.msm.client.ClientListener;
import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.util.config.Config;
import com.ithinkrok.util.config.MemoryConfig;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by paul on 11/03/16.
 */
public abstract class ClientUpdateBaseProtocol implements ClientListener {

    private final boolean primary;
    private final Map<String, IncompleteResource> incompleteResourceMap = new ConcurrentHashMap<>();

    private Map<String, Instant> resourceVersions;

    public ClientUpdateBaseProtocol(boolean primary) {
        this.primary = primary;
    }

    @Override
    public void connectionOpened(Client client, Channel channel) {
        if (!primary) return;

        if(resourceVersions == null) {
            resourceVersions = getResourceVersions();
        }

        sendResourceInfo(channel);
    }

    private void sendResourceInfo(Channel channel) {
        Config versions = new MemoryConfig('\0');

        for (Map.Entry<String, Instant> version : resourceVersions.entrySet()) {
            versions.set(version.getKey(), version.getValue().toEpochMilli());
        }

        Config payload = new MemoryConfig('\0');

        payload.set("versions", versions);
        payload.set("mode", "ResourceInfo");

        channel.write(payload);
    }

    protected abstract Map<String, Instant> getResourceVersions();

    @Override
    public void connectionClosed(Client client) {
        incompleteResourceMap.clear();
    }

    @Override
    public void packetRecieved(Client client, Channel channel, Config payload) {
        String mode = payload.getString("mode");
        if (mode == null) return;

        switch (mode) {
            case "ResourceUpdate":
                handleResourceUpdate(payload);
        }
    }

    private void handleResourceUpdate(Config payload) {
        String resourceName = payload.getString("resource");

        byte[] bytes = payload.getByteArray("bytes");

        int index = payload.getInt("index");
        int length = payload.getInt("length");

        Instant version = Instant.ofEpochMilli(payload.getLong("version"));

        if (index != 0 && bytes.length != length) {
            IncompleteResource resource = incompleteResourceMap.get(resourceName);
            if (resource == null) {
                resource = new IncompleteResource(length);
                incompleteResourceMap.put(resourceName, resource);
            }

            System.arraycopy(bytes, 0, resource.bytes, index, bytes.length);
            resource.downloadRange = Range.add(resource.downloadRange, new Range(index, bytes.length));

            if (resource.downloadRange.start > 0 || resource.downloadRange.endPlusOne < length) return;

            incompleteResourceMap.remove(resourceName);

            resourceVersions.put(resourceName, version);
            updateResource(resourceName, resource.bytes);
        } else {
            resourceVersions.put(resourceName, version);
            updateResource(resourceName, bytes);
        }
    }

    protected abstract boolean updateResource(String name, byte[] update);

    private static class IncompleteResource {
        byte[] bytes;

        Range downloadRange;

        public IncompleteResource(int length) {
            bytes = new byte[length];
        }
    }

    private static class Range {

        private final int start;
        private int endPlusOne;

        private Range next;

        public Range(int start, int length) {
            this.start = start;
            this.endPlusOne = start + length;
        }

        public static Range add(Range one, Range two) {
            if (one == null) return two;
            else if (two == null) return one;

            if (one.start < two.start) {
                one.add(two);
                return one;
            } else {
                two.add(one);
                return two;
            }
        }

        public void add(Range range) {
            if (range.start == endPlusOne) {
                this.endPlusOne = range.endPlusOne;
            } else if (next == null) {
                next = range;
            } else {
                if (range.start < next.start) {
                    range.add(next);
                    this.next = range;
                } else {
                    next.add(range);
                }
            }
        }
    }
}
