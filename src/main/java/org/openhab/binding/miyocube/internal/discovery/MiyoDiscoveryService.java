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
package org.openhab.binding.miyocube.internal.discovery;

import static org.openhab.binding.miyocube.internal.MiyoCubeBindingConstants.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.miyocube.internal.handler.MiyoCubeHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The {@link MiyoDiscoveryService} is responsible for discovering Miyo devices on a connected Cube bridge
 *
 * @author Fabian Obernberger - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = MiyoDiscoveryService.class)
@NonNullByDefault
public class MiyoDiscoveryService extends AbstractThingHandlerDiscoveryService<MiyoCubeHandler> {

    private final Logger logger = LoggerFactory.getLogger(MiyoDiscoveryService.class);

    private static final int TIMEOUT_SECONDS = 5;
    private @Nullable HttpClientFactory httpClientFactory;

    public MiyoDiscoveryService() {
        super(MiyoCubeHandler.class, Set.of(THING_TYPE_CIRCUIT, THING_TYPE_VALVE, THING_TYPE_SENSOR), TIMEOUT_SECONDS,
                true);
        logger.debug("Discovery service initialized");
    }

    @org.osgi.service.component.annotations.Reference
    protected void setHttpClientFactory(HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    @Override
    protected void startScan() {
        HttpClientFactory httpClientFactory = this.httpClientFactory;
        if (httpClientFactory == null) {
            logger.warn("HttpClientFactory is not set, cannot perform discovery");
            return;
        }
        try {
            HttpClient httpClient = httpClientFactory.createHttpClient("MiyoHttpDisc");
            httpClient.start();
            MiyoCubeHandler thingHandler = this.getThingHandler();
            if (thingHandler == null) {
                logger.warn("Thing handler is null, cannot perform discovery");
                return;
            }
            String url = String.format("http://%s/api/circuit/all?apiKey=%s", thingHandler.getHost(),
                    URLEncoder.encode(thingHandler.getApiKey(), StandardCharsets.UTF_8));

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

                    JsonNode circuitsData = jsonNode.get("params").get("circuits");

                    if (!circuitsData.isObject()) {
                        logger.debug("No circuits found in response");
                        return;
                    }

                    for (Map.Entry<String, JsonNode> entry : circuitsData.properties()) {
                        JsonNode circuit = entry.getValue();
                        String circuitId = entry.getKey().replace("{", "").replace("}", "");
                        String circuitName = circuit.get("name").asText();
                        String circuitLabel = "Miyo Bewässerungskreis " + circuitName;

                        ThingUID circuitUID = new ThingUID(THING_TYPE_CIRCUIT, thingHandler.getThing().getUID(),
                                circuitId);

                        DiscoveryResult circuitResult = DiscoveryResultBuilder.create(circuitUID)
                                .withLabel(circuitLabel).withBridge(thingHandler.getThing().getUID()).build();

                        thingDiscovered(circuitResult);

                        if (circuit.has("sensorData")) {
                            String ip = circuit.get("sensorData").get("ipv6").asText();
                            String id = circuit.get("sensorData").get("id").asText().replace("{", "").replace("}", "");
                            String label = "Miyo Sensor: " + ip;

                            ThingUID sensorUID = new ThingUID(THING_TYPE_SENSOR, thingHandler.getThing().getUID(), id);

                            DiscoveryResult sensorResult = DiscoveryResultBuilder.create(sensorUID).withLabel(label)
                                    .withProperty("ip address", ip).withProperty("circuit", circuitName)
                                    .withBridge(thingHandler.getThing().getUID()).build();
                            thingDiscovered(sensorResult);
                        }

                        JsonNode valves = circuit.get("valves");
                        for (Map.Entry<String, JsonNode> valveEntry : valves.properties()) {
                            JsonNode valve = valveEntry.getValue();
                            JsonNode valveData = valve.get("valveData");

                            String ip = valveData.get("ipv6").asText();
                            String id = valveData.get("id").asText().replace("{", "").replace("}", "") + "_"
                                    + valve.get("channel").asText();
                            String label = "Miyo Valve: " + ip;
                            if (valveData.get("hardwareRevision").asText().equals("1")) {
                                label += " - " + valve.get("channel").asText();
                            }

                            ThingUID valveUID = new ThingUID(THING_TYPE_VALVE, thingHandler.getThing().getUID(), id);

                            DiscoveryResult valveResult = DiscoveryResultBuilder.create(valveUID).withLabel(label)
                                    .withProperty("ip address", ip).withProperty("circuit", circuitName)
                                    .withBridge(thingHandler.getThing().getUID()).build();
                            thingDiscovered(valveResult);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Error requesting circuit data {}", e.getMessage());
            } finally {
                try {
                    httpClient.stop();
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            logger.info("Discovery failed", e);
        }
    }
}
