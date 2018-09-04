# dlock 基于redis和zookeeper实现分布式锁

## redis 实现原理
   由于redis是单线程模型，利用redis的setnx命令抢先占用锁并设置锁的超时时间，若没有取得锁就轮训执行setnx命令重试获得锁。轮询频率以timeOut/10 。
   总轮训时间不超过timeOut时间
   
## zookeeper 实现原理
   基于zookeeper实现的是公平锁并临时序列节点做为锁，并以watcher机制通过释放锁的节点。由于zookeeper没有节点超时，唯恐有僵尸节点不释放，
   故设一个清理僵尸节点线程。
   1. 创建临时序列节点
   2. 判断创建的节点是否有前置节点，无则获得锁，有则进行一下步
   3. exist前置节点并wait(timeout)等待4或超时唤醒(累计timeOut时间，超时获取锁失败并释放创建的临时节点)
   4. watcher notify 前置节点是否释放锁。继续2环节。
   为了避免未获得锁的节点提前超时释放节点，导致后面的节点获得锁所以再进行2环节，待当前临时节点前置无节点时才能获得锁）
   5. 内部线程清理僵尸临时节点
   
### zookeeper 实现简化流程图
![ABC](https://github.com/shangzefeng/dlock/blob/master/image/zookpeer.jpg) 
   
### 使用方法
    见测试
