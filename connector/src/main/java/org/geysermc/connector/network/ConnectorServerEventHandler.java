/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.connector.network;

import com.github.steveice10.mc.protocol.data.status.ServerStatusInfo;
import com.nukkitx.protocol.bedrock.BedrockPong;
import com.nukkitx.protocol.bedrock.BedrockServerEventHandler;
import com.nukkitx.protocol.bedrock.BedrockServerSession;
import org.geysermc.api.Player;
import org.geysermc.api.events.PingEvent;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.configuration.GeyserConfiguration;
import org.geysermc.connector.console.GeyserLogger;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.utils.MessageUtils;

import java.net.InetSocketAddress;

public class ConnectorServerEventHandler implements BedrockServerEventHandler {

    private GeyserConnector connector;

    public ConnectorServerEventHandler(GeyserConnector connector) {
        this.connector = connector;
    }

    @Override
    public boolean onConnectionRequest(InetSocketAddress inetSocketAddress) {
        GeyserLogger.DEFAULT.info(inetSocketAddress + " tried to connect!");
        return true;
    }

    @Override
    public BedrockPong onQuery(InetSocketAddress inetSocketAddress) {
        GeyserLogger.DEFAULT.debug(inetSocketAddress + " has pinged you!");
        GeyserConfiguration config = connector.getConfig();
        PingEvent pongEvent = new PingEvent(inetSocketAddress);
        pongEvent.setEdition("MCPE");
        pongEvent.setGameType("Default");
        pongEvent.setNintendoLimited(false);
        pongEvent.setProtocolVersion(GeyserConnector.BEDROCK_PACKET_CODEC.getProtocolVersion());
        pongEvent.setVersion(GeyserConnector.BEDROCK_PACKET_CODEC.getMinecraftVersion());

        connector.getPluginManager().runEvent(pongEvent);
        if (connector.getConfig().isPingPassthrough()) {
            ServerStatusInfo serverInfo = connector.getPassthroughThread().getInfo();

            if (serverInfo != null) {
                pongEvent.setMotd(MessageUtils.getBedrockMessage(serverInfo.getDescription()));
                pongEvent.setSubMotd(config.getBedrock().getMotd2());
                pongEvent.setPlayerCount(serverInfo.getPlayerInfo().getOnlinePlayers());
                pongEvent.setMaximumPlayerCount(serverInfo.getPlayerInfo().getMaxPlayers());
            }
        } else {
            pongEvent.setPlayerCount(1);
            pongEvent.setMaximumPlayerCount(config.getMaxPlayers());
            pongEvent.setMotd(config.getBedrock().getMotd1());
            pongEvent.setSubMotd(config.getBedrock().getMotd2());
        }

        BedrockPong pong = new BedrockPong();
        pong.setEdition(pongEvent.getEdition());
        pong.setGameType(pongEvent.getGameType());
        pong.setNintendoLimited(pongEvent.isNintendoLimited());
        pong.setProtocolVersion(pongEvent.getProtocolVersion());
        pong.setVersion(pongEvent.getVersion());
        pong.setMotd(pongEvent.getMotd());
        pong.setSubMotd(pongEvent.getSubMotd());
        pong.setPlayerCount(pongEvent.getPlayerCount());
        pong.setMaximumPlayerCount(pongEvent.getMaximumPlayerCount());
        pong.setIpv4Port(config.getBedrock().getPort());

        return pong;
    }

    @Override
    public void onSessionCreation(BedrockServerSession bedrockServerSession) {
        bedrockServerSession.setLogging(true);
        bedrockServerSession.setPacketHandler(new UpstreamPacketHandler(connector, new GeyserSession(connector, bedrockServerSession)));
        bedrockServerSession.addDisconnectHandler(disconnectReason -> {
            GeyserLogger.DEFAULT.info("Bedrock user with ip: " + bedrockServerSession.getAddress().getAddress() + " has disconnected for reason " + disconnectReason);

            GeyserSession player = connector.getPlayers().get(bedrockServerSession.getAddress());
            if (player != null) {
                player.disconnect(disconnectReason.name());
                connector.removePlayer(player);

                player.getEntityCache().clear();
                player.getInventoryCache().getInventories().clear();
                player.getWindowCache().getWindows().clear();
                player.getScoreboardCache().removeScoreboard();
            }
        });
        bedrockServerSession.setPacketCodec(GeyserConnector.BEDROCK_PACKET_CODEC);
    }
}