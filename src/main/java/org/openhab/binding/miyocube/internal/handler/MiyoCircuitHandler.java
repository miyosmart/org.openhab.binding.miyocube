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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MiyoCircuitHandler} is responsible for handling the circuit things
 *
 * @author Fabian Obernberger - Initial contribution
 */
@NonNullByDefault
public class MiyoCircuitHandler extends MiyoThingHandler {

    private @Nullable Integer currentDuration;
    private final Logger logger = LoggerFactory.getLogger(MiyoCircuitHandler.class);

    public MiyoCircuitHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        try {
            updateState(new ChannelUID(getThing().getUID(), "duration"), UnDefType.NULL);
        } catch (Exception e) {
            logger.debug("Failed to set initial duration state: {}", e.getMessage());
        }
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Handling command for channel {}: {} class {}", channelUID.getId(), command.toString(),
                command.getClass());

        if (channelUID.getId().equals("duration")) {
            try {
                currentDuration = ((QuantityType<?>) command).intValue();
                updateState(channelUID, ((QuantityType<?>) command));
            } catch (Exception e) {
                logger.debug("Failed to handle duration command: {}", e.getMessage());
            }
            return;
        }

        // check channelUID with irrigationStart
        if (command instanceof OnOffType) {
            // turn off
            State offState = OnOffType.OFF;
            this.handleBridgeUpdate(channelUID, offState);

            Bridge bridge = this.getBridge();
            if (bridge != null) {
                BaseBridgeHandler bridgeHandler = (BaseBridgeHandler) bridge.getHandler();
                if (bridgeHandler != null) {
                    bridgeHandler.handleCommand(channelUID, command);
                }
            }
        }
    }

    @Override
    public void handleBridgeUpdate(ChannelUID channelUID, State state) {
        updateState(channelUID, state);
    }

    @Override
    public void updateChildStatus(ThingStatus thingStatus, ThingStatusDetail thingStatusDetail) {
        // do nothing, circuit status is only dependent on bridge status
    }

    @Override
    public void updateChildStatus(ThingStatus thingStatus, ThingStatusDetail thingStatusDetail, String description) {
        // do nothing, circuit status is only dependent on bridge status
    }

    /**
     * Gets the currently set duration for irrigation.
     * 
     * @return the current duration in minutes, or null if not set
     */
    public @Nullable Integer getCurrentDuration() {
        return this.currentDuration;
    }
}
