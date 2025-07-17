package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;

/**
 * ReserveCalcContext holds calculation state across all flows.
 * - Field values
 * - Step snapshots
 * - Dynamic step values
 * - Running total history
 */
public class ReserveCalcContext {

    private InitialValueWrapper initialValueWrapper;

    // Holds final calculated values per field name
    private final Map<String, BigDecimal> fieldValues = new LinkedHashMap<>();

    // Per step row index, per flow snapshots
    private final Map<Integer, Map<CalculationFlow, ReserveCalcStep>> stepSnapshots = new HashMap<>();

    // Dynamic step values (latest)
    private final Map<String, BigDecimal> dynamicValues = new HashMap<>();

    // Running total history per field
    private final Map<String, List<BigDecimal>> runningTotalHistory = new HashMap<>();

    private List<ReserveCalcStep> dynamicSteps = new ArrayList<>();

    public void setInitialValueWrapper(InitialValueWrapper wrapper) {
        this.initialValueWrapper = wrapper;
    }

    public InitialValueWrapper getInitialValueWrapper() {
        return initialValueWrapper;
    }

    public void setDynamicSteps(List<ReserveCalcStep> dynamicSteps) {
        this.dynamicSteps = dynamicSteps;
    }

    public List<ReserveCalcStep> getDynamicSteps() {
        return dynamicSteps;
    }

    public void put(String field, BigDecimal value) {
        fieldValues.put(field, value);
        runningTotalHistory.computeIfAbsent(field, k -> new ArrayList<>()).add(value);
    }

    public BigDecimal get(String field) {
        return fieldValues.getOrDefault(field, BigDecimal.ZERO);
    }

    public Map<String, BigDecimal> getAll() {
        return Collections.unmodifiableMap(fieldValues);
    }

//    public void calculateSteps(int stepIndex, Map<CalculationFlow, ReserveCalcStep> currentSteps, ReserveCalcStep contextConditionStep) {
//        Map<CalculationFlow, ReserveCalcStep> snapshotForThisStep = new EnumMap<>(CalculationFlow.class);
//
//        for (Map.Entry<CalculationFlow, ReserveCalcStep> entry : currentSteps.entrySet()) {
//            CalculationFlow flow = entry.getKey();
//            ReserveCalcStep step = entry.getValue();
//
//            try {
//                BigDecimal value = step.calculateValue(this);
//                put(step.getFieldName(), value);
//                snapshotForThisStep.put(flow, step.copy());
//            } catch (Exception e) {
//                System.err.println("Error executing step '" + step.getFieldName() + "' for flow " + flow + ": " + e.getMessage());
//                put(step.getFieldName(), BigDecimal.ZERO);
//            }
//        }
//
//        if (contextConditionStep != null) {
//            try {
//                BigDecimal finalValue = contextConditionStep.calculateValue(this);
//                put(contextConditionStep.getFieldName(), finalValue);
//            } catch (Exception e) {
//                System.err.println("Error executing context condition for field '" + contextConditionStep.getFieldName() + "': " + e.getMessage());
//            }
//        }
//
//        // Dynamic step recalculation and snapshot
//        for (ReserveCalcStep dynamicStep : dynamicSteps) {
//            try {
//                BigDecimal dynamicValue = dynamicStep.calculateValue(this);
//                dynamicValues.put(dynamicStep.getFieldName(), dynamicValue);
//                put(dynamicStep.getFieldName(), dynamicValue);
//            } catch (Exception e) {
//                System.err.println("Error recalculating dynamic step '" + dynamicStep.getFieldName() + "': " + e.getMessage());
//            }
//        }
//
//        stepSnapshots.put(stepIndex, snapshotForThisStep);
//    }


    // Step calculation loop controlled by engine
    public void calculateSteps(int stepIndex, Map<CalculationFlow, ReserveCalcStep> currentSteps, ReserveCalcStep contextConditionStep) {
        Map<CalculationFlow, ReserveCalcStep> snapshotForThisStep = new EnumMap<>(CalculationFlow.class);

        for (Map.Entry<CalculationFlow, ReserveCalcStep> entry : currentSteps.entrySet()) {
            CalculationFlow flow = entry.getKey();
            ReserveCalcStep step = entry.getValue();

            try {
                BigDecimal value = step.calculateValue(this);
                put(step.getFieldName(), value);
                snapshotForThisStep.put(flow, step.copy());
            } catch (Exception e) {
                System.err.println("Error executing step '" + step.getFieldName() + "' for flow " + flow + ": " + e.getMessage());
                put(step.getFieldName(), BigDecimal.ZERO);
            }
        }

        if (contextConditionStep != null) {
            try {
                BigDecimal finalValue = contextConditionStep.calculateValue(this);
                put(contextConditionStep.getFieldName(), finalValue);
            } catch (Exception e) {
                System.err.println("Error executing context condition for field '" + contextConditionStep.getFieldName() + "': " + e.getMessage());
            }
        }

        for (ReserveCalcStep dynamicStep : dynamicSteps) {
            try {
                BigDecimal dynamicValue = dynamicStep.calculateValue(this);
                dynamicValues.put(dynamicStep.getFieldName(), dynamicValue);
                put(dynamicStep.getFieldName(), dynamicValue);
            } catch (Exception e) {
                System.err.println("Error recalculating dynamic step '" + dynamicStep.getFieldName() + "': " + e.getMessage());
            }
        }

        stepSnapshots.put(stepIndex, snapshotForThisStep);
    }


    public Map<Integer, Map<CalculationFlow, ReserveCalcStep>> getStepSnapshots() {
        return stepSnapshots;
    }

    public Map<String, List<BigDecimal>> getRunningTotalHistory() {
        return runningTotalHistory;
    }

    public Map<String, BigDecimal> getDynamicValues() {
        return dynamicValues;
    }
}
