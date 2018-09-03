/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.haw.dlock.api;

import org.apache.commons.lang.StringUtils;

/**
 * 分布式锁处理类.
 *
 * @author Fsz
 * @version 1.0.0
 * @since Aug 31 2017
 */
public class DlockHandlerImpl implements Dlock {

    /**
     * 锁的最大等待时间 10000 毫秒（10秒）.
     */
    private static final int LOCK_TIME_OUT = 10000;

    /**
     * 分布式锁heander.
     */
    private final DlockOp dlockOp;

    /**
     * 构造方法.
     *
     * @param dlockOp
     */
    public DlockHandlerImpl(final DlockOp dlockOp) {
        this.dlockOp = dlockOp;
    }

    /**
     * 获取锁.
     *
     * <p>
     * 支持锁可重入
     * </p>
     *
     * @param lockResource 锁对象.
     * @return 是否取得锁.
     */
    @Override
    public boolean lock(final String lockResource) {
        return this.lock(lockResource, LOCK_TIME_OUT);
    }

    /**
     * 获取锁.
     * <p>
     * 支持锁可重入
     * </p>
     *
     * @param lockResource 锁对象.
     * @param timeout 超时时间(单位毫秒).
     * @return 是否取得锁.
     */
    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public boolean lock(final String lockResource, long timeout) {

        if (StringUtils.isBlank(lockResource)) {
            return false;
        }
        return dlockOp.set(lockResource, timeout);
    }

    /**
     * 释放锁.
     *
     * @param lockResource 锁对象.
     */
    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public void unlock(final String lockResource) {
        try {
            if (StringUtils.isBlank(lockResource)) {
                return;
            }
            dlockOp.del(lockResource);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
}
