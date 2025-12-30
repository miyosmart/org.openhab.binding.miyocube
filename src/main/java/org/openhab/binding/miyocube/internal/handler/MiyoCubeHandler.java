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
package org.openhab.binding.miyocube.internal.handler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.miyocube.internal.utils.MiyoUtils;
import org.openhab.binding.miyocube.internal.websocket.MiyoWebsocket;
import org.openhab.binding.miyocube.internal.websocket.MiyoWebsocketEventHandler;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.io.net.http.WebSocketFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link MiyoCubeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Fabian Obernberger - Initial contribution
 */
@NonNullByDefault
public class MiyoCubeHandler extends BaseBridgeHandler implements MiyoWebsocketEventHandler {

    private final Logger logger = LoggerFactory.getLogger(MiyoCubeHandler.class);

    private @Nullable MiyoWebsocket wsClient;
    private String apiKey = "";
    private String host = "";
    private final HttpClient httpClient;
    private final WebSocketClient webSocketClient;

    public MiyoCubeHandler(Bridge bridge, HttpClientFactory httpClientFactory, WebSocketFactory webSocketFactory) {
        super(bridge);
        this.httpClient = httpClientFactory.createHttpClient("MiyoHttp");
        this.webSocketClient = webSocketFactory.createWebSocketClient("MiyoWS");
        try {
            this.httpClient.start();
            this.webSocketClient.start();
        } catch (Exception e) {
            logger.debug("Failed to start HTTP or WebSocket client: {}", e.getMessage());
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> params = new HashMap<>();
        Integer duration = null;
        switch (channelUID.getId()) {
            case "start-irrigation":
                String circuitId = channelUID.getThingUID().getId();

                for (Thing child : this.getThing().getThings()) {
                    if (child.getUID().getId().equals(circuitId)) {
                        try {
                            ThingHandler childHandler = child.getHandler();
                            if (childHandler instanceof MiyoCircuitHandler) {
                                duration = ((MiyoCircuitHandler) childHandler).getCurrentDuration();
                            }
                        } catch (Exception e) {
                            logger.debug("Failed to get current duration: {}", e.getMessage());
                        }
                    }
                }

                if (duration != null) {
                    params.put("circuitId", circuitId);
                    params.put("mode", "start");
                    params.put("duration", duration);
                    data.put("params", params);
                    data.put("method", "Circuit.irrigation");
                    MiyoWebsocket ws = this.wsClient;
                    if (ws != null) {
                        ws.send(data);
                    }
                }
                break;

            case "stop-irrigation":
                params.put("circuitId", channelUID.getThingUID().getId());
                params.put("mode", "stop");
                data.put("params", params);
                data.put("method", "Circuit.irrigation");
                MiyoWebsocket ws = this.wsClient;
                if (ws != null) {
                    ws.send(data);
                }
                break;
        }
    }

    @Override
    public void initialize() {
        this.host = getConfig().get("host").toString();
        this.apiKey = getThing().getProperties().getOrDefault("apiKey", "").toString();

        updateStatus(ThingStatus.UNKNOWN);
        updateAllChildStatus();

        if (!("").equals(apiKey)) {
            logger.debug("API key found in configuration, trying to connect to WebSocket");
            wsClient = new MiyoWebsocket(webSocketClient, this.host, this.apiKey, this);
            MiyoWebsocket ws = this.wsClient;
            if (ws != null) {
                ws.connect();
            }
            initializeDefaultStates();
        } else {
            scheduler.submit(() -> {
                try {
                    this.apiKey = requestApiKey();

                    if (!("").equals(apiKey)) {
                        logger.debug("API key retrieved successfully");
                        updateProperty("apiKey", this.apiKey);

                        wsClient = new MiyoWebsocket(webSocketClient, this.host, this.apiKey, this);
                        MiyoWebsocket ws = this.wsClient;
                        if (ws != null) {
                            ws.connect();
                        }
                        initializeDefaultStates();
                    } else {
                        logger.warn("API key could not be retrieved");
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                                "@text/offline.communication-error-api");
                        updateAllChildStatus();
                    }

                } catch (Exception e) {
                    logger.warn("Error during initialization: {}", e.getMessage());
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                    updateAllChildStatus();
                }
            });
        }
    }

