package com.cisco.commons.processing.distributed.etcd;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.cisco.commons.processing.DataObject;
import com.cisco.commons.processing.DataObjectProcessResultHandler;
import com.cisco.commons.processing.DataObjectProcessor;
import com.cisco.commons.processing.DataProcessor;
import com.cisco.commons.processing.retry.FailureHandler;
import com.google.gson.Gson;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Lock;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.lock.LockResponse;
import io.etcd.jetcd.lock.UnlockResponse;
import io.etcd.jetcd.options.GetOption;
import lombok.extern.slf4j.Slf4j;

/**
 * Data processor.
 * Processing data objects by multiple parallel consumers with ability to override pending objects
 * tasks for saving redundant tasks. <br/>
 * Highlight features:
 * <ul>
 * <li> Ability to override pending/running objects tasks for saving redundant tasks.
 * <li> Asynchronous retry mechanism for data objects processing tasks.
 * <li> Save potential memory by holding data objects instead of tasks class instances.
 * <li> No redundant live threads where there are no pending tasks.
 * </ul>
 * This is useful for example case where multiple notification received on same data object IDs in a 
 * time window where the previous data objects are still pending processing since the
 * internal thread pool is running other tasks up to the core pool size limit. The data processing
 * logic involves fetching the object from the DB and parsing the result. In this case, the
 * new notifications will override the same data objects entries, and each data object will be
 * fetched and processed with hopefully a single task instead of multiple times. <br/>
 * <br/>
 * DataProcessor.aggregate() vs threadPool.execute() - by the above example: <br/>
 * threadPool.execute:
 * <ul>
 * <li> 10 notifications arrive on data object with key 'x'.
 * <li> 10 similar tasks are created and executed via the thread pool for fetching and processing the
 * same object, 9 of them are redundant.
 * </ul>
 * DataProcessor.aggregate():
 * <ul>
 * <li> 10 notifications arrive on data object with key 'x'.
 * <li> 10 notifications are mapped to the same single queue map entry.
 * <li> 1 task is created and executed via the thread pool.
 * </ul>
 * <br/>
 * Theoretically, this solution can fit also for persistent messaging processing by replacing
 * the data map implementation with persistent map using one of the persistent key-value 
 * storage products. <br/>
 * <br/>
 * 
 * @author Liran Mendelovich
 * 
 * Copyright 2021 Cisco Systems
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@Slf4j
public class ETCDDataProcessor extends DataProcessor {

	private static final String QUEUE_MAP_PREFIX = "commons-processing-etcd.queue.map.";
	private static final String QUEUE_MAP_TIMESTAMP_PENDING_PREFIX = QUEUE_MAP_PREFIX + "timestamp.pending.";
	private static final String QUEUE_MAP_DATA_PREFIX = QUEUE_MAP_PREFIX + ".data.";
	private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
	private static final long MAX_DATA_OBJECT_PROCESSING_TIME_SECONDS = 60 * 5;
	private static final ByteSequence QUEUE_MAP_LOCK = ByteSequence.from(QUEUE_MAP_PREFIX, DEFAULT_CHARSET);
	
	private Gson gson;
	private Client client;
	private KV kvClient;
	private Lock lockClient;
	private Lease leaseClient;
	private ByteSequence queueMapTimestampePendingPrefixByteSeq;

	public static ETCDDataProcessorBuilder newBuilder() {
		return new ETCDDataProcessorBuilder();
	}

	public ETCDDataProcessor(Integer numOfThreads, Long retryDelay, TimeUnit retryDelayTimeUnit, int retries,
			DataObjectProcessor dataObjectProcessor, DataObjectProcessResultHandler dataObjectProcessResultHandler,
			FailureHandler failureHandler, boolean shouldAggregateIfAlreadyRunning, String etcdUrl, Client client) {
		super(numOfThreads, retryDelay, retryDelayTimeUnit, retries, dataObjectProcessor, dataObjectProcessResultHandler, failureHandler, shouldAggregateIfAlreadyRunning);
		gson = new Gson();
		this.client = client;
		if (client == null) {
			client = Client.builder().endpoints(etcdUrl).build();
		}
		kvClient = client.getKVClient();
		lockClient = client.getLockClient();
		leaseClient = client.getLeaseClient();
		queueMapTimestampePendingPrefixByteSeq = ByteSequence.from(QUEUE_MAP_TIMESTAMP_PENDING_PREFIX.getBytes());

	}

	@Override
	protected void addDataObject(DataObject dataObject) throws Exception {
		try {
			lockMap();

			String dataObjectMapKeyStr = QUEUE_MAP_DATA_PREFIX + dataObject.getKey().toString();

			byte[] dataObjectKeyBytes = dataObjectMapKeyStr.getBytes(DEFAULT_CHARSET);
			ByteSequence dataObjectKeyBytesSeq = ByteSequence.from(dataObjectKeyBytes);
			ByteSequence dataObjectDataBytesSeq = buildByteSeq(dataObject);


			PutResponse valuePutResponse = kvClient.put(dataObjectKeyBytesSeq, dataObjectDataBytesSeq)
					.get(30, TimeUnit.SECONDS);
			if (!valuePutResponse.hasPrevKv()) {

				// TODO expose method to user for faster performance
				String randomString = UUID.randomUUID().toString();

				String dataObjectMapTimestampKeyStr = QUEUE_MAP_TIMESTAMP_PENDING_PREFIX + 
						System.currentTimeMillis() + "." + randomString;

				byte[] dataObjectTimestampKeyBytes = dataObjectMapTimestampKeyStr.getBytes(DEFAULT_CHARSET);
				ByteSequence dataObjectTimestampKeyBytesSeq = ByteSequence.from(dataObjectTimestampKeyBytes);

				PutResponse timestampPutResponse = kvClient.put(dataObjectTimestampKeyBytesSeq, dataObjectKeyBytesSeq)
						.get(30, TimeUnit.SECONDS);
			}
		} finally {
			unlockMap();
		}
	}

	private ByteSequence buildByteSeq(DataObject dataObject) {
		byte[] dataObjectDataBytes = gson.toJson(dataObject.getData()).getBytes(DEFAULT_CHARSET);
		ByteSequence dataObjectDataBytesSeq = ByteSequence.from(dataObjectDataBytes);
		return dataObjectDataBytesSeq;
	}
	
	private void lockMap() throws InterruptedException, ExecutionException, TimeoutException {
		CompletableFuture<LeaseGrantResponse> leaseFuture = leaseClient.grant(MAX_DATA_OBJECT_PROCESSING_TIME_SECONDS);
		LeaseGrantResponse leaseResponse = leaseFuture.get(30, TimeUnit.SECONDS);
		long leaseId = leaseResponse.getID();
		CompletableFuture<LockResponse> lockFuture = lockClient.lock(QUEUE_MAP_LOCK, leaseId);
		LockResponse response = lockFuture.get(MAX_DATA_OBJECT_PROCESSING_TIME_SECONDS, TimeUnit.SECONDS);
	}
	
	private void unlockMap() throws Exception {
		// TODO 30 to constant
		UnlockResponse unlockResponse = lockClient.unlock(QUEUE_MAP_LOCK).get(30, TimeUnit.SECONDS);
	}

	@Override
	protected DataObject poll() throws Exception {
		try {
			lockMap();

			GetOption getOption = GetOption.newBuilder().withPrefix(queueMapTimestampePendingPrefixByteSeq)
					.withLimit(1).build();
			CompletableFuture<GetResponse> getFuture = kvClient.get(queueMapTimestampePendingPrefixByteSeq, getOption);
			GetResponse response = getFuture.get(30, TimeUnit.SECONDS);
			List<KeyValue> kvs = response.getKvs();
			if (!kvs.isEmpty()) {
				String value = kvs.iterator().next().getValue().toString(DEFAULT_CHARSET);
				return gson.fromJson(value, DataObject.class);
			}
			
			// TODO replace value from pending to inprogress
			
			return null;
		} finally {
			unlockMap();
		}
	}
	
	@Override
	protected void postProcess(DataObject dataObject) throws Exception {
		
		try {
			lockMap();
			
			// TODO change key from pending to inprogress

			ByteSequence dataObjectDataBytesSeq = buildByteSeq(dataObject);
			CompletableFuture<DeleteResponse> res = kvClient.delete(dataObjectDataBytesSeq);
			
		} finally {
			unlockMap();
		}
	}

	@Override
	public void shutdown() {
		try {
			super.shutdown();
		} catch (Exception e) {
			log.error("Error shutdown." + e.getMessage());
		}
	}
}
