/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.miyocube.internal.websocket;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.common.WebSocketSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link MiyoWebsocketClient} is responsible for handling websocket communication
 *
 * @author Fabian Obernberger - Initial contribution
 */
@NonNullByDefault
@WebSocket
public class MiyoWebsocket {

    private final Logger logger = LoggerFactory.getLogger(MiyoWebsocket.class);
    MiyoWebsocketEventHandler eventHandler;

    private String url = "";
    private String apiKey = "";
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final long retryDelaySeconds = 60;
    private final WebSocketClient webSocketClient;
    private @Nullable WebSocketSession session;
    private boolean disconnected = false;

    public MiyoWebsocket(WebSocketClient webSocketClient, String host, String apiKey,
            MiyoWebsocketEventHandler eventHandler) {
        this.url = "ws://" + host + ":3810";
        this.eventHandler = eventHandler;
        this.apiKey = apiKey;
        this.webSocketClient = webSocketClient;
    }

    /**
     * Connects to the WebSocket server.
     */
    public void connect() {
        try {
            logger.debug("Connecting to Miyo WebSocket at {}", url);
            session = (WebSocketSession) webSocketClient.connect(this, new URI(this.url)).get();
        } catch (Exception e) {
            logger.warn("Error connecting to WebSocket: {}", e.getMessage(), e);
            scheduleReconnect();
        }
    }

    /**
     * Disconnects the WebSocket connection.
     */
    public void disconnect() {
        Session session = this.session;
        try {
            if (session != null && session.isOpen()) {
                session.close();
                disconnected = true;
                logger.debug("WS Session closed");
            }
        } catch (Exception e) {
            logger.warn("Error closing WebSocket session: {}", e.getMessage());
        }
    }

    /**
     * Sends a message through the WebSocket connection.
     * 
     * @param data the data to send
     */
    public void send(Map<String, Object> data) {
        Session session = this.session;
        ObjectMapper objectMapper = new ObjectMapper();
        if (session != null && session.isOpen()) {
            logger.debug("WS Session is open");
            try {
                data.put("id", 1);
                data.put("apiKey", this.apiKey);
                session.getRemote().sendString(objectMapper.writeValueAsString(data));
            } catch (Exception e) {
                logger.warn("Error sending message: {}", e.getMessage());
            }
        } else {
            logger.debug("WS Session is not open");
        }
    }

    @OnWebSocketConnect
    public synchronized void onConnect(Session session) {
        logger.info("WebSocket connected: {}", session.getRemoteAddress().getAddress());
        eventHandler.onOpened();
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        logger.debug("Miyo WebSocket closed: {} - {}", statusCode, reason);
        scheduleReconnect();
        eventHandler.onError();
    }

    @OnWebSocketError
    public void onError(Throwable cause) {
        logger.debug("Miyo WebSocket error ({})", cause.getMessage());
        scheduleReconnect();
        eventHandler.onError();
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        eventHandler.onMessage(msg);
    }

    /**
     * Schedules a reconnect attempt after a delay.
     */
    private void scheduleReconnect() {
        if (disconnected) {
            logger.debug("WebSocket client is marked as disconnected. Not attempting to reconnect.");
            return;
        }

        logger.info("Attempting to reconnect WebSocket in {} seconds", retryDelaySeconds);
        scheduler.schedule(this::connect, retryDelaySeconds, TimeUnit.SECONDS);
        eventHandler.onError();
    }
}
