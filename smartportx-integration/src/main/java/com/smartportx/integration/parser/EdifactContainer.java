package com.smartportx.integration.parser;

/** One container unit read from a COPRAR/COPARN message. */
public record EdifactContainer(String containerNumber, String isoType, String status,
                               String operator, String yardPosition, String customsStatus) {

    public EdifactContainer {
        containerNumber = containerNumber == null ? "" : containerNumber.trim();
        isoType = isoType == null ? "" : isoType.trim();
        status = status == null ? "" : status.trim();
        operator = operator == null ? "" : operator.trim();
        yardPosition = yardPosition == null ? "" : yardPosition.trim();
        customsStatus = customsStatus == null ? "" : customsStatus.trim();
    }
}
