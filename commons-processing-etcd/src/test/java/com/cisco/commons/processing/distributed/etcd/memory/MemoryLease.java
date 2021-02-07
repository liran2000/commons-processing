package com.cisco.commons.processing.distributed.etcd.memory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.etcd.jetcd.Lease;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.lease.LeaseRevokeResponse;
import io.etcd.jetcd.lease.LeaseTimeToLiveResponse;
import io.etcd.jetcd.options.LeaseOption;
import io.etcd.jetcd.support.CloseableClient;
import io.grpc.stub.StreamObserver;

public class MemoryLease implements Lease {

	@Override
	public CompletableFuture<LeaseGrantResponse> grant(long ttl) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<LeaseGrantResponse> grant(long ttl, long timeout, TimeUnit unit) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<LeaseRevokeResponse> revoke(long leaseId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<LeaseKeepAliveResponse> keepAliveOnce(long leaseId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<LeaseTimeToLiveResponse> timeToLive(long leaseId, LeaseOption leaseOption) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CloseableClient keepAlive(long leaseId, StreamObserver<LeaseKeepAliveResponse> observer) {
		// TODO Auto-generated method stub
		return null;
	}

}
