/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.haw.dlock.impl.redis;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.haw.dlock.api.DlockOp;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * redis作为分布式锁的实现类.
 *
 * @author Fsz
 * @version 1.0.0
 * @since Aug 31 2017
 */
public class RedisDlockOpImpl implements DlockOp, InitializingBean {
    
    private JedisPool jedisPool;

    /**
     * 连接池属性配置--最大空闲数.
     */
    private int maxIdle;

    /**
     * 最大连接数.
     */
    private int maxTotal;

    /**
     * 最大等待时间-毫秒.
     */
    private int maxWait;

    /**
     * redis - host.
     */
    private String host;

    /**
     * redis - port.
     */
    private int port;

    /**
     * redis - 操作timeout.
     */
    private int timeout;

    /**
     * redis - 密码.
     */
    private String password;

    /**
     * redis - db.
     */
    private int db;

    /**
     * 资源自动释放时间单位秒-300秒主动释放锁.
     */
    private final int keyExpireTime = 300;

    /**
     * redis实现分布式锁--可重入锁
     *
     * @param lockResource 锁对象.
     * @param timeOut
     * @return
     */
    @Override
    @SuppressWarnings({"CallToPrintStackTrace", "ConfusingArrayVararg",
        "PrimitiveArrayArgumentToVariableArgMethod"})
    public boolean set(final String lockResource, final long timeOut) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            final String threadId = String.valueOf(Thread.currentThread().getId());
            final String value = jedis.get(lockResource);
            if (StringUtils.equals(value, threadId)) {
                return true;
            }
            
            long startTime = System.currentTimeMillis();
            long endTime = System.currentTimeMillis();
            long timeWait = timeOut;
            long totalWaitTime = 0;
            
            while (true) {
                startTime = System.currentTimeMillis();
                totalWaitTime += (endTime - startTime);
                timeWait = timeOut - totalWaitTime;
                
                if (jedis.setnx(lockResource, threadId) == 1) {
                    jedis.expire(lockResource, keyExpireTime);
                    return true;
                }

                //超时返回获取锁失败
                if (timeWait < 0) {
                    return false;
                }
                
                Thread.sleep(timeWait / 10);
                
                endTime = System.currentTimeMillis();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != jedis) {
                jedis.close();
            }
        }
        return false;
    }
    
    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public void del(final String lockResource) {
        Jedis jedis = null;
        try {
            final String threadId = String.valueOf(Thread.currentThread().getId());
            jedis = jedisPool.getResource();
            if (StringUtils.equals(jedis.get(lockResource), threadId)) {
                jedis.del(lockResource);
            }
            jedis.publish(lockResource, lockResource);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != jedis) {
                jedis.close();
            }
        }
    }
    
    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public void afterPropertiesSet() throws Exception {
        try {
            final JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxIdle(getMaxIdle() > 0 ? getMaxIdle() : 5);
            poolConfig.setMaxTotal(getMaxTotal() > 0 ? getMaxTotal() : 10);
            poolConfig.setMaxWaitMillis(getMaxWait() > 0 ? getMaxWait() : 500);
            poolConfig.setTestOnBorrow(true);
            this.jedisPool = new JedisPool(poolConfig, this.getHost(), getPort(),
                    getTimeout(), getPassword(), getDb());
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 连接池属性配置--最大空闲数.
     *
     * @return the maxIdle
     */
    public int getMaxIdle() {
        return maxIdle;
    }

    /**
     * 连接池属性配置--最大空闲数.
     *
     * @param maxIdle the maxIdle to set
     */
    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }

    /**
     * 最大连接数.
     *
     * @return the maxTotal
     */
    public int getMaxTotal() {
        return maxTotal;
    }

    /**
     * 最大连接数.
     *
     * @param maxTotal the maxTotal to set
     */
    public void setMaxTotal(int maxTotal) {
        this.maxTotal = maxTotal;
    }

    /**
     * 最大等待时间-毫秒.
     *
     * @return the maxWait
     */
    public int getMaxWait() {
        return maxWait;
    }

    /**
     * 最大等待时间-毫秒.
     *
     * @param maxWait the maxWait to set
     */
    public void setMaxWait(int maxWait) {
        this.maxWait = maxWait;
    }

    /**
     * redis - host.
     *
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * redis - host.
     *
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * redis - port.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * redis - port.
     *
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * redis - 操作timeout.
     *
     * @return the timeout
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * redis - 操作timeout.
     *
     * @param timeout the timeout to set
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /**
     * redis - db.
     *
     * @return the db
     */
    public int getDb() {
        return db;
    }

    /**
     * redis - db.
     *
     * @param db the db to set
     */
    public void setDb(int db) {
        this.db = db;
    }

    /**
     * redis - 密码.
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * redis - 密码.
     *
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }
}
