package com.ithinkrok.msm.client.command;

import com.ithinkrok.msm.common.command.CommandInfo;
import com.ithinkrok.util.config.Config;

/**
 * Created by paul on 09/03/16.
 */
public final class ClientCommandInfo extends CommandInfo {

    public ClientCommandInfo(Config config) {
        super(config.getString("name"), config);
    }
}
