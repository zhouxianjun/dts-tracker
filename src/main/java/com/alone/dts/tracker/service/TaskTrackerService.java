package com.alone.dts.tracker.service;

import com.alone.dts.thrift.service.JobService;
import com.alone.dts.thrift.service.TaskProcessor;
import com.alone.dts.thrift.struct.ExecuteResult;
import com.alone.dts.thrift.struct.HostInfo;
import com.alone.dts.thrift.struct.InvalidOperation;
import com.alone.dts.thrift.struct.JobStruct;
import com.alone.dts.tracker.ActionHandler;
import com.gary.trc.ThriftServiceServerFactory;
import com.gary.trc.annotation.ThriftReference;
import com.gary.trc.annotation.ThriftService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.thrift.TException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@ThriftService
public class TaskTrackerService implements TaskProcessor.Iface, InitializingBean {
    @Resource
    @Setter
    private ApplicationContext applicationContext;
    @ThriftReference
    private JobService.Iface jobService;
    @Setter
    private ThriftServiceServerFactory factory;

    @Setter
    private boolean spring = true;
    @Setter
    private String nodeGroup;
    @Setter
    private int handlerThread = 10;

    private String attr;
    private Map<String, ActionHandler> handlerMap = new HashMap<>();
    private ExecutorService executorService;
    private HostInfo info;

    @Override
    public ExecuteResult execute(final JobStruct jobStruct) throws InvalidOperation, TException {
        log.debug("RECEIVE JOB {}", jobStruct.getTaskId());
        try {
            ActionHandler handler = null;
            if (spring) {
                handler = applicationContext.getBean(jobStruct.getAction(), ActionHandler.class);
            }
            if (handler == null && !handlerMap.containsKey(jobStruct.getAction())) {
                throw new Exception(String.format("job %s not found action %s", jobStruct.getTaskId(), jobStruct.getAction()));
            }
            handler = handler == null ? handlerMap.get(jobStruct.getAction()) : handler;
            final ActionHandler finalHandler = handler;
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        String msg;
                        boolean success;
                        long start = System.currentTimeMillis();
                        try {
                            msg = finalHandler.handler(jobStruct);
                            success = true;
                        } catch (Throwable e) {
                            log.error("job {} handler execute error", jobStruct.getTaskId(), e);
                            success = false;
                            msg = ExceptionUtils.getStackTrace(e);
                        }
                        jobService.complete(jobStruct.getTaskId(), msg, info, success);
                        log.info("COMPLETE JOB {} {} millis", jobStruct.getTaskId(), System.currentTimeMillis() - start);
                    } catch (Throwable e) {
                        log.error("Job {} Handler thread error", jobStruct.getTaskId(), e);
                    }
                }
            });
            return new ExecuteResult(info).setMsg("ok");
        } catch (Exception e) {
            log.error("job {} handler error", jobStruct.getTaskId(), e);
            throw new TException(e);
        }
    }

    public TaskTrackerService action(String action, ActionHandler handler) {
        handlerMap.put(action, handler);
        return this;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (factory == null) throw new Exception("ThriftServiceServerFactory must be not null");
        if (StringUtils.isNotEmpty(nodeGroup)) {
            this.attr = String.format("{\"nodeGroup\": \"%s\"}", nodeGroup);
        }
        executorService = Executors.newFixedThreadPool(handlerThread);
        info = new HostInfo(factory.getHost(), factory.getPort(), factory.getPid());
    }
}
