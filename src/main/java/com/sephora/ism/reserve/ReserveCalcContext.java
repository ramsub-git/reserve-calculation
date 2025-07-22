package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ReserveCalcContext holds calculation state across all flows.
 * - Field values
 * - Step snapshots
 * - Dynamic step values
 * - Running total history
 * <p>
 * ReserveCalcContext holds calculation state across all flows.
 * Simplified and cleaned up version.
 */
public class ReserveCalcContext {

    public static final Logger logger = LoggerFactory.getLogger(ReserveCalcContext.class);

    private InitialValueWrapper initialValueWrapper;

    // Core storage: Field values per flow
    private final Map<CalculationFlow, Map<ReserveField, ReserveCalcStep>> fieldValues = new LinkedHashMap<>();
    private final Map<ReserveField, Map<CalculationFlow, ReserveCalcStep>> resultSets = new LinkedHashMap<>();
    // Step snapshots for debugging
    private final Map<Integer, Map<CalculationFlow, ReserveCalcStep>> stepSnapshots = new HashMap<>();

    // Dynamic values and history - kept simple for now (OMS flow only)
    private final Map<ReserveField, BigDecimal> dynamicValues = new HashMap<>();
    private final Map<ReserveField, List<BigDecimal>> runningTotalHistory = new HashMap<>();

    // Dynamic steps that need recalculation
    private List<ReserveCalcStep> dynamicSteps = new ArrayList<>();

    // Running calculation steps per flow
    private final Map<CalculationFlow, List<Steps.RunningCalculationStep>> runningSteps = new HashMap<>();

    public ReserveCalcContext() {
        // Initialize maps for each flow
        for (CalculationFlow flow : CalculationFlow.values()) {
            fieldValues.put(flow, new EnumMap<>(ReserveField.class));
            runningSteps.put(flow, new ArrayList<>());
        }
    }

    // === Initial Value Wrapper Methods ===

    public void setInitialValueWrapper(InitialValueWrapper wrapper) {
        this.initialValueWrapper = wrapper;

        // Initialize all flows with initial values
        if (wrapper != null) {
            for (CalculationFlow flow : CalculationFlow.values()) {
                Map<ReserveField, ReserveCalcStep> flowMap = fieldValues.get(flow);

                // Create initial steps for each field from wrapper
                for (Map.Entry<ReserveField, BigDecimal> entry : wrapper.getValues().entrySet()) {
                    // Check if this field already has a step (from engine setup)
                    ReserveCalcStep existingStep = flowMap.get(entry.getKey());

                    if (existingStep != null) {
                        // If step exists, just update its value
                        existingStep.updateTracking(entry.getValue());
                    } else {
                        // Create a new step only if one doesn't exist
                        Steps.SkulocFieldStep step = new Steps.SkulocFieldStep(entry.getKey());
                        step.setFlow(flow);
                        step.updateTracking(entry.getValue());
                        flowMap.put(entry.getKey(), step);
                    }
                }
            }
        }
    }

    public InitialValueWrapper getInitialValueWrapper() {
        return initialValueWrapper;
    }

    // === Dynamic Steps Management ===

    public void setDynamicSteps(List<ReserveCalcStep> dynamicSteps) {
        this.dynamicSteps = dynamicSteps;
    }

    public List<ReserveCalcStep> getDynamicSteps() {
        return dynamicSteps;
    }

    // === Running Steps Management ===

    public void registerRunningStep(CalculationFlow flow, Steps.RunningCalculationStep runningStep) {
        runningSteps.get(flow).add(runningStep);
        // Also add to fieldValues so getStep() can find it
        runningStep.setFlow(flow);
        putStep(flow, runningStep.getFieldName(), runningStep);
    }

    // === Core Value Access Methods ===

    public ReserveCalcStep getStep(CalculationFlow flow, ReserveField field) {
        Map<ReserveField, ReserveCalcStep> flowMap = fieldValues.get(flow);
        return (flowMap != null) ? flowMap.get(field) : null;
    }

    public BigDecimal getCurrentValue(CalculationFlow flow, ReserveField field) {
//        logger.info("    getCurrentValue(" + flow + ", " + field + ")");

        ReserveCalcStep step = getStep(flow, field);
        if (step == null) {
            logger.info("      No step found! {} {} Returning 0", flow, field);
            // new Exception().printStackTrace();
            return BigDecimal.ZERO;
        }

        BigDecimal value = step.getCurrentValue();
//        logger.info("      Step found, currentValue = " + value);
        return value != null ? value : BigDecimal.ZERO;
    }

    public BigDecimal getPreviousValue(CalculationFlow flow, ReserveField field) {
        ReserveCalcStep step = getStep(flow, field);
        return (step != null) ? step.getPreviousValue() : BigDecimal.ZERO;
    }

