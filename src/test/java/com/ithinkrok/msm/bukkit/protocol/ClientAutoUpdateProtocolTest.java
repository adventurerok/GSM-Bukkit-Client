package com.ithinkrok.msm.bukkit.protocol;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.ithinkrok.msm.client.Client;
import com.ithinkrok.msm.common.Channel;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.fest.assertions.api.Assertions.assertThat;

/**
 * Created by paul on 05/02/16.
 */
@RunWith(DataProviderRunner.class)
public class ClientAutoUpdateProtocolTest {

    @Mock
    public Plugin mockPlugin;

    @Mock
    public Client mockClient;

    @Mock
    public Channel mockChannel;

    public Path pluginsPath;

    public ClientAutoUpdateProtocol sut;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

        pluginsPath = fs.getPath("/plugins");
        Files.createDirectory(pluginsPath);

        sut = new ClientAutoUpdateProtocol(mockPlugin, pluginsPath);
    }

    @Test
    public void pluginInstallPacketShouldInstallPlugin() throws IOException {
        ConfigurationSection payload = new MemoryConfiguration();

        payload.set("mode", "PluginInstall");

        String pluginName = "AnyPluginName";
        payload.set("name", pluginName);

        byte[] updateBytes = new byte[]{1, 2, 3, 4};
        payload.set("bytes", updateBytes);

        sut.packetRecieved(mockClient, mockChannel, payload);

        Path expectedFile = pluginsPath.resolve(pluginName + ".jar");
        assertThat(Files.exists(expectedFile)).isTrue();

        byte[] writtenBytes = Files.readAllBytes(expectedFile);
        assertThat(writtenBytes).isEqualTo(updateBytes);
    }
}