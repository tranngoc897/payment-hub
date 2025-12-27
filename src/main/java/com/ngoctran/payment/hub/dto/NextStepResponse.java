package com.ngoctran.payment.hub.dto;

import java.util.Map;

public record NextStepResponse(
    String nextStep,
    Map<String, Object> uiModel,
    String status
) {}