    public BigDecimal getOriginalValue(CalculationFlow flow, ReserveField field) {
        ReserveCalcStep step = getStep(flow, field);
        return (step != null) ? step.getOriginalValue() : BigDecimal.ZERO;
    }

    // === Backward Compatibility Methods ===

    // Simple get method - defaults to OMS flow
    public BigDecimal get(ReserveField field) {
        return getCurrentValue(CalculationFlow.OMS, field);
    }

    // Get all values for backward compatibility - returns OMS flow as BigDecimal map
    public Map<ReserveField, BigDecimal> getAll() {
        Map<ReserveField, BigDecimal> result = new HashMap<>();
        Map<ReserveField, ReserveCalcStep> omsFlow = fieldValues.get(CalculationFlow.OMS);
        if (omsFlow != null) {
            for (Map.Entry<ReserveField, ReserveCalcStep> entry : omsFlow.entrySet()) {
                result.put(entry.getKey(), entry.getValue().getCurrentValue());
            }
        }
        return result;
    }

    // Get all steps for a specific flow
    public Map<ReserveField, ReserveCalcStep> getAll(CalculationFlow flow) {
        Map<ReserveField, ReserveCalcStep> flowMap = fieldValues.get(flow);
        return (flowMap != null) ? Collections.unmodifiableMap(flowMap) : Collections.emptyMap();
    }

    // === Update Methods ===

    public void updateStepValue(CalculationFlow flow, ReserveField field, BigDecimal newValue) {
        // logger.info("    updateStepValue(" + flow + ", " + field + ", " + newValue + ")");

        ReserveCalcStep step = getStep(flow, field);
        if (step != null) {
            // Just verify the value matches
            if (!step.getCurrentValue().equals(newValue)) {
                logger.info("      WARNING: Step current value " + step.getCurrentValue() + " doesn't match new value " + newValue);
            }

            // Track history for OMS flow
            if (flow == CalculationFlow.OMS) {
                runningTotalHistory.computeIfAbsent(field, k -> new ArrayList<>()).add(newValue);
            }
        } else {
            logger.error("      WARNING: No step found to update! Flow: " + flow + ", Field: " + field);
        }
    }

    public void putStep(CalculationFlow flow, ReserveField field, ReserveCalcStep step) {
        fieldValues.get(flow).put(field, step);
    }

    // === Step Calculation Engine ===

    public void calculateSteps(int stepIndex, Map<CalculationFlow, ReserveCalcStep> currentSteps,
                               ReserveCalcStep contextConditionStep) {

        ReserveField fieldName = currentSteps.values().iterator().next().getFieldName();
// logger.info("\n=== CALCULATING STEP " + stepIndex + ": " + fieldName + " ===");

        Map<CalculationFlow, ReserveCalcStep> snapshotForThisStep = new EnumMap<>(CalculationFlow.class);


// FIRST, store all steps in the context so they can be found later!
        for (Map.Entry<CalculationFlow, ReserveCalcStep> entry : currentSteps.entrySet()) {
            CalculationFlow flow = entry.getKey();
            ReserveCalcStep step = entry.getValue();

// Store the step in fieldValues so updateStepValue can find it
            putStep(flow, fieldName, step);
        }

// Process each flow's step
        for (Map.Entry<CalculationFlow, ReserveCalcStep> entry : currentSteps.entrySet()) {
            CalculationFlow flow = entry.getKey();
            ReserveCalcStep step = entry.getValue();

// logger.info("\n[" + flow + "] Processing " + step.getClass().getSimpleName() + " for " + fieldName);
// logger.info("  Current value before calc: " + step.getCurrentValue());

            try {
                BigDecimal value = step.calculateValue(this);
// logger.info("  Calculated value: {}", value);

// NOW update the tracking
                step.updateTracking(value);
// logger.info("  Updated tracking - current: {}, prev: {}", step.getCurrentValue(), step.getPreviousValue());

// Store in context
                updateStepValue(flow, step.getFieldName(), value);
// logger.info("  Stored in context for " + flow + "." + fieldName + " = " + value);

                snapshotForThisStep.put(flow, step.copy());
 
// Trigger running calculations for this flow
                triggerRunningCalculations(flow, step.getFieldName(), stepIndex > 0);

            } catch (Exception e) {
                logger.error("  ERROR executing step: " + e.getMessage());
                e.printStackTrace();
                updateStepValue(flow, step.getFieldName(), BigDecimal.ZERO);
            }
        }

// Store in resultSets for tracking
        resultSets.put(fieldName, new EnumMap<>(snapshotForThisStep));
// logger.info("\nStored resultSet for " + fieldName);
        showResultSetsPropagation();
// Handle context condition step if present
        if (contextConditionStep != null) {
// logger.info("\nProcessing context condition step...");
            try {
                BigDecimal finalValue = contextConditionStep.calculateValue(this);
// logger.info("  Context condition result: " + finalValue);
                for (CalculationFlow flow : CalculationFlow.values()) {
                    updateStepValue(flow, contextConditionStep.getFieldName(), finalValue);
                }
            } catch (Exception e) {
                logger.error("  ERROR in context condition: " + e.getMessage());
            }
        }

// Process dynamic steps
// logger.info("\nProcessing " + dynamicSteps.size() + " dynamic steps...");
        for (ReserveCalcStep dynamicStep : dynamicSteps) {
            if (!(dynamicStep instanceof Steps.RunningCalculationStep)) {
// logger.info("  Dynamic step: " + dynamicStep.getFieldName());
                try {
                    for (CalculationFlow flow : CalculationFlow.values()) {
                        ReserveCalcStep flowStep = dynamicStep.copy();
                        flowStep.setFlow(flow);

                        // FIX: Register the step first so updateStepValue can find it
                        putStep(flow, flowStep.getFieldName(), flowStep);

                        BigDecimal dynamicValue = flowStep.calculateValue(this);
                        updateStepValue(flow, flowStep.getFieldName(), dynamicValue);
                        // logger.info("    " + flow + "." + flowStep.getFieldName() + " = " + dynamicValue);

                        if (flow == CalculationFlow.OMS) {
                            dynamicValues.put(flowStep.getFieldName(), dynamicValue);
                        }
                    }
                } catch (Exception e) {
                    logger.error("  ERROR in dynamic step " + dynamicStep.getFieldName() + ": " + e.getMessage());
                }
            }
        }

        stepSnapshots.put(stepIndex, snapshotForThisStep);
// logger.info("\n=== END STEP " + stepIndex + " ===\n");
    }

