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
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.types.State;

/**
 * The {@link MiyoThingHandler} is responsible for handling miyo things
 *
 * @author Fabian Obernberger - Initial contribution
 */
@NonNullByDefault
public abstract class MiyoThingHandler extends BaseThingHandler {

    public MiyoThingHandler(Thing thing) {
        super(thing);
    }

    /**
     * Handles updates from the bridge to the child thing.
     * 
     * @param channelUID the channel UID
     * @param state the new state
     */
    public abstract void handleBridgeUpdate(ChannelUID channelUID, State state);

    /**
     * Updates the status of the child thing based on the bridge's status.
     * 
     * @param bridgeStatus
     */
    public void updateChildStatus(ThingStatusInfo bridgeStatus) {
        if (bridgeStatus.getStatus() == ThingStatus.OFFLINE && getThing().getStatus() != ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatus.getStatus() == ThingStatus.ONLINE && getThing().getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        }
    }

    /**
     * Updates the status of the child thing if it differs from the current status.
     * 
     * @param thingStatus the new status to set
     * @param thingStatusDetail the new status detail to set
     */
    public void updateChildStatus(ThingStatus thingStatus, ThingStatusDetail thingStatusDetail) {
        if (getThing().getStatus() != thingStatus
                || getThing().getStatusInfo().getStatusDetail() != thingStatusDetail) {
            updateStatus(thingStatus, thingStatusDetail);
        }
    }

    /**
     * Updates the status of the child thing if it differs from the current status.
     * 
     * @param thingStatus the new status to set
     * @param thingStatusDetail the new status detail to set
     * @param description the new description to set
     */
    public void updateChildStatus(ThingStatus thingStatus, ThingStatusDetail thingStatusDetail, String description) {
        String currentDescription = getThing().getStatusInfo().getDescription();
        if (getThing().getStatus() != thingStatus || getThing().getStatusInfo().getStatusDetail() != thingStatusDetail
                || (currentDescription != null && !currentDescription.equals(description))) {
            updateStatus(thingStatus, thingStatusDetail, description);
        }
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        Bridge bridge = getBridge();
        if (bridge != null) {
            BridgeHandler bridgeHandler = bridge.getHandler();
            if (bridgeHandler instanceof MiyoCubeHandler) {
                ((MiyoCubeHandler) bridgeHandler).initializeDefaultStates();
            }
        }
    }
}
