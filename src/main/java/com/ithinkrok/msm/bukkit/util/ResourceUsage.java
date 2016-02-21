package com.ithinkrok.msm.bukkit.util;

import com.ithinkrok.msm.common.Channel;
import com.ithinkrok.util.config.Config;
import com.ithinkrok.util.config.MemoryConfig;

import java.util.Arrays;

/**
 * Created by paul on 16/02/16.
 */
public class ResourceUsage implements Runnable {

    /**
     * The interval in ticks between successive calls to run()
     */
    private final long tickInterval = 20;
    private final int historySize = 60;
    private final double[] tpsHistory = new double[historySize];
    private final double[] ramHistory = new double[historySize];
    /**
     * The last time polled, in ns
     */
    private long lastPoll = System.nanoTime();
    private double averageTPS;
    private double averageRamUsage;
    private int historyIndex = 0;

    private double lastSentTPS;
    private double lastSentRamUsage;
    private double lastSentMaxRam;
    private double lastSentAllocatedRam;

    private boolean sendUpdate = true;

    private Channel channel;

    public ResourceUsage() {
        Arrays.fill(tpsHistory, averageTPS = 20);
        Arrays.fill(ramHistory, averageRamUsage = calculateUsedRam());

        lastSentTPS = averageTPS;
        lastSentRamUsage = averageRamUsage;
    }

    private double calculateUsedRam() {
        long freeRamBytes = Runtime.getRuntime().freeMemory();
        long allocatedRamBytes = Runtime.getRuntime().totalMemory();

        long usedRamBytes = allocatedRamBytes - freeRamBytes;

        return usedRamBytes / 1_048_576d;
    }

    public long getTickInterval() {
        return tickInterval;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;

        sendUpdate = true;
    }

    @Override
    public void run() {
        tpsHistory[historyIndex] = calculateTPS();
        ramHistory[historyIndex] = calculateUsedRam();

        ++historyIndex;
        if (historyIndex >= historySize) historyIndex = 0;

        averageTPS = average(tpsHistory);
        //TPS higher than 20 should not be recorded
        if (averageTPS > 20) averageTPS = 20;

        averageRamUsage = average(ramHistory);

        double tpsDifference = Math.abs(averageTPS - lastSentTPS);

        double ramDifferencePercent = Math.abs(averageRamUsage - lastSentRamUsage) / lastSentRamUsage;

        boolean sendTpsUpdate = averageTPS > 19 ? tpsDifference > 0.1d : tpsDifference > 0.2d;
        boolean sendRamUpdate = ramDifferencePercent > 0.1d;


        if (sendTpsUpdate || sendRamUpdate || getAllocatedMemory() != lastSentAllocatedRam ||
                getMaxMemory() != lastSentMaxRam) {
            sendUpdate = true;
        }

        if (sendUpdate) sendUpdate();
    }

    private double calculateTPS() {
        long currentTime = System.nanoTime(); //ns

        long timeSpent = currentTime - lastPoll; //ns

        if (timeSpent <= 1) return 20;

        double timeSpentTicks = timeSpent / 1_000_000d / 50d;

        double tps = (tickInterval / timeSpentTicks) * 20d;

        //Prevent tps from being too high and skewing the average
        if (tps > 21) tps = 21;

        lastPoll = currentTime;

        return tps;
    }

    private double average(double... items) {
        double total = 0;

        for (double item : items) {
            total += item;
        }

        return total / items.length;
    }

    public double getAllocatedMemory() {
        return Runtime.getRuntime().totalMemory() / 1_048_576d;
    }

    public double getMaxMemory() {
        return Runtime.getRuntime().maxMemory() / 1_048_576d;
    }

    private void sendUpdate() {
        if (channel == null) return;

        sendUpdate = false;

        Config payload = toConfig();

        channel.write(payload);
    }

    private Config toConfig() {
        Config config = new MemoryConfig();

        config.set("average_tps", lastSentTPS = averageTPS);
        config.set("average_ram", lastSentRamUsage = averageRamUsage);
        config.set("max_ram", lastSentMaxRam = getMaxMemory());
        config.set("allocated_ram", lastSentAllocatedRam = getAllocatedMemory());
        config.set("mode", "ResourceUsage");

        return config;
    }

    public double getAverageRamUsage() {
        return averageRamUsage;
    }

    public double getAverageTPS() {
        return averageTPS;
    }
}
