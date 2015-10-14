package org.eclipse.californium.scandium.jmeter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.RawDataChannel;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.InMemoryConnectionStore;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;
import org.eclipse.californium.scandium.util.DatagramWriter;

public class DtlsConnectionSampler extends AbstractJavaSamplerClient {

	private static final String DEFAULT_SERVER_HOST = "localhost";
	private static final int DEFAULT_SERVER_PORT = 5684;
	private static final String SHARED_KEY = "secretPSK";
	private static final String CLIENT_IDENTITY = "Client_identity";
	private static final int DEFAULT_LOCAL_PORT = 0; // use ephemeral port by default
	private static final String PARAM_LOCAL_PORT = "local port";
	private static final String PARAM_CLIENT_IDENTITY = "client identity";
	private static final String PARAM_SHARED_KEY = "shared key";
	private static final String PARAM_SERVER_HOST = "server host";
	private static final String PARAM_SERVER_PORT = "server port";
	private static final String PARAM_SUCCESS_THRESHOLD_MS = "success threshold (ms)";
	private static final String PARAM_REQUEST_NO = "requestNo";
	private static final int SUCCESS_THRESHOLD_MILLIS = 600;
	private DTLSConnector connector;
	private int successThreshold = SUCCESS_THRESHOLD_MILLIS;
	private String remoteHost;
	private int remotePort;
	private LatchBasedRawDataChannel clientMessageHandler;
	private InetSocketAddress serverAddress;
	
	public DtlsConnectionSampler() {
	}

	@Override
	public Arguments getDefaultParameters() {
		Arguments result = new Arguments();
		result.addArgument(PARAM_LOCAL_PORT, String.valueOf(DEFAULT_LOCAL_PORT));
		result.addArgument(PARAM_CLIENT_IDENTITY, CLIENT_IDENTITY);
		result.addArgument(PARAM_SHARED_KEY, SHARED_KEY);
		result.addArgument(PARAM_SERVER_HOST, DEFAULT_SERVER_HOST);
		result.addArgument(PARAM_SERVER_PORT, String.valueOf(DEFAULT_SERVER_PORT));
		result.addArgument(PARAM_SUCCESS_THRESHOLD_MS, String.valueOf(SUCCESS_THRESHOLD_MILLIS));
		result.addArgument(PARAM_REQUEST_NO, "${requestCounter}");

		return result;
	}

	@Override
	public void setupTest(JavaSamplerContext context) {
		int localPort = context.getIntParameter(PARAM_LOCAL_PORT, DEFAULT_LOCAL_PORT);
		String identity = context.getParameter(PARAM_CLIENT_IDENTITY, CLIENT_IDENTITY);
		byte[] key = context.getParameter(PARAM_SHARED_KEY, SHARED_KEY).getBytes();
		remoteHost = context.getParameter(PARAM_SERVER_HOST, DEFAULT_SERVER_HOST);
		remotePort = context.getIntParameter(PARAM_SERVER_PORT, DEFAULT_SERVER_PORT);
		successThreshold = context.getIntParameter(PARAM_SUCCESS_THRESHOLD_MS, SUCCESS_THRESHOLD_MILLIS);

		serverAddress = new InetSocketAddress(remoteHost, remotePort);
		InetSocketAddress localAddress = new InetSocketAddress(localPort);
		getLogger().info("Starting a DTLSConnector at [" + localAddress + "]");

		DtlsConnectorConfig config = new DtlsConnectorConfig.Builder(localAddress)
			.setPskStore(new StaticPskStore(identity, key))
			.setMaxFragmentLengthCode(1)
			.build();
		connector = new DTLSConnector(config, new InMemoryConnectionStore(2, 300));
		clientMessageHandler = new LatchBasedRawDataChannel(new CountDownLatch(1));
		connector.setRawDataReceiver(clientMessageHandler);
		try {
			connector.start();
			SampleResult result = new SampleResult();
			sendMessage(new byte[]{(byte) 0x01}, 4000, result); // send single byte as initial message
			if (result.isSuccessful()) {
				getLogger().info(
						String.format("Successfully established DTLS connection from [%s] to [%s]",
								connector.getAddress(), serverAddress));
			} else {
				connector.stop();
				getLogger().info(
						String.format("Could not establish DTLS connection from [%s] to [%s]: %s",
								connector.getAddress(), serverAddress, result.getResponseMessage()));
			}
		} catch (IOException e) {
			getLogger().error("Could not start DTLSConnector", e);
		}
	}

	public SampleResult runTest(JavaSamplerContext context) {
		int requestNo = context.getIntParameter(PARAM_REQUEST_NO);
		DatagramWriter writer = new DatagramWriter();
		writer.write(requestNo, 32);
		SampleResult result = new SampleResult();
		result.setSampleLabel(String.format("Snd/Rcv %d", requestNo));
		result.setSamplerData(String.format("host: %s, port: %d, msg: %d", remoteHost, remotePort, requestNo));
		result.sampleStart();
		sendMessage(writer.toByteArray(), successThreshold, result);
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
		private byte[] response;

		public LatchBasedRawDataChannel(CountDownLatch latch) {
			setLatch(latch);
		}

		public void setLatch(CountDownLatch latch) {
			this.latch = latch;
		}

		public void receiveData(RawData raw) {
			response = raw.getBytes();
			latch.countDown();
		}

		public byte[] getResponse() {
			return response;
		}
	}

	private void sendMessage(byte[] payload,
			int connectionEstablishmentThreshold, SampleResult sampleResult) {
		CountDownLatch latch = new CountDownLatch(1);
		clientMessageHandler.setLatch(latch);
		boolean success = false;
		if (connector.isRunning()) {
			RawData rawData = new RawData(payload, serverAddress);
			connector.send(rawData);
			try {
				if (latch.await(connectionEstablishmentThreshold, TimeUnit.MILLISECONDS)) {
					if (Arrays.equals(payload, clientMessageHandler.getResponse())) {
						sampleResult.setResponseMessageOK();
						success = true;
					} else {
						sampleResult.setResponseMessage("Error");
						sampleResult.setResponseData(clientMessageHandler.getResponse());
					}
				} else {
					sampleResult.setResponseMessage(
							String.format("Timeout after %d ms", connectionEstablishmentThreshold));
				}
			} catch (InterruptedException e) {
				sampleResult.setResponseMessage(e.getMessage());
				Thread.currentThread().interrupt();
			}
		}
		sampleResult.setSuccessful(success);
	}
}
