package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;

public class ReserveCalcContext {
    private static final Logger LOGGER = Logger.getLogger(ReserveCalcContext.class.getName());

    private final Map<String, BigDecimal> fieldValues = new LinkedHashMap<>();
    private final Map<String, Object> extendedValues = new LinkedHashMap<>(); // Add this

    public void put(String field, BigDecimal value) {
        fieldValues.put(field, value);
    }

    // New method to store extended values
    public void putExtended(String field, Object value) {
        extendedValues.put(field, value);
    }

    public Object getExtended(String field) {
        return extendedValues.get(field);
    }

    public BigDecimal get(String field) {
        return fieldValues.getOrDefault(field, BigDecimal.ZERO);
    }

    public void clear() {
        fieldValues.clear();
        extendedValues.clear(); // Clear extended values as well
    }

    public void calculateSteps(
            List<ReserveCalcStep> currentSteps,
            ReserveCalcStep contextConditionStep
    ) {
        // Calculate for each flow
        Map<CalculationFlow, BigDecimal> stepValues = new LinkedHashMap<>();
        Map<CalculationFlow, Map<String, BigDecimal>> flowStepValues = new LinkedHashMap<>();

        for (CalculationFlow flow : CalculationFlow.values()) {
            ReserveCalcContext flowContext = new ReserveCalcContext();

            // Find and calculate step for this flow
            for (ReserveCalcStep step : currentSteps) {
                step.calculateValue(flowContext);
            }

            // Store flow-specific calculation value
            stepValues.put(flow, flowContext.get(currentSteps.get(0).getFieldName()));

            // Store entire flow context values
            flowStepValues.put(flow, flowContext.getAll());
        }

        // Determine final value using context condition
        if (contextConditionStep != null) {
            ReserveCalcContext conditionContext = new ReserveCalcContext();

            // Prepare flow values for condition step
            conditionContext.putExtended("flowValues", stepValues);

            // Calculate final value
            contextConditionStep.calculateValue(conditionContext);

            // Update main context
            put(
                    currentSteps.get(0).getFieldName(),
                    conditionContext.get("selectedValue")
            );
        } else {
            // Default to OMS flow if no context condition
            put(
                    currentSteps.get(0).getFieldName(),
                    stepValues.get(CalculationFlow.OMS)
            );
        }

        // Store flow-specific values
        putExtended("flowStepValues", flowStepValues);
    }

    public Map<String, BigDecimal> getAll() {
        return new LinkedHashMap<>(fieldValues);
    }

    public Map<CalculationFlow, Map<String, BigDecimal>> getFlowValues() {
        Object flowStepValuesObj = getExtended("flowStepValues");

        if (flowStepValuesObj instanceof Map) {
            return (Map<CalculationFlow, Map<String, BigDecimal>>) flowStepValuesObj;
        }

        return new LinkedHashMap<>();
    }
}