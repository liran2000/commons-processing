package com.cisco.commons.processing.distributed.etcd.memory;

import java.util.concurrent.CompletableFuture;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Lock;
import io.etcd.jetcd.lock.LockResponse;
import io.etcd.jetcd.lock.UnlockResponse;

public class MemoryLock implements Lock {

	@Override
	public CompletableFuture<LockResponse> lock(ByteSequence name, long leaseId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<UnlockResponse> unlock(ByteSequence lockKey) {
		// TODO Auto-generated method stub
		return null;
	}

}
