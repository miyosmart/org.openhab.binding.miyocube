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
package org.openhab.binding.miyocube.internal.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.types.State;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link MiyoMessageParser} is responsible for parsing incoming messages
 *
 * @author Fabian Obernberger - Initial contribution
 */
@NonNullByDefault
public class MiyoUtils {

    /**
     * Parses a JSON-formatted message from the MiyoCube WebSocket and extracts relevant notification data.
     * Each notification is returned as a map with keys: deviceId, stateType, value.
     *
     * @param message the JSON message string received from the WebSocket
     * @param logger the logger for debug output
     * @return a list of maps, each containing parsed notification data (deviceId, stateType, value)
     */
    public static List<Map<String, String>> parse(String message, Logger logger) {
        List<Map<String, String>> messageList = new ArrayList<>();

        JsonNode dataJson;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            dataJson = objectMapper.readTree(message);
        } catch (Exception e) {
            logger.debug("Failed to parse message JSON: {}", e.getMessage());
            return messageList;
        }

        if (!dataJson.has("notification")) {
            return List.of();
        }
        String notificationString = dataJson.get("notification").asText();

        logger.debug("Notification {}", notificationString);
        try {
            if (("Device.stateChanged").equals(notificationString)) {
                JsonNode params = dataJson.get("params");
                Map<String, String> notification = new HashMap<>();
                notification.put("deviceId", params.get("deviceId").asText().replace("{", "").replace("}", ""));
                notification.put("stateType", params.get("type").asText());
                notification.put("value", params.get("value").asText());
                messageList.add(notification);
            } else if (("Device.updated").equals(notificationString)) {
                JsonNode params = dataJson.get("params");
                Map<String, String> notification = new HashMap<>();
                notification.put("deviceId", params.get("id").asText().replace("{", "").replace("}", ""));
                notification.put("stateType", "lastUpdate");
                notification.put("value", params.get("lastUpdate").asText());
                messageList.add(notification);
            } else if (("Circuit.stateChanged").equals(notificationString)) {
                JsonNode params = dataJson.get("params");
                Map<String, String> notification = new HashMap<>();
                notification.put("deviceId", params.get("circuitId").asText().replace("{", "").replace("}", ""));
                notification.put("stateType", params.get("type").asText());
                notification.put("value", params.get("value").asText());
                messageList.add(notification);
            } else if (("Circuit.edited").equals(notificationString)) {
                JsonNode params = dataJson.get("params");
                JsonNode circuit = params.get("circuit");
                JsonNode circuitParams = circuit.get("params");

                Map<String, String> notificationAutomaticMode = new HashMap<>();
                notificationAutomaticMode.put("deviceId", circuit.get("id").asText().replace("{", "").replace("}", ""));
                notificationAutomaticMode.put("stateType", "automaticMode");
                notificationAutomaticMode.put("value", circuitParams.get("automaticMode").asText());
                messageList.add(notificationAutomaticMode);

                Map<String, String> notificationValveStaggering = new HashMap<>();
                notificationValveStaggering.put("deviceId",
                        circuit.get("id").asText().replace("{", "").replace("}", ""));
                notificationValveStaggering.put("stateType", "valveStaggering");
                notificationValveStaggering.put("value", circuitParams.get("valveStaggering").asText());
                messageList.add(notificationValveStaggering);
            }
        } catch (Exception e) {
            logger.debug("Error parsing notification: {}", e.getMessage());
        }

        return messageList;
    }

    /**
     * Converts a state type and its string value from a device or circuit message into an openHAB {@link State} object.
     *
     * @param stateType the type of state as a string (e.g., "moisture", "valve-status")
     * @param value the value of the state as a string
     * @return the corresponding {@link State} object for openHAB channels
     */
    public static State convertState(String stateType, String value) {
        State newState;
        switch (stateType) {
            case "moisture", "solar-voltage", "temperature", "brightness":
                newState = new org.openhab.core.library.types.DecimalType(Double.parseDouble(value));
                break;
            case "valve-status", "valve2-status", "automatic-mode", "valve-staggering", "irrigation-was-started",
                    "reachable":
                newState = (("1").equals(value) || "true".equalsIgnoreCase(value))
                        ? org.openhab.core.library.types.OnOffType.ON
                        : org.openhab.core.library.types.OnOffType.OFF;
                break;
            case "last-update":
                long epochSeconds = Long.parseLong(value);
                Instant instant = Instant.ofEpochSecond(epochSeconds);
                ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
                newState = new DateTimeType(zdt);
                break;
            default:
                newState = org.openhab.core.library.types.StringType.valueOf(value);
                break;
        }
        return newState;
    }

    /**
     * Converts a camelCase string to a hyphen-separated lowercase string.
     *
     * @param input the camelCase input string
     * @return the hyphen-separated lowercase string
     */
    public static String camelToHyphen(String input) {
        return input.replaceAll("([a-z1-9])([A-Z])", "$1-$2").toLowerCase();
    }
}
