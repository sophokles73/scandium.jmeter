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
package org.eclipse.californium.scandium.server;

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PreDestroy;

import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.RawDataChannel;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.ConnectionStore;
import org.eclipse.californium.scandium.dtls.InMemoryConnectionStore;
import org.eclipse.californium.scandium.dtls.pskstore.InMemoryPskStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class PskDtlsServer implements CommandLineRunner {

	private static final Logger LOGGER = Logger.getLogger(PskDtlsServer.class.getName());
	private DTLSConnector dtlsConnector;

	@Value(value="${scandium.client.identity}")
	private String clientIdentity;

	@Value(value="${scandium.client.secret}")
	private String clientSecret;

	@Value(value="${scandium.dtls.port}")
	private int port;

	public PskDtlsServer() {
	}

	@PreDestroy
	public void destroy() throws Exception {
		dtlsConnector.destroy();
	}

	private class RawDataChannelImpl implements RawDataChannel {

		private Connector connector;

		public RawDataChannelImpl(Connector con) {
			this.connector = con;
		}

		// @Override
		public void receiveData(final RawData request) {
			if (request.getAddress() != null &&  request.getPort() > 0) {
				connector.send(new RawData(request.getBytes(), request.getAddress(), request.getPort()));
			} else {
				LOGGER.info("Received message without sender address, discarding...");
			}
		}
	}

	public void run(String... arg0) throws Exception {

		InMemoryPskStore pskStore = new InMemoryPskStore();
		LOGGER.log(Level.INFO, "Clients can authenticate to this server using PSK with identity [{0}]", clientIdentity);
		pskStore.setKey(clientIdentity, clientSecret.getBytes());

		DtlsConnectorConfig config = new DtlsConnectorConfig.Builder(new InetSocketAddress(port))
			.setPskStore(pskStore)
			.setMaxFragmentLengthCode(3)
			.build();
		ConnectionStore connectionStore = new InMemoryConnectionStore(1000, 60);
		dtlsConnector = new DTLSConnector(config, connectionStore);
		dtlsConnector.setRawDataReceiver(new RawDataChannelImpl(dtlsConnector));

		dtlsConnector.start();
	}
}
