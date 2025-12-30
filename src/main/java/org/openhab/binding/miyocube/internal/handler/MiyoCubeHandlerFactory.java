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

import static org.openhab.binding.miyocube.internal.MiyoCubeBindingConstants.*;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.miyocube.internal.discovery.MiyoDiscoveryService;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.io.net.http.WebSocketFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link MiyoCubeHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Fabian Obernberger - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.miyocube", service = ThingHandlerFactory.class)
public class MiyoCubeHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_CUBE, THING_TYPE_SENSOR,
            THING_TYPE_VALVE, THING_TYPE_CIRCUIT);

    private final HttpClientFactory httpClientFactory;
    private final WebSocketFactory webSocketFactory;

    @Activate
    public MiyoCubeHandlerFactory(@Reference HttpClientFactory httpClientFactory,
            @Reference WebSocketFactory webSocketFactory) {
        this.httpClientFactory = httpClientFactory;
        this.webSocketFactory = webSocketFactory;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_CUBE.equals(thingTypeUID)) {
            MiyoCubeHandler handler = new MiyoCubeHandler((Bridge) thing, httpClientFactory, webSocketFactory);
            registerService(handler, MiyoDiscoveryService.class);
            return handler;
        } else if (THING_TYPE_SENSOR.equals(thingTypeUID)) {
            return new MiyoSensorHandler(thing);
        } else if (THING_TYPE_VALVE.equals(thingTypeUID)) {
            return new MiyoValveHandler(thing);
        } else if (THING_TYPE_CIRCUIT.equals(thingTypeUID)) {
            return new MiyoCircuitHandler(thing);
        }

        return null;
    }
}
