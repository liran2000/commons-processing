package com.cisco.commons.processing.distributed.etcd.memory;

import io.etcd.jetcd.Auth;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Cluster;
import io.etcd.jetcd.Election;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Lock;
import io.etcd.jetcd.Maintenance;
import io.etcd.jetcd.Watch;

public class MemoryClient implements Client {
	
	private KV kv;
	private Lock lock;
	private Lease lease;
	
	public MemoryClient() {
		kv = new MemoryKV();
		lock = new MemoryLock();
		lease = new MemoryLease();
	}

	@Override
	public Auth getAuthClient() {
		return null;
	}

	@Override
	public KV getKVClient() {
		return kv;
	}

	@Override
	public Cluster getClusterClient() {
		return null;
	}

	@Override
	public Maintenance getMaintenanceClient() {
		return null;
	}

	@Override
	public Lease getLeaseClient() {
		return lease;
	}

	@Override
	public Watch getWatchClient() {
		return null;
	}

	@Override
	public Lock getLockClient() {
		return lock;
	}

	@Override
	public Election getElectionClient() {
		return null;
	}

	@Override
	public void close() {
		
	}

}
