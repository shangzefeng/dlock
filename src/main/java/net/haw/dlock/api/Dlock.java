/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.haw.dlock.api;

/**
 * 分布式锁api.
 *
 * @author Fsz
 * @version 1.0.0
 * @since Aug 31 2017
 */
public interface Dlock {

    /**
     * 获取锁.
     *
     * @param lockResource 锁对象.
     * @return 是否取得锁.
     */
    public boolean lock(final String lockResource);

    /**
     * 超时获取锁.
     *
     * @param lockResource 锁对象.
     * @param timeout 超时时间(单位毫秒).
     * @return 是否取得锁.
     */
    public boolean lock(final String lockResource, final long timeout);

    /**
     * 释放锁.
     *
     * @param lockResource 锁对象.
     */
    public void unlock(final String lockResource);
}
