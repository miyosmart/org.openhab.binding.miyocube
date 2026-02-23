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
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;

/**
 * The {@link MiyoValveHandler} is responsible for handling valve things
 *
 * @author Fabian Obernberger - Initial contribution
 */
@NonNullByDefault
public class MiyoValveHandler extends MiyoThingHandler {

    public MiyoValveHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void handleBridgeUpdate(ChannelUID channelUID, State state) {
        if (channelUID.getId().equals("valve-status")) {
            if (this.getThing().getUID().getId().endsWith("_1")) {
                updateState(channelUID, state);
            }
        } else if (channelUID.getId().equals("valve2-status")) {
            if (this.getThing().getUID().getId().endsWith("_2")) {
                ChannelUID valveChannelUID = new ChannelUID(this.getThing().getUID(), "valve-status");
                updateState(valveChannelUID, state);
            }
        } else {
            updateState(channelUID, state);
        }
    }

    @Override
    public void updateChildStatus(ThingStatusInfo bridgeStatus) {
        if (bridgeStatus.getStatus() == ThingStatus.OFFLINE && getThing().getStatus() != ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }
}
