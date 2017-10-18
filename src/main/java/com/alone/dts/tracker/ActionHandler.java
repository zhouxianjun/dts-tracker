package com.alone.dts.tracker;

import com.alone.dts.thrift.struct.JobStruct;

public interface ActionHandler {
    String handler(JobStruct struct);
}
