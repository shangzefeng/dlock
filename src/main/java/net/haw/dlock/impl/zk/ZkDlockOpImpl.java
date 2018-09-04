/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.haw.dlock.impl.zk;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.haw.dlock.api.DlockOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.zookeeper.CreateMode;
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
public class ZkDlockOpImpl implements DlockOp, InitializingBean, Closeable {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ZkDlockOpImpl.class);

    /**
     * 锁记录.
     */
    private final ConcurrentHashMap<String, LockResource> LOCK_MAP = new ConcurrentHashMap();

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
     * 锁占用最大时长ms-- 默认10s.
     */
    private static final int MAX_LOCKED_TIME = 10000;

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
            if (!StringUtils.isBlank((getLockPath(lockId)))) {
                final Stat stat = new Stat();
                final byte[] data = zk.getData(getLockPath(lockId), false, stat);
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

            long startTime = System.currentTimeMillis();
            long endTime = startTime;
            long timeWait = timeOut > MAX_LOCKED_TIME ? MAX_LOCKED_TIME : timeOut;

            final LockResource resource = new LockResource(key, startTime);
            LOCK_MAP.put(lockId, resource);

            final long currenSeq = Long.parseLong(key.split("_")[1]);

            String preLockKey = null;
            synchronized (key) {
                while (true) {
                    timeWait = timeOut - (endTime - startTime);
                    flag = StringUtils.isBlank(preLockKey = tryLock(currenSeq, key, preLockKey));
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
                if (!flag && !StringUtils.isBlank(key)) {
                    this.del(lockResource);
                }
            } catch (final Exception e) {
                LOGGER.error("unlock error", e);
            }
        }
    }

    /**
     * 尝试是否能获得锁
     *
     * @param currenSeqv 当前锁序列.
     * @param currentLockKey 当前锁.
     * @param preKey 上级锁.
     * @return 返回null 说明可以获得锁，非null 需要等待此锁释放
     * @throws Exception
     */
    private String tryLock(final Long currenSeqv,
            final String currentLockKey, final String preKey) throws Exception {
        final List<String> keys = zk.getChildren(lockRootPath + "/" + lockPath, false);
        if (keys.size() == 1) {
            return null;
        }
        final TreeMap<Long, String> map = new TreeMap<>();
        for (String key1 : keys) {
            final long l = Long.parseLong(key1.split("_")[1]);
            map.put(l, key1);
        }

        final Map<Long, String> mm = map.descendingMap();
        final int size = mm.keySet().size();

        int index = 0;
        for (Map.Entry<Long, String> entry : mm.entrySet()) {
            Long key = entry.getKey();
            String value = entry.getValue();
            //当前的锁为最小锁时--获得锁
            if (key <= currenSeqv && index == size - 1) {
                return null;
            }

            //获取比当前锁序列小的锁
            if (key < currenSeqv) {
                if (!StringUtils.equals(value, preKey)) {
                    zk.exists(lockRootPath + "/" + lockPath + "/" + value,
                            new WatcherImpl(zk, currentLockKey));
                }
                return value;
            }
            index--;
        }
        return null;
    }

    /**
     * 释放锁.
     *
     * @param lockResource 锁资源.
     */
    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public void del(final String lockResource) {
        if (StringUtils.isBlank(lockResource)) {
            return;
        }
        final String lockId = String.valueOf(Thread.currentThread().getId()) + lockResource;
        try {
            final Stat stat = zk.exists(getLockPath(lockId), false);
            if (null == stat) {
                return;
            }
            zk.delete(getLockPath(lockId), stat.getVersion());
        } catch (final Exception e) {
            LOGGER.error("释放锁异常[" + lockResource + "]", e);
        } finally {
            LOCK_MAP.remove(lockId);
        }
    }

    @Override
    public void close() throws IOException {
        if (null == zk) {
            return;
        }
        try {
            LOCK_MAP.clear();
            zk.close();
            zk = null;
        } catch (final InterruptedException ex) {
            LOGGER.error("release resourece exception", ex);
        }
    }

    /**
     * 定时清理僵尸锁.
     */
    class ClearLock implements Runnable {

        @Override
        public void run() {
            final long expireTime = MAX_LOCKED_TIME * 1000;
            while (true) {
                try {
                    final long time = System.currentTimeMillis();
                    for (Map.Entry<String, LockResource> entry : LOCK_MAP.entrySet()) {
                        final LockResource value = entry.getValue();
                        if (null == value || time - value.getTime() < expireTime) {
                            continue;
                        }
                        final Stat stat = zk.exists(entry.getKey(), false);
                        if (null == stat) {
                            LOCK_MAP.remove(entry.getKey());
                            continue;
                        }
                        zk.delete(value.getLockKey(), stat.getVersion());
                        LOCK_MAP.remove(entry.getKey());
                    }
                    Thread.sleep(1000);
                } catch (final Exception ex) {
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
     * 通过lockId 获取lockPath
     *
     * @param lockId
     * @return
     * @throws Exception
     */
    private String getLockPath(final String lockId) throws Exception {
        if (StringUtils.isBlank(lockId)) {
            throw new Exception("lockId is null");
        }

        if (null == LOCK_MAP.get(lockId)) {
            throw new Exception("not found lockId [" + lockId + "]");
        }

        if (StringUtils.isBlank(LOCK_MAP.get(lockId).getLockKey())) {
            throw new Exception("lockId [" + lockId + "] not found lockPath");
        }

        return LOCK_MAP.get(lockId).getLockKey();
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
