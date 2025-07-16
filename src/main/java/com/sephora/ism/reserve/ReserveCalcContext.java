// ReserveCalcContext.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;

public class ReserveCalcContext {

    private final Map<String, BigDecimal> fieldValues = new LinkedHashMap<>();
    private final Map<String, List<BigDecimal>> runningHistory = new HashMap<>();
    private final Map<Integer, Map<CalculationFlow, ReserveCalcStep>> stepSnapshots = new LinkedHashMap<>();
    private List<ReserveCalcStep> dynamicSteps = new ArrayList<>();

    public void put(String field, BigDecimal value) {
        fieldValues.put(field, value);
        runningHistory.computeIfAbsent(field, k -> new ArrayList<>()).add(value);
    }

    public void setDynamicSteps(List<ReserveCalcStep> steps) {
        this.dynamicSteps = new ArrayList<>(steps);
    }


    public BigDecimal get(String field) {
        return fieldValues.getOrDefault(field, BigDecimal.ZERO);
    }

    public void calculateSteps(int stepIndex, Map<CalculationFlow, ReserveCalcStep> currentSteps, ReserveCalcStep contextConditionStep, Map<CalculationFlow, ReserveCalcContext> flowContexts) {
        Map<CalculationFlow, BigDecimal> flowResults = new EnumMap<>(CalculationFlow.class);
        Map<CalculationFlow, ReserveCalcStep> snapshotForThisStep = new EnumMap<>(CalculationFlow.class);

        for (Map.Entry<CalculationFlow, ReserveCalcStep> entry : currentSteps.entrySet()) {
            CalculationFlow flow = entry.getKey();
            ReserveCalcStep step = entry.getValue();
            ReserveCalcContext flowContext = flowContexts.get(flow);

            try {
                BigDecimal value = step.calculateValue(flowContext);
                flowContext.put(step.getFieldName(), value);
                flowResults.put(flow, value);
                snapshotForThisStep.put(flow, step.copy());


                BigDecimal finalValue = BigDecimal.ZERO;
                if (contextConditionStep != null) {
                    finalValue = contextConditionStep.calculateValue(this);
                    this.put(contextConditionStep.getFieldName(), finalValue);
                } else finalValue = value;

                if (finalValue.compareTo(BigDecimal.ZERO) == 0 && this.get("onHand").compareTo(BigDecimal.ZERO) > 0) {
                    System.out.println("Warning: Final reserve computed as 0 – inventory fully consumed or logic issue?");
                }

                stepSnapshots.put(stepIndex, snapshotForThisStep);


                // Dynamic Step Recalculation and Snapshot
                if (dynamicSteps != null) {
                    for (ReserveCalcStep dynamicStep : dynamicSteps) {
                        try {
                            BigDecimal dynamicValue = dynamicStep.calculateValue(this);
                            this.put(dynamicStep.getFieldName(), dynamicValue);

                            stepSnapshots.computeIfAbsent(stepIndex, k -> new EnumMap<>(CalculationFlow.class)).put(CalculationFlow.OMS, dynamicStep.copy());
                        } catch (Exception e) {
                            System.err.println("Error recalculating dynamic step '" + dynamicStep.getFieldName() + "': " + e.getMessage());
                        }
                    }
                }

            } catch (Exception e) {
                System.err.println("Error executing step '" + step.getFieldName() + "' for flow " + flow + ": " + e.getMessage());
                flowContext.put(step.getFieldName(), BigDecimal.ZERO);
            }
        }


    }


    public Map<String, BigDecimal> getAll() {
        return Collections.unmodifiableMap(fieldValues);
    }

    public Map<String, List<BigDecimal>> getRunningHistory() {
        return Collections.unmodifiableMap(runningHistory);
    }

    public Map<Integer, Map<CalculationFlow, ReserveCalcStep>> getStepSnapshots() {
        return Collections.unmodifiableMap(stepSnapshots);
    }

    public Map<String, BigDecimal> getStateAtStep(int stepIndex, CalculationFlow flow) {
        if (stepSnapshots.containsKey(stepIndex) && stepSnapshots.get(stepIndex).containsKey(flow)) {
            ReserveCalcStep step = stepSnapshots.get(stepIndex).get(flow);
            Map<String, BigDecimal> state = new HashMap<>();
            state.put(step.getFieldName() + ".originalValue", step.getOriginalValue());
            state.put(step.getFieldName() + ".previousValue", step.getPreviousValue());
            state.put(step.getFieldName() + ".currentValue", step.getCurrentValue());
            return state;
        }
        return Collections.emptyMap();
    }
}
