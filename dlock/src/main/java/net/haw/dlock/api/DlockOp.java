/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.haw.dlock.api;

/**
 * 分布式锁对象操作方法.
 *
 * @author Fsz
 * @version 1.0.0
 * @since Aug 31 2017
 */
public interface DlockOp {

    boolean set(final String lockResource,final long timeOut);

    void del(final String lockResource);
}
