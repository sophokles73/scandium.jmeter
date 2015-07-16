package org.eclipse.californium.scandium.jmeter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.RawDataChannel;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
//import org.eclipse.californium.scandium.config.DtlsConnectorConfig.Profile;
import org.eclipse.californium.scandium.dtls.ApplicationMessage;
import org.eclipse.californium.scandium.dtls.InMemoryConnectionStore;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;

public class DtlsConnectionSampler extends AbstractJavaSamplerClient {

	private static final int DEFAULT_PORT = 5684;
	private static final String SHARED_KEY = "secretPSK";
	private static final String CLIENT_IDENTITY = "Client_identity";
	private static final String MESSAGE_TEXT = "Hello there!";
	private static final int DEFAULT_LOCAL_PORT = 5050;
	private static final String PARAM_LOCAL_PORT = "local port";
	private static final String PARAM_CLIENT_IDENTITY = "client identity";
	private static final String PARAM_SHARED_KEY = "shared key";
	private static final String PARAM_SERVER_HOST = "server host";
	private static final String PARAM_SERVER_PORT = "server port";
	private static final String PARAM_SUCCESS_THRESHOLD_MS = "success threshold (ms)";
	private static final int SUCCESS_THRESHOLD_MILLIS = 600;
	private DTLSConnector connector;
	private RawData message;
	private int successThreshold = SUCCESS_THRESHOLD_MILLIS;
	private String name;
	private String remoteHost;
	private int remotePort;
	private LatchBasedRawDataChannel clientMessageHandler;
	private InetSocketAddress serverAddress;
	
	public DtlsConnectionSampler() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public Arguments getDefaultParameters() {
		Arguments result = new Arguments();
		result.addArgument(PARAM_LOCAL_PORT, String.valueOf(DEFAULT_LOCAL_PORT));
		result.addArgument(PARAM_CLIENT_IDENTITY, CLIENT_IDENTITY);
		result.addArgument(PARAM_SHARED_KEY, SHARED_KEY);
		result.addArgument(PARAM_SERVER_HOST, "localhost");
		result.addArgument(PARAM_SERVER_PORT, String.valueOf(DEFAULT_PORT));
		result.addArgument(PARAM_SUCCESS_THRESHOLD_MS, String.valueOf(SUCCESS_THRESHOLD_MILLIS));
		
		return result;
	}
	
	@Override
	public void setupTest(JavaSamplerContext context) {
		int localPort = context.getIntParameter(PARAM_LOCAL_PORT, DEFAULT_LOCAL_PORT);
		String identity = context.getParameter(PARAM_CLIENT_IDENTITY, CLIENT_IDENTITY);
		byte[] key = context.getParameter(PARAM_SHARED_KEY, SHARED_KEY).getBytes();
		remoteHost = context.getParameter(PARAM_SERVER_HOST, "localhost");
		remotePort = context.getIntParameter(PARAM_SERVER_PORT, DEFAULT_PORT);
		successThreshold = context.getIntParameter(PARAM_SUCCESS_THRESHOLD_MS, SUCCESS_THRESHOLD_MILLIS);
		name = context.getParameter(TestElement.NAME);
		
		serverAddress = new InetSocketAddress(remoteHost, remotePort);
		InetSocketAddress localAddress = new InetSocketAddress(localPort);
		getLogger().info("Starting a DTLSConnector at [" + localAddress + "]");
		
		DtlsConnectorConfig config = new DtlsConnectorConfig.Builder(localAddress)
			.setPskStore(new StaticPskStore(identity, key))
//			.setProfile(Profile.CLIENT)
			.build();
		connector = new DTLSConnector(config, new InMemoryConnectionStore(2, 300));
		clientMessageHandler = new LatchBasedRawDataChannel(new CountDownLatch(1));
		connector.setRawDataReceiver(clientMessageHandler);
		message = new RawData(
				new ApplicationMessage(MESSAGE_TEXT.getBytes(), serverAddress).toByteArray(),
				serverAddress);
		try {
			connector.start();
			SampleResult result = new SampleResult();
			sendMessage(message, 4000, result);
			if (result.isSuccessful()) {
				getLogger().info(
						String.format("Established DTLS connection from [%s] to [%s]",
								localAddress, serverAddress));
			} else {
				connector.stop();
				getLogger().info(
						String.format("Could not establish DTLS connection from [%s] to [%s]: %s",
								localAddress, serverAddress, result.getResponseMessage()));
			}
		} catch (IOException e) {
			getLogger().error("Could not start DTLSConnector", e);
		}
	}
	
	public SampleResult runTest(JavaSamplerContext context) {
		SampleResult result = new SampleResult();
		result.setSampleLabel(name);
		result.setSamplerData(String.format("host: %s, port: %d, msg: %s", remoteHost, remotePort, MESSAGE_TEXT));
		result.sampleStart();
		sendMessage(message, successThreshold, result);
		result.sampleEnd();
		return result;
	}

	@Override
	public void teardownTest(JavaSamplerContext context) {
		if (connector != null) {
			connector.close(serverAddress);
			connector.stop();
			connector = null;
		}
	}
	
	private class LatchBasedRawDataChannel implements RawDataChannel {
		
		private CountDownLatch latch;
		private String response;
		
		public LatchBasedRawDataChannel(CountDownLatch latch) {
			setLatch(latch);
		}
		
		public void setLatch(CountDownLatch latch) {
			this.latch = latch;
		}
		
		public void receiveData(RawData raw) {
			response = new String(raw.getBytes());
			latch.countDown();
		}
		
		public String getResponse() {
			return response;
		}
	}
	
	private void sendMessage(RawData message,
			int connectionEstablishmentThreshold, SampleResult sampleResult) {
		CountDownLatch latch = new CountDownLatch(1);
		clientMessageHandler.setLatch(latch);
		boolean success = false;
		if (connector.isRunning()) {
			connector.send(message);
			try {
				success = latch.await(connectionEstablishmentThreshold, TimeUnit.MILLISECONDS);
				if (success) {
					sampleResult.setResponseMessage(clientMessageHandler.getResponse());
				} else {
					sampleResult.setResponseMessage(
							String.format("DTLS roundtrip timed out at [%dms]", connectionEstablishmentThreshold));
				}
			} catch (InterruptedException e) {
				success = false;
				sampleResult.setResponseMessage(e.getMessage());
				Thread.currentThread().interrupt();
			}
		}
		sampleResult.setSuccessful(success);
	}

}
