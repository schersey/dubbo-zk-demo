package com.imooc.curator.utils;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Created by yanchangxian on 2018/12/13.
 */
public class DistributedLock {
    private CuratorFramework client = null;

    final static Logger log = LoggerFactory.getLogger(DistributedLock.class);
    //用户挂起当前请求，并且等待上一个锁释放
    private static CountDownLatch countDownLatch = new CountDownLatch(1);

    //分布式锁的总节点名
    private static final String ZK_LOCK_PRIJECT = "smart-lock";
    //分布式锁节点
    private static final String DISTRIBUTED_LOCK = "distributed_lock";

    //构造函数
    public DistributedLock(CuratorFramework client) {
        this.client = client;
    }

    /**
     * 初始化锁
     */
    public void init() {
        client = client.usingNamespace("SmartLocks-Namespace");
        /**
         * 创建zk锁的总节点，相当于eclipse的工作空间下的项目
         *      SmartLocks-Namespace
         *          |
         *           -- smart-lock
         *              |
         *              -- distributed_lock
         */
        try {
            if (client.checkExists().forPath("/" + ZK_LOCK_PRIJECT) == null) {
                client.create().creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT)
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        .forPath("/" + ZK_LOCK_PRIJECT);
            }
            //针对zk的分布式锁节点，创建相应的watcher监听事件
            addWatcherToLock("/" + ZK_LOCK_PRIJECT);
        } catch (Exception e) {
            log.error("客户端连接zookeeper服务器错误，请重试", e);
        }

    }

    /**
     * 获取分布式锁
     */
    public void getLock() {
        //使用死循环，而且仅当上一个锁释放并且当前请求获得锁成功后才会跳出
        while (true) {
            try {
                client.create().creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL) //临时节点
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        .forPath("/" + ZK_LOCK_PRIJECT + "/" + DISTRIBUTED_LOCK);
                log.info("获得分布式锁成功");
                return;
            } catch (Exception e) {
                log.error("获得分布式锁失败");
                try {
                    //如果没有获得锁，需要重新设置同步资源值
                    if (countDownLatch.getCount() <= 0) {
                        countDownLatch = new CountDownLatch(1);
                    }
                    //线程阻塞
                    countDownLatch.await();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }

    /**
     * 释放锁
     */
    public boolean releaseLock() {
        try {
            if (client.checkExists().forPath("/" + ZK_LOCK_PRIJECT + "/" + DISTRIBUTED_LOCK) != null) {
                client.delete().forPath("/" + ZK_LOCK_PRIJECT + "/" + DISTRIBUTED_LOCK);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        log.info("分布式锁释放完毕");
        return true;
    }

    /**
     * 针对zk的分布式锁节点，创建相应的watcher监听事件
     */
    public void addWatcherToLock(String path) throws Exception {
        final PathChildrenCache cache = new PathChildrenCache(client, path, true);
        /*
         * cachadata 设置缓存节点的数据状态
         * StartMode-初始化方式
         * BUILD_INITIAL_CACHE:同步初始化
         * POST_INITIALIZED_EVENT：异步初始化，初始化后会触发事件
         * NORMAL：异步初始化
         */
        cache.start(StartMode.POST_INITIALIZED_EVENT);
        cache.getListenable().addListener(new PathChildrenCacheListener() {
            @Override
            public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
                if (event.getType().equals(PathChildrenCacheEvent.Type.INITIALIZED)) {
                    log.info("子节点初始化OK");
                } else if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_ADDED)) {
                    String path = event.getData().getPath();
                    if (path.equals("/super/smart/e")) {
                        log.info("添加子节点：" + event.getData().getPath());
                        log.info("子节点数据：" + new String(event.getData().getData()));
                    } else {
                        log.info("add other");
                    }
                } else if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_REMOVED)) {
                    //这里主要写在这里
                    String path = event.getData().getPath();
                    log.info("上一个会话已经释放锁或该会话已经断开，节点路径为：" + path);
                    if (path.contains(DISTRIBUTED_LOCK)) {
                        log.info("释放计算器，让当前请求来获得分布式锁...");
                        countDownLatch.countDown();
                    }
                } else if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_UPDATED)) {
                    log.info("修改子节点：" + event.getData().getPath());
                    log.info("修改子节点数据：" + new String(event.getData().getData()));
                } else {
                    log.info("other...");
                }
            }
        });

    }


}
