/*******************************************************************************
 * Copyright (c) 2014 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Stefan Jucker - DTLS implementation
 ******************************************************************************/
package org.eclipse.californium.scandium;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.RawDataChannel;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.InMemorySessionStore;
import org.eclipse.californium.scandium.dtls.SessionStore;
import org.eclipse.californium.scandium.dtls.pskstore.InMemoryPskStore;



public class PskDtlsServer {

	public static final int DEFAULT_PORT = 5684; 
	public static final String CLIENT_IDENTITY = "Client_identity";
	public static final String SECRET_KEY = "secretPSK";
	private DTLSConnector dtlsConnector;

	public PskDtlsServer() {
		InMemoryPskStore pskStore = new InMemoryPskStore();
		// put in the PSK store the default identity/psk for tinydtls tests
		pskStore.setKey(CLIENT_IDENTITY, SECRET_KEY.getBytes());

		DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder(new InetSocketAddress(DEFAULT_PORT));
		builder.setPskStore(pskStore);
		SessionStore sessionStore = new InMemorySessionStore(1000, 3);
		dtlsConnector = new DTLSConnector(builder.build(), sessionStore);
		dtlsConnector.setRawDataReceiver(new RawDataChannelImpl(dtlsConnector));
	}

	public void start() {
		try {
			dtlsConnector.start();
		} catch (IOException e) {
			throw new IllegalStateException("Unexpected error starting the DTLS UDP server",e);
		}
	}

	public void stop() {
		dtlsConnector.stop();
	}
	
	private class RawDataChannelImpl implements RawDataChannel {

		private Connector connector;

		public RawDataChannelImpl(Connector con) {
			this.connector = con;
		}

		// @Override
		public void receiveData(final RawData raw) {
			if (raw.getAddress() == null)
				throw new NullPointerException();
			if (raw.getPort() == 0)
				throw new NullPointerException();

			connector.send(new RawData("ACK".getBytes(), raw.getAddress(), raw.getPort()));
		}
	}

	public static void main(String[] args) {

		PskDtlsServer server = new PskDtlsServer();
		server.start();
		while (true) {
			// wait until the process is killed
		}
	}
}
