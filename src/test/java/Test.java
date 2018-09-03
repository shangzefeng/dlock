
import net.haw.dlock.api.DlockHandlerImpl;
import net.haw.dlock.impl.redis.RedisDlockOpImpl;
import net.haw.dlock.impl.zk.ZkDlockOpImpl;
import org.junit.Before;
import org.springframework.beans.factory.xml.XmlBeanFactory;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Fsz
 */
public class Test {

    public static void main(String[] args) throws Exception {
        zkLock();
    }

    public static void zkLock() {
        ZkDlockOpImpl zkDlockOpImpl = new ZkDlockOpImpl();
        zkDlockOpImpl.setHost("127.0.0.1");
        zkDlockOpImpl.setPort(2181);
        zkDlockOpImpl.setTimeout(5000);
        zkDlockOpImpl.afterPropertiesSet();
        DlockHandlerImpl dlockHandlerImpl = new DlockHandlerImpl(zkDlockOpImpl);
        if (dlockHandlerImpl.lock("abc", 1000000)) {
            dlockHandlerImpl.unlock("abc");
            System.out.println("100");
        }
    }

    public static void redisLock() throws Exception {
        RedisDlockOpImpl redisDlockOpImpl = new RedisDlockOpImpl();
        redisDlockOpImpl.setDb(0);
        redisDlockOpImpl.setHost("192.168.8.106");
        redisDlockOpImpl.setMaxIdle(10);
        redisDlockOpImpl.setMaxTotal(100);
        redisDlockOpImpl.setMaxWait(5000);
        redisDlockOpImpl.setPassword("123456");
        redisDlockOpImpl.setPort(6379);
        redisDlockOpImpl.setTimeout(2000);
        redisDlockOpImpl.afterPropertiesSet();
        DlockHandlerImpl dlockHandlerImpl = new DlockHandlerImpl(redisDlockOpImpl);

        if (dlockHandlerImpl.lock("abc", 1000)) {
            System.out.println("100");
        }
    }
}
