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
import org.eclipse.californium.scandium.PskDtlsServer;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.ApplicationMessage;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;

public class DtlsConnectionSampler extends AbstractJavaSamplerClient {

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
	
	public DtlsConnectionSampler() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public Arguments getDefaultParameters() {
		Arguments result = new Arguments();
		result.addArgument(PARAM_LOCAL_PORT, String.valueOf(DEFAULT_LOCAL_PORT));
		result.addArgument(PARAM_CLIENT_IDENTITY, PskDtlsServer.CLIENT_IDENTITY);
		result.addArgument(PARAM_SHARED_KEY, PskDtlsServer.SECRET_KEY);
		result.addArgument(PARAM_SERVER_HOST, "localhost");
		result.addArgument(PARAM_SERVER_PORT, String.valueOf(PskDtlsServer.DEFAULT_PORT));
		result.addArgument(PARAM_SUCCESS_THRESHOLD_MS, String.valueOf(SUCCESS_THRESHOLD_MILLIS));
		
		return result;
	}
	
	@Override
	public void setupTest(JavaSamplerContext context) {
		int localPort = context.getIntParameter(PARAM_LOCAL_PORT, DEFAULT_LOCAL_PORT);
		String identity = context.getParameter(PARAM_CLIENT_IDENTITY, PskDtlsServer.CLIENT_IDENTITY);
		byte[] key = context.getParameter(PARAM_SHARED_KEY, PskDtlsServer.SECRET_KEY).getBytes();
		remoteHost = context.getParameter(PARAM_SERVER_HOST, "localhost");
		remotePort = context.getIntParameter(PARAM_SERVER_PORT, PskDtlsServer.DEFAULT_PORT);
		successThreshold = context.getIntParameter(PARAM_SUCCESS_THRESHOLD_MS, SUCCESS_THRESHOLD_MILLIS);
		name = context.getParameter(TestElement.NAME);
		
		DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder(new InetSocketAddress(localPort));
		builder.setPskStore(new StaticPskStore(identity, key));
		connector = new DTLSConnector(builder.build());
		message = new RawData(
				new ApplicationMessage(MESSAGE_TEXT.getBytes()).toByteArray(),
				new InetSocketAddress(remoteHost, remotePort));
		try {
			connector.start();
		} catch (IOException e) {
			// could not start connector
		}
	}
	
	public SampleResult runTest(JavaSamplerContext context) {
		SampleResult result = new SampleResult();
		result.setSampleLabel(name);
		result.setSamplerData(String.format("host: %s, port: %d, msg: %s", remoteHost, remotePort, MESSAGE_TEXT));
		result.sampleStart();
		establishDtlsConnection(message, successThreshold, result);
		result.sampleEnd();
		return result;
	}

	@Override
	public void teardownTest(JavaSamplerContext context) {
		if (connector != null) {
			connector.stop();
			connector = null;
		}
	}
	
	private class LatchBasedRawDataChannel implements RawDataChannel {
		
		private CountDownLatch latch;
		private String response;
		
		public LatchBasedRawDataChannel(CountDownLatch latch) {
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
	
	private void establishDtlsConnection(RawData message,
			int connectionEstablishmentThreshold, SampleResult sampleResult) {
		CountDownLatch latch = new CountDownLatch(1);
		LatchBasedRawDataChannel channel = new LatchBasedRawDataChannel(latch);
		connector.setRawDataReceiver(channel);
		boolean success = false;
		if (connector.isRunning()) {
			connector.send(message);
			try {
				success = latch.await(connectionEstablishmentThreshold, TimeUnit.MILLISECONDS);
				if (success) {
					sampleResult.setResponseMessage(channel.getResponse());
				}				
			} catch (InterruptedException e) {
				// will not happen
				success = true;
				sampleResult.setResponseMessage(e.getMessage());
			}
		}
		sampleResult.setSuccessful(success);
	}
}
