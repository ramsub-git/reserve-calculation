package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;

public class ReserveCalcContext {
    private static final Logger LOGGER = Logger.getLogger(ReserveCalcContext.class.getName());

    private final Map<String, BigDecimal> fieldValues = new LinkedHashMap<>();
    private final Map<String, Object> extendedValues = new LinkedHashMap<>();

    public void put(String field, BigDecimal value) {
        LOGGER.info("Putting value in context: " + field + " = " + value);
        fieldValues.put(field, value);
    }

    public BigDecimal get(String field) {
        BigDecimal value = fieldValues.getOrDefault(field, BigDecimal.ZERO);
        LOGGER.info("Getting value from context: " + field + " = " + value);
        return value;
    }

    public void putExtended(String field, Object value) {
        LOGGER.info("Putting extended value in context: " + field + " = " + value);
        extendedValues.put(field, value);
    }

    public Object getExtended(String field) {
        Object value = extendedValues.get(field);
        LOGGER.info("Getting extended value from context: " + field + " = " + value);
        return value;
    }

    public void calculateSteps(
            List<ReserveCalcStep> currentSteps,
            ReserveCalcStep contextConditionStep
    ) {
        LOGGER.info("Starting calculateSteps with " + currentSteps.size() + " steps");

        // Calculate for each flow
        Map<CalculationFlow, BigDecimal> stepValues = new LinkedHashMap<>();
        Map<CalculationFlow, Map<String, BigDecimal>> flowStepValues = new LinkedHashMap<>();

        for (CalculationFlow flow : CalculationFlow.values()) {
            LOGGER.info("Calculating steps for flow: " + flow);
            ReserveCalcContext flowContext = new ReserveCalcContext();

            // Find and calculate step for this flow
            for (ReserveCalcStep step : currentSteps) {
                LOGGER.info("Calculating step: " + step.getFieldName());
                step.calculateValue(flowContext);
            }

            // Store flow-specific calculation value
            BigDecimal flowStepValue = flowContext.get(currentSteps.get(0).getFieldName());
            LOGGER.info("Flow " + flow + " step value: " + flowStepValue);
            stepValues.put(flow, flowStepValue);

            // Store entire flow context values
            flowStepValues.put(flow, flowContext.getAll());
        }

        // Determine final value using context condition
        if (contextConditionStep != null) {
            LOGGER.info("Applying context condition step");
            ReserveCalcContext conditionContext = new ReserveCalcContext();

            // Prepare flow values for condition step
            conditionContext.putExtended("flowValues", stepValues);

            // Calculate final value
            contextConditionStep.calculateValue(conditionContext);

            // Update main context
            BigDecimal selectedValue = conditionContext.get("selectedValue");
            LOGGER.info("Selected value from context condition: " + selectedValue);
            put(
                    currentSteps.get(0).getFieldName(),
                    selectedValue
            );
        } else {
            // Default to OMS flow if no context condition
            BigDecimal omsValue = stepValues.get(CalculationFlow.OMS);
            LOGGER.info("No context condition, defaulting to OMS flow value: " + omsValue);
            put(
                    currentSteps.get(0).getFieldName(),
                    omsValue
            );
        }

        // Store flow-specific values
        putExtended("flowStepValues", flowStepValues);
        LOGGER.info("Calculation steps completed");
    }

    public Map<String, BigDecimal> getAll() {
        LOGGER.info("Retrieving all context values: " + fieldValues);
        return new LinkedHashMap<>(fieldValues);
    }

    public Map<CalculationFlow, Map<String, BigDecimal>> getFlowValues() {
        Object flowStepValuesObj = getExtended("flowStepValues");

        if (flowStepValuesObj instanceof Map) {
            Map<CalculationFlow, Map<String, BigDecimal>> flowValues =
                    (Map<CalculationFlow, Map<String, BigDecimal>>) flowStepValuesObj;

            LOGGER.info("Retrieving flow values: " + flowValues);
            return flowValues;
        }

        LOGGER.info("No flow values found, returning empty map");
        return new LinkedHashMap<>();
    }
}