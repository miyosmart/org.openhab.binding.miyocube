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
package org.openhab.binding.miyocube.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link MiyoCubeBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Fabian Obernberger - Initial contribution
 */
@NonNullByDefault
public class MiyoCubeBindingConstants {

    private static final String BINDING_ID = "miyocube";

    /**
     * The thing type UID for the Miyo Cube
     */
    public static final ThingTypeUID THING_TYPE_CUBE = new ThingTypeUID(BINDING_ID, "cube");

    /**
     * The thing type UID for the Miyo Circuit
     */
    public static final ThingTypeUID THING_TYPE_CIRCUIT = new ThingTypeUID(BINDING_ID, "circuit");

    /**
     * The thing type UID for the Miyo Valve
     */
    public static final ThingTypeUID THING_TYPE_VALVE = new ThingTypeUID(BINDING_ID, "valve");

    /**
     * The thing type UID for the Miyo Sensor
     */
    public static final ThingTypeUID THING_TYPE_SENSOR = new ThingTypeUID(BINDING_ID, "sensor");
}
