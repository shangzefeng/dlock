/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.haw.dlock.impl.zk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.haw.dlock.api.DlockOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.InitializingBean;

/**
 * zk 作为分布式锁的实现类.
 *
 * @author Fsz
 * @version 1.0.0
 * @since Aug 31 2017
 */
public class ZkDlockOpImpl implements DlockOp, InitializingBean {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ZkDlockOpImpl.class);

    /**
     * 锁记录.
     */
    private final ConcurrentHashMap<String, String> LOCK_MAP = new ConcurrentHashMap();

    /**
     * zk.
     */
    private ZooKeeper zk;

    /**
     * lock.
     */
    private final String lockRootPath = "/lock";

    /**
     * zk .
     */
    private final String lockPath = "temp";

    /**
     * zk - host.
     */
    private String host;

    /**
     * zk - port.
     */
    private int port;

    /**
     * zk - 操作timeout.
     */
    private int timeout;

    /**
     * zk实现分布式锁--可重入锁
     *
     * @param lockResource 锁对象.
     * @param timeOut
     * @return
     */
    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public boolean set(final String lockResource, final long timeOut) {

        boolean flag = false;
        String key = null;
        try {
            final String threadId = String.valueOf(Thread.currentThread().getId());
            final String lockId = threadId + lockResource;

            //判断此锁在当前线程是否已被占用
            if (!StringUtils.isBlank((LOCK_MAP.get(threadId + lockResource)))) {
                final Stat stat = new Stat();
                final byte[] data = zk.getData(LOCK_MAP.get(lockId), false, stat);
                if (StringUtils.equals(new String(data, "UTF-8"), threadId)) {
                    flag = true;
                    return flag;
                }
                //若当前线程有此锁ID,但不是此线程拥有则返回false
                //LOCK_MAP.remove(lockId);  此除不删除了，由拥有此锁的线程删除
                return flag;
            }

            key = zk.create(lockRootPath + "/" + lockPath + "/" + lockResource + "_",
                    threadId.getBytes(),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL_SEQUENTIAL);

            LOCK_MAP.put(lockId, key);

            final long currenSeq = Long.parseLong(key.split("_")[1]);

            long startTime = System.currentTimeMillis();
            long endTime = System.currentTimeMillis();
            long timeWait = timeOut;
            long totalWaitTime = 0;

            synchronized (key) {
                while (true) {
                    startTime = System.currentTimeMillis();
                    totalWaitTime += (endTime - startTime);
                    timeWait = timeOut - totalWaitTime;

                    flag = this.tryLock(currenSeq, key);
                    if (!flag) {

                        if (timeWait < 0) {
                            return flag;
                        }
                        key.wait(timeWait);
                        endTime = System.currentTimeMillis();
                        continue;
                    }
                    return flag;
                }
            }
        } catch (final Exception e) {
            LOGGER.error("lock error", e);
            return false;
        } finally {
            try {
                if (!flag && StringUtils.isBlank(key)) {
                    zk.delete(key, 1);
                }
            } catch (final Exception e) {
                LOGGER.error("unlock error", e);
            }
        }
    }

    private boolean tryLock(final Long currenSeqv, final String key) throws KeeperException, InterruptedException {
        final List<String> keys = zk.getChildren(lockRootPath + "/" + lockPath, false);
        if (keys.size() == 1) {
            return true;
        }
        List<Long> seq = new ArrayList<>();
        for (String key1 : keys) {
            final long l = Long.parseLong(key1.split("_")[1]);
            seq.add(l);
        }
        Collections.sort(seq);

        for (int i = seq.size() - 1; i >= 0; i--) {
            //当前的锁为最小锁时--获得锁
            if (seq.get(i) <= currenSeqv && i == 0) {
                return true;
            }
            //或取列表中最小锁
            if (seq.get(i) < currenSeqv) {
                zk.exists(lockRootPath + "/" + lockPath + "/abc_000000000" + seq.get(i),
                        new WatcherImpl(zk, key));
                return false;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public void del(final String lockResource) {
        if (StringUtils.isBlank(lockResource)) {
            return;
        }
        final String lockId = String.valueOf(Thread.currentThread().getId()) + lockResource;
        try {
            zk.delete(lockId, 1);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            LOCK_MAP.remove(lockId);
        }
    }

    /**
     * 定时清理僵尸锁.
     */
    class ClearLock implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                }
            }
        }

    }

    @Override
    public void afterPropertiesSet() throws RuntimeException {
        try {
            zk = new ZooKeeper(host, 5000, new Watcher() {
                public void process(WatchedEvent we) {
                }
            });
            Stat stat = zk.exists(lockRootPath, false);
            if (null == stat) {
                zk.create(lockRootPath, "lock".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
            stat = zk.exists(lockRootPath + "/" + lockPath, false);
            if (null == stat) {
                zk.create(lockRootPath + "/" + lockPath, lockPath.getBytes(), Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);
            }
            (new Thread(new ClearLock())).start();
        } catch (final Exception e) {
            throw new RuntimeException("init lock impl error", e);
        }
    }

    /**
     * zk - host.
     *
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * zk - host.
     *
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * zk - port.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * zk - port.
     *
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * zk - 操作timeout.
     *
     * @return the timeout
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * zk - 操作timeout.
     *
     * @param timeout the timeout to set
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
