package com.chengliuxiang.xiaochengshu.search.biz.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Objects;

@Component
@Slf4j
public class CanalClient implements DisposableBean {

    @Resource
    private CanalProperties canalProperties;

    private CanalConnector canalConnector;

    /**
     * 实例化 Canal 链接对象
     *
     * @return
     */
    @Bean
    public CanalConnector getCanalConnector() {
        String address = canalProperties.getAddress();
        String[] addressArr = address.split(":");
        String host = addressArr[0];
        int port = Integer.parseInt(addressArr[1]);
        canalConnector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(host, port),
                canalProperties.getDestination(),
                canalProperties.getUsername(),
                canalProperties.getPassword());
        canalConnector.connect(); // 连接到 Canal 服务端
        canalConnector.subscribe(canalProperties.getSubscribe());
        canalConnector.rollback();
        return canalConnector;
    }

    @Override
    public void destroy() throws Exception {
        if(Objects.nonNull(canalConnector)){
            // 断开 canalConnector 与 Canal 服务的连接
            canalConnector.disconnect();
        }
    }
}
