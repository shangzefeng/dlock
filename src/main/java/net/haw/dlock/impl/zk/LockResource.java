/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.haw.dlock.impl.zk;

/**
 *
 * @author Fsz
 */
public class LockResource {

    /**
     * 锁key.
     */
    private String lockKey;

    /**
     * 生成锁的时间.
     */
    private long time;

    public LockResource(final String lockKey, final long time) {
        this.lockKey = lockKey;
        this.time = time;
    }

    /**
     * 锁key.
     *
     * @return the lockKey
     */
    public String getLockKey() {
        return lockKey;
    }

    /**
     * 锁key.
     *
     * @param lockKey the lockKey to set
     */
    public void setLockKey(final String lockKey) {
        this.lockKey = lockKey;
    }

    /**
     * 生成锁的时间.
     *
     * @return the time
     */
    public long getTime() {
        return time;
    }

    /**
     * 生成锁的时间.
     *
     * @param time the time to set
     */
    public void setTime(final long time) {
        this.time = time;
    }

}
