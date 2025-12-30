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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link MiyoWebsocketClient} is a interface for handling websocket events
 *
 * @author Fabian Obernberger - Initial contribution
 */
@NonNullByDefault
public interface MiyoWebsocketEventHandler {
    /**
     * Called when a message is received from the WebSocket server.
     * 
     * @param message the received message
     */
    void onMessage(String message);

    /**
     * Called when an error occurs in the WebSocket connection.
     */
    void onError();

    /**
     * Called when the WebSocket connection is successfully opened.
     */
    void onOpened();
}
