package com.alone.dts.actions;

import com.alone.dts.thrift.struct.JobStruct;
import com.alone.dts.tracker.ActionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component("say")
@Slf4j
public class SayAction implements ActionHandler {
    @Override
    public String handler(JobStruct struct) {
        log.info("handler job {}", struct.getTaskId());
        return "ok";
    }
}
