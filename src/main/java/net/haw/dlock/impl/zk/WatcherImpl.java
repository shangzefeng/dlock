/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.haw.dlock.impl.zk;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

/**
 *
 * @author Fsz
 */
public class WatcherImpl implements Watcher {

    private ZooKeeper zooKeeper;

    private String lockId;

    public WatcherImpl(final ZooKeeper zooKeeper, final String lockId) {
        this.zooKeeper = zooKeeper;
        this.lockId = lockId;
    }

    @Override
    public void process(WatchedEvent event) {
        synchronized (lockId) {
            System.out.println("notify");
            lockId.notify();
        }
    }

}