    public void showResultSetsPropagation() {
        // Get the last outer entry
        Map.Entry<ReserveField, Map<CalculationFlow, ReserveCalcStep>> lastEntry = null;
        for (Map.Entry<ReserveField, Map<CalculationFlow, ReserveCalcStep>> entry : resultSets.entrySet()) {
            lastEntry = entry;
        }

        if (lastEntry != null) {
            ReserveField field = lastEntry.getKey();
            for (Map.Entry<CalculationFlow, ReserveCalcStep> flowEntry : lastEntry.getValue().entrySet()) {
                CalculationFlow flow = flowEntry.getKey();
                ReserveCalcStep step = flowEntry.getValue();
                logger.info("{} -> {} -> {}, {}, {}",
                        field,
                        flow,
                        step.getCurrentValue(),
                        step.getPreviousValue(),
                        step.getOriginalValue());
            }
        }
    }


    private void triggerRunningCalculations(CalculationFlow flow, ReserveField triggeredField,
                                            boolean afterInitStep) {
        List<Steps.RunningCalculationStep> flowRunningSteps = runningSteps.get(flow);

        for (Steps.RunningCalculationStep runningStep : flowRunningSteps) {
            if (runningStep.shouldTrigger(triggeredField, afterInitStep)) {
                try {
                    BigDecimal newValue = runningStep.calculateValue(this, triggeredField);
                    updateStepValue(flow, runningStep.getFieldName(), newValue);

                    logger.info(String.format(
                            "Running calculation triggered for %s in flow %s: triggered by %s, new value = %s",
                            runningStep.getFieldName(), flow, triggeredField, newValue));

                } catch (Exception e) {
                    logger.error("Error in running calculation '" + runningStep.getFieldName() +
                            "' for flow " + flow + ": " + e.getMessage());
                }
            }
        }
    }

    // === Getter Methods for Logger Compatibility ===

    public Map<Integer, Map<CalculationFlow, ReserveCalcStep>> getStepSnapshots() {
        return stepSnapshots;
    }

    public Map<ReserveField, List<BigDecimal>> getRunningTotalHistory() {
        return runningTotalHistory;
    }

    public Map<ReserveField, BigDecimal> getDynamicValues() {
        return dynamicValues;
    }

    // Methods for logger that expect flow-aware maps
    public Map<CalculationFlow, Map<ReserveField, BigDecimal>> getFullDynamicValues() {
        Map<CalculationFlow, Map<ReserveField, BigDecimal>> result = new EnumMap<>(CalculationFlow.class);
        // Only OMS flow has dynamic values in current implementation
        result.put(CalculationFlow.OMS, new HashMap<>(dynamicValues));
        return result;
    }

    public Map<CalculationFlow, Map<ReserveField, List<BigDecimal>>> getFullRunningTotalHistory() {
        Map<CalculationFlow, Map<ReserveField, List<BigDecimal>>> result = new EnumMap<>(CalculationFlow.class);
        // Only OMS flow has history in current implementation
        result.put(CalculationFlow.OMS, new HashMap<>(runningTotalHistory));
        return result;
    }

    public Map<ReserveField, Map<CalculationFlow, ReserveCalcStep>> getResultSets() {
        return Collections.unmodifiableMap(resultSets);
    }


}