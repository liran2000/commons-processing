package com.cisco.commons.processing.distributed.etcd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.cisco.commons.processing.DataObject;
import com.cisco.commons.processing.DataObjectProcessResultHandler;
import com.cisco.commons.processing.DataObjectProcessor;
import com.cisco.commons.processing.retry.FailureHandler;

import io.etcd.jetcd.launcher.EtcdCluster;
import io.etcd.jetcd.test.EtcdClusterExtension;
import lombok.extern.slf4j.Slf4j;

@RunWith(Parameterized.class)
@Slf4j
public class ETCDDataProcessorTest {

	@RegisterExtension static final EtcdCluster etcd = new EtcdClusterExtension("test-etcd", 1);
	
	@Parameterized.Parameters
	public static Collection<Integer> numOfThreadsCollection() {
		return Arrays.asList(new Integer[] { 1, 4 });
	}
	
	private Integer numOfThreads;
	
	public ETCDDataProcessorTest(int numOfThreads) {
		this.numOfThreads = numOfThreads;
	}
	
	@Test
	public void eTCDDataProcessorTest() throws Exception {
		log.info("eTCDDataProcessorTest running with numOfThreads: {}", numOfThreads);
		String etcdUrl = etcd.getClientEndpoints().get(0).toString();
		log.info("etcdUrl: {}", etcdUrl);
		int retries = 1;
		Long retryDelay = 1L;
		TimeUnit retryDelayTimeUnit = TimeUnit.SECONDS;
		String testKey = "a1";
		AtomicInteger testKeyProcessedCount = new AtomicInteger(0);
		AtomicInteger processedDataObjectsCount = new AtomicInteger(0);
		DataObjectProcessor dataObjectProcessor = new DataObjectProcessor() {
			
			@Override
			public boolean process(DataObject dataObject) {
				sleepQuitely(100);
				log.info("processed dataObject: {}", dataObject.getKey());
				processedDataObjectsCount.incrementAndGet();
				if (testKey.equals(dataObject.getKey())) {
					testKeyProcessedCount.incrementAndGet();
				}
				return true;
			}
		};
		FailureHandler failureHandler = new FailureHandler() {
			
			@Override
			public void handleFailure(Supplier<Boolean> supplier) {
				log.info("handleFailure.");
			}
		};
		DataObjectProcessResultHandler resultHandler = new DataObjectProcessResultHandler() {
			@Override
			public void handleResult(Object dataObject, Boolean result) {
				log.info("handleResult.");
			}
		};
		ETCDDataProcessor dataProcessor = ETCDDataProcessor.newBuilder().dataObjectProcessor(dataObjectProcessor)
				.dataObjectProcessResultHandler(resultHandler).failureHandler(failureHandler).numOfThreads(numOfThreads)
				.retries(retries).retryDelay(retryDelay).retryDelayTimeUnit(retryDelayTimeUnit).etcdUrl(etcdUrl ).build();
		dataProcessor.aggregate(testKey, "dataObject_a1_value1");
		dataProcessor.aggregate(testKey, "dataObject_a1_value2");
		int count = numOfThreads * 3;
		log.info("count: {} ", count);
		for (int i = 0; i < count; i++) {
			dataProcessor.aggregate(String.valueOf(i), "dataObject" + i);
		}
		for (int i = 0; i < count; i++) {
			dataProcessor.aggregate(String.valueOf(i), "dataObject" + i);
		}
		for (int i = 0; i < count; i++) {
			dataProcessor.aggregate(String.valueOf(i), "dataObject" + i);
		}
		Thread.sleep(2000);
		log.info("Processed {} data objects.", processedDataObjectsCount.get());
		assertTrue(processedDataObjectsCount.get() >= count + 1 && processedDataObjectsCount.get() <= count*2 - 1);
		assertEquals(1, testKeyProcessedCount.get());
	}
	
	private void sleepQuitely(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			log.error("Error sleeping.");
		}
	}
}
