package com.imooc.curator.utils;

import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by yanchangxian on 2018/12/13.
 */
public class ZKCurator {
    private CuratorFramework client = null; //zk客户端

    final static Logger log = LoggerFactory.getLogger(ZKCurator.class);

    public ZKCurator(CuratorFramework client){
        this.client = client;
    }

    /**
     * 初始化操作
     */
    public void init(){
        client = client.usingNamespace("zk-curator-connector");
    }

    /**
     * 判断zk是否连接
     * @return
     */
    public boolean isZKAlive(){
        return client.isStarted();
    }
}