    /**
     * Initializes the default states of all child things (circuits, sensors, valves) by querying the MiyoCube API for
     * current data.
     * 
     * For each discovered circuit, sensor, and valve, this method updates the corresponding child thing channels with
     * their latest values
     * (e.g., automatic mode, valve staggering, irrigation status, sensor readings, valve status, etc.).
     * Channel IDs and values are mapped according to the thing-types and channel-types defined in the binding.
     *
     * This method is typically called after a successful WebSocket connection or API key retrieval to ensure all child
     * things reflect the latest state.
     */
    public void initializeDefaultStates() {
        JsonNode circuitsData = requestCircuitData();
        for (Map.Entry<String, JsonNode> entry : circuitsData.properties()) {
            JsonNode circuit = entry.getValue();
            String circuitId = entry.getKey().replace("{", "").replace("}", "");
            if (circuit.has("params")) {
                JsonNode params = circuit.get("params");
                if (params != null) {
                    if (params.has("automaticMode")) {
                        JsonNode automaticJsonNode = params.get("automaticMode");
                        updateChildThingChannel(circuitId, "automatic-mode", automaticJsonNode.asText());
                    }
                    if (params.has("valveStaggering")) {
                        JsonNode valveStaggeringJsonNode = params.get("valveStaggering");
                        updateChildThingChannel(circuitId, "valve-staggering", valveStaggeringJsonNode.asText());
                    }
                }
                if (circuit.has("stateTypes")) {
                    JsonNode stateTypes = circuit.get("stateTypes");
                    JsonNode irrigationWasStartedNode = findStateType(stateTypes, "irrigationWasStarted");
                    if (irrigationWasStartedNode != null) {
                        updateChildThingChannel(circuitId, "irrigation-was-started",
                                irrigationWasStartedNode.get("value").asText());
                    }
                }
            }
            if (circuit.has("sensorData")) {
                JsonNode sensorData = circuit.get("sensorData");
                String sensorId = sensorData.get("id").asText().replace("{", "").replace("}", "");
                if (sensorData.has("lastUpdate")) {
                    JsonNode lastUpdateNode = sensorData.get("lastUpdate");
                    updateChildThingChannel(sensorId, "last-update", lastUpdateNode.asText());
                }
                if (sensorData.has("stateTypes")) {
                    JsonNode stateTypes = sensorData.get("stateTypes");
                    JsonNode moistureNode = findStateType(stateTypes, "moisture");
                    if (moistureNode != null) {
                        updateChildThingChannel(sensorId, "moisture", moistureNode.get("value").asText());
                    }
                    JsonNode temperatureNode = findStateType(stateTypes, "temperature");
                    if (temperatureNode != null) {
                        updateChildThingChannel(sensorId, "temperature", temperatureNode.get("value").asText());
                    }
                    JsonNode brightnessNode = findStateType(stateTypes, "brightness");
                    if (brightnessNode != null) {
                        updateChildThingChannel(sensorId, "brightness", brightnessNode.get("value").asText());
                    }
                    JsonNode solarVoltageNode = findStateType(stateTypes, "solarVoltage");
                    if (solarVoltageNode != null) {
                        updateChildThingChannel(sensorId, "solar-voltage", solarVoltageNode.get("value").asText());
                    }
                    JsonNode reachableNode = findStateType(stateTypes, "reachable");
                    if (reachableNode != null) {
                        updateChildThingChannel(sensorId, "reachable", reachableNode.get("value").asText());
                    }
                }
            }
            if (circuit.has("valves")) {
                JsonNode valves = circuit.get("valves");
                for (Map.Entry<String, JsonNode> valveEntry : valves.properties()) {
                    JsonNode valve = valveEntry.getValue();
                    if (valve.has("valveData")) {
                        JsonNode valveData = valve.get("valveData");
                        String valveId = valveData.get("id").asText().replace("{", "").replace("}", "");
                        if (valveData.has("lastUpdate")) {
                            JsonNode lastUpdateNode = valveData.get("lastUpdate");
                            updateChildThingChannel(valveId, "last-update", lastUpdateNode.asText());
                        }
                        if (valveData.has("stateTypes")) {
                            JsonNode stateTypes = valveData.get("stateTypes");
                            JsonNode valveStatusNode = findStateType(stateTypes, "valveStatus");
                            if (valveStatusNode != null) {
                                updateChildThingChannel(valveId, "valve-status", valveStatusNode.get("value").asText());
                            }
                            JsonNode valve2StatusNode = findStateType(stateTypes, "valve2Status");
                            if (valve2StatusNode != null) {
                                updateChildThingChannel(valveId, "valve2-status",
                                        valve2StatusNode.get("value").asText());
                            }
                            JsonNode solarVoltageNode = findStateType(stateTypes, "solarVoltage");
                            if (solarVoltageNode != null) {
                                updateChildThingChannel(valveId, "solar-voltage",
                                        solarVoltageNode.get("value").asText());
                            }
                            JsonNode reachableNode = findStateType(stateTypes, "reachable");
                            if (reachableNode != null) {
                                updateChildThingChannel(valveId, "reachable", reachableNode.get("value").asText());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates the state of a specific channel for a child thing (circuit, sensor, or valve).
     * 
     * Finds the child thing by its ID, converts the provided value to an openHAB {@link State} using the channel ID,
     * and updates the channel state via the child handler. If the channel is "reachable", the child thing's status is
     * set to ONLINE or OFFLINE accordingly; otherwise, the status is set to ONLINE after the update.
     *
     * @param childId the ID of the child thing to update
     * @param channelId the ID of the channel to update
     * @param value the new value to set for the channel
     */
    private void updateChildThingChannel(String childId, String channelId, String value) {
        for (Thing child : this.getThing().getThings()) {
            if (child.getUID().getId().equals(childId)) {
                // Update the corresponding channel based on stateType and value
                ThingHandler childHandler = child.getHandler();
                if (childHandler != null) {
                    if (childHandler instanceof MiyoThingHandler) {
                        State newState = MiyoUtils.convertState(channelId, value);
                        ChannelUID channel = new ChannelUID(child.getUID(), channelId);
                        logger.debug("handle bridge update for channel: {} and new state: {}", channel, newState);
                        ((MiyoThingHandler) childHandler).handleBridgeUpdate(channel, newState);
                        if (("reachable").equals(channelId)) {
                            if (newState == org.openhab.core.library.types.OnOffType.OFF) {
                                // set child thing to OFFLINE if reachable is OFF
                                ((MiyoThingHandler) childHandler).updateChildStatus(ThingStatus.OFFLINE,
                                        ThingStatusDetail.COMMUNICATION_ERROR,
                                        "@text/offline.communication-error-not-reachable");
                            } else {
                                // set child thing to ONLINE if reachable is ON
                                ((MiyoThingHandler) childHandler).updateChildStatus(ThingStatus.ONLINE,
                                        ThingStatusDetail.NONE);
                            }
                        } else {
                            // for other channels, just set to ONLINE
                            ((MiyoThingHandler) childHandler).updateChildStatus(ThingStatus.ONLINE,
                                    ThingStatusDetail.NONE);
                        }
                    }
                }
            }
        }
    }

    /**
     * Finds a state type node within the provided JSON array of state types.
     * 
     * @param jsonStateTypes the JSON array of state types
     * @param stateTypeString the state type string to find
     * @return the matching state type JSON node, or null if not found
     */
    @Nullable
    private JsonNode findStateType(JsonNode jsonStateTypes, String stateTypeString) {
        for (JsonNode stateTypeNode : jsonStateTypes) {
            if (stateTypeNode.get("type").asText().equals(stateTypeString)) {
                return stateTypeNode;
            }
        }
        return null;
    }

    @Override
    public void dispose() {
        MiyoWebsocket ws = this.wsClient;
        if (ws != null) {
            ws.disconnect();
        }
        super.dispose();
    }

    /**
     * Requests an API key from the MiyoCube by sending an HTTP GET request to the /api/link endpoint.
     *
     * If successful, returns the API key as a string; otherwise, returns an empty string.
     *
     * @return the API key retrieved from the MiyoCube, or an empty string if not available
     */
    private String requestApiKey() {
        String url = "http://" + this.host + "/api/link";

        try {
            httpClient.start();

            ContentResponse response = httpClient.newRequest(url).method(HttpMethod.GET)
                    .header(HttpHeader.ACCEPT, "application/json")
                    .timeout(5000, java.util.concurrent.TimeUnit.MILLISECONDS).send();

            if (response.getStatus() == 200) {
                String json = response.getContentAsString();
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(json);

                if (jsonNode.has("apiKey")) {
                    return jsonNode.get("apiKey").asText();
                }
            }

        } catch (Exception e) {
            logger.debug("Error requesting api key: {}", e.getMessage());
        } finally {
            try {
                httpClient.stop();
            } catch (Exception ignore) {
            }
        }

        return "";
    }

    /**
     * Returns the current API key used for communication with the MiyoCube.
     *
     * @return the API key as a string
     */
    public String getApiKey() {
        return this.apiKey;
    }

    /**
     * Returns the host address (IP or hostname) of the MiyoCube.
     *
     * @return the host address as a string
     */
    public String getHost() {
        return this.host;
    }

    @Override
    public void onMessage(String message) {
        List<Map<String, String>> parsedMessages = MiyoUtils.parse(message, logger);
        for (Map<String, String> msg : parsedMessages) {
            String deviceId = msg.getOrDefault("deviceId", "");
            String stateType = msg.getOrDefault("stateType", "");
            stateType = MiyoUtils.camelToHyphen(stateType);
            String value = msg.getOrDefault("value", "");

            updateChildThingChannel(deviceId, stateType, value);
        }
    }

    /**
     * Requests the current circuit data from the MiyoCube via HTTP GET.
     *
     * Sends a request to the /api/circuit/all endpoint using the configured host and API key.
     * If successful, returns the JSON node containing all circuits; otherwise, returns an empty object node.
     *
     * @return a {@link JsonNode} containing the circuits data, or an empty node if not available
     */
    private JsonNode requestCircuitData() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode circuitsData = mapper.createObjectNode();
        logger.debug("Starting discovery");
        try {
            String url = String.format("http://%s/api/circuit/all?apiKey=%s", getHost(),
                    URLEncoder.encode(getApiKey(), StandardCharsets.UTF_8));

            logger.debug("Requesting circuit data from {}", url);

            try {
                httpClient.start();

                ContentResponse response = httpClient.newRequest(url).method(HttpMethod.GET)
                        .header(HttpHeader.ACCEPT, "application/json")
                        .timeout(5000, java.util.concurrent.TimeUnit.MILLISECONDS).send();

                if (response.getStatus() == 200) {
                    String json = response.getContentAsString();
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonNode = objectMapper.readTree(json);

                    if (jsonNode.has("params") && jsonNode.get("params").has("circuits")) {
                        circuitsData = jsonNode.get("params").get("circuits");
                    } else {
                        logger.debug("No circuits found in response");
                    }
                }
            } catch (Exception e) {
                logger.warn("Error requesting circuit data: {}", e.getMessage());
            } finally {
                try {
                    httpClient.stop();
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            logger.warn("Error during circuit data request: {}", e.getMessage());
        }
        return circuitsData;
    }

    @Override
    public void onError() {
        logger.debug("WebSocket connection error");
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                "@text/offline.communication-error-ws");
        updateAllChildStatus();
    }

    @Override
    public void onOpened() {
        logger.debug("WebSocket connection opened");
        updateStatus(ThingStatus.ONLINE);
        updateAllChildStatus();
        initializeDefaultStates();
    }

    /**
     * Updates the status of all child things (circuits, sensors, valves) to match the current status of the bridge
     * thing.
     */
    private void updateAllChildStatus() {
        for (Thing child : this.getThing().getThings()) {
            ThingHandler childHandler = child.getHandler();
            if (childHandler != null && childHandler instanceof MiyoThingHandler) {
                ((MiyoThingHandler) childHandler).updateChildStatus(getThing().getStatusInfo());
            }
        }
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof MiyoCircuitHandler) {
            ((MiyoCircuitHandler) childHandler).updateChildStatus(getThing().getStatusInfo());
        }
        initializeDefaultStates();
    }
}
