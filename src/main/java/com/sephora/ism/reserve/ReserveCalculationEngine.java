// ReserveCalculationEngine.java
package com.sephora.ism.reserve;

import java.util.*;
import java.util.logging.Logger;
import java.math.BigDecimal;

public class ReserveCalculationEngine {
    private static final Logger LOGGER = Logger.getLogger(ReserveCalculationEngine.class.getName());

    private final Map<CalculationFlow, List<ReserveCalcStep>> flowSteps = new LinkedHashMap<>();
    private final Map<String, ContextConditionStep> contextConditions = new LinkedHashMap<>();
    private int maxStepCount = 0;

    public ReserveCalculationEngine() {
        for (CalculationFlow flow : CalculationFlow.values()) {
            flowSteps.put(flow, new ArrayList<>());
        }
    }

    public void addStep(ReserveCalcStep mainStep,
                        Map<CalculationFlow, ReserveCalcStep> alternateSteps,
                        ContextConditionStep contextCondition) {

        // Ensure all flows are aligned by padding with no-ops
        int currentMaxIndex = flowSteps.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        // Pad all flows to same length
        for (CalculationFlow flow : CalculationFlow.values()) {
            List<ReserveCalcStep> steps = flowSteps.get(flow);
            while (steps.size() < currentMaxIndex) {
                steps.add(new SkulocFieldStep("noop_" + steps.size()));
            }
        }

        // Add main step to OMS flow
        flowSteps.get(CalculationFlow.OMS).add(mainStep);

        // Add alternate steps or use main step as default
        for (CalculationFlow flow : CalculationFlow.values()) {
            if (flow != CalculationFlow.OMS) {
                ReserveCalcStep stepToAdd = alternateSteps.getOrDefault(flow, mainStep);
                flowSteps.get(flow).add(stepToAdd);
            }
        }

        // Register context condition if provided
        if (contextCondition != null) {
            contextConditions.put(mainStep.getFieldName(), contextCondition);
        }

        maxStepCount = Math.max(maxStepCount, currentMaxIndex + 1);
        LOGGER.info(String.format("Added step %s at index %d", mainStep.getFieldName(), currentMaxIndex));
    }

    public void calculate(ReserveCalcContext context) {
        LOGGER.info("Starting calculation with " + maxStepCount + " steps");

        // Execute steps in order
        for (int stepIndex = 0; stepIndex < maxStepCount; stepIndex++) {
            LOGGER.info("Processing step " + stepIndex);

            // Get the OMS step to determine field name
            List<ReserveCalcStep> omsSteps = flowSteps.get(CalculationFlow.OMS);
            if (stepIndex >= omsSteps.size()) continue;

            ReserveCalcStep omsStep = omsSteps.get(stepIndex);
            String fieldName = omsStep.getFieldName();

            // Skip no-ops
            if (fieldName.startsWith("noop_")) continue;

            // Execute step for each flow
            Map<CalculationFlow, BigDecimal> flowResults = new LinkedHashMap<>();

            for (CalculationFlow flow : CalculationFlow.values()) {
                List<ReserveCalcStep> steps = flowSteps.get(flow);
                if (stepIndex < steps.size()) {
                    ReserveCalcStep step = steps.get(stepIndex);

                    // Create a temporary context for this flow's calculation
                    ReserveCalcContext flowContext = new ReserveCalcContext();
                    flowContext.fieldValues.putAll(context.fieldValues);
                    flowContext.fieldValues.putAll(context.getFlowContext(flow));

                    // Execute the step
                    step.calculateValue(flowContext);

                    // Store the result
                    BigDecimal result = flowContext.get(step.getFieldName());
                    flowResults.put(flow, result);

                    // Update flow-specific context
                    context.putFlowValue(flow, step.getFieldName(), result);
                }
            }

            // Apply context condition if exists
            ContextConditionStep condition = contextConditions.get(fieldName);
            if (condition != null) {
                // Store flow results for condition to access
                context.put("_flowResults_" + fieldName, flowResults.get(CalculationFlow.OMS));
                for (CalculationFlow flow : flowResults.keySet()) {
                    context.put("_flowResult_" + flow + "_" + fieldName, flowResults.get(flow));
                }

                condition.calculateValue(context);
            } else {
                // Default to OMS value
                context.put(fieldName, flowResults.getOrDefault(CalculationFlow.OMS, BigDecimal.ZERO));
            }
        }

        LOGGER.info("Calculation completed");
    }

    public static ReserveCalculationEngine setupEngine() {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();

        // Input fields
        engine.addStep(new SkulocFieldStep("onHand"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("rohm"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("lost"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("oobAdjustment"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("retPickReserve"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("dotPickReserve"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("dotShipNotBill"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("dotOpenCustOrder"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("retHardReserveAtsYes"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("retHardReserveAtsNo"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("dotHardReserveAtsYes"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("dotHardReserveAtsNo"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("heldHardReserve"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("dotReserve"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("retReserve"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("dotOutb"), Map.of(), null);
        engine.addStep(new SkulocFieldStep("retNeed"), Map.of(), null);

        // Initial AFS Calculation
        engine.addStep(
                new CalculationStep(
                        "initialAfs",
                        List.of("onHand", "rohm", "lost", "oobAdjustment"),
                        (inputs) -> {
                            BigDecimal onHand = inputs.get("onHand");
                            BigDecimal rohm = inputs.get("rohm");
                            BigDecimal lost = inputs.get("lost");
                            BigDecimal oob = inputs.get("oobAdjustment");

                            // For non-JEI: onHand - rohm - lost - max(oob, 0)
                            return onHand.subtract(rohm).subtract(lost).subtract(oob.max(BigDecimal.ZERO));
                        }
                ),
                Map.of(
                        CalculationFlow.JEI, new CalculationStep(
                                "initialAfs",
                                List.of("onHand", "lost"),
                                (inputs) -> {
                                    // For JEI: onHand - lost
                                    return inputs.get("onHand").subtract(inputs.get("lost"));
                                }
                        )
                ),
                new ContextConditionStep(
                        "initialAfs",
                        List.of(),
                        (context) -> {
                            BigDecimal omsValue = context.get("_flowResult_OMS_initialAfs");
                            BigDecimal jeiValue = context.get("_flowResult_JEI_initialAfs");

                            // Use JEI if positive and different from OMS
                            if (jeiValue.compareTo(BigDecimal.ZERO) > 0 && !jeiValue.equals(omsValue)) {
                                return jeiValue;
                            }
                            return omsValue;
                        }
                )
        );

        // Constraint calculations for consumption

        // SNB Constraint
        engine.addStep(
                new ConstraintStep("snbConstraint", "dotShipNotBill", "initialAfs"),
                Map.of(),
                null
        );

        // Running inventory after SNB
        engine.addStep(
                new CalculationStep(
                        "runningAfterSnb",
                        List.of("initialAfs", "snbConstraint"),
                        (inputs) -> inputs.get("initialAfs").subtract(inputs.get("snbConstraint"))
                ),
                Map.of(),
                null
        );

        // DTCO Constraint
        engine.addStep(
                new ConstraintStep("dtcoConstraint", "dotOpenCustOrder", "runningAfterSnb"),
                Map.of(),
                null
        );

        // Running inventory after DTCO
        engine.addStep(
                new CalculationStep(
                        "runningAfterDtco",
                        List.of("runningAfterSnb", "dtcoConstraint"),
                        (inputs) -> inputs.get("runningAfterSnb").subtract(inputs.get("dtcoConstraint"))
                ),
                Map.of(),
                null
        );

        // ROHP Constraint
        engine.addStep(
                new ConstraintStep("rohpConstraint", "retPickReserve", "runningAfterDtco"),
                Map.of(),
                null
        );

        // Uncommitted AFS (with zero floor)
        engine.addStep(
                new CalculationStep(
                        "uncommittedAfs",
                        List.of("runningAfterDtco", "rohpConstraint"),
                        (inputs) -> {
                            BigDecimal result = inputs.get("runningAfterDtco").subtract(inputs.get("rohpConstraint"));
                            return result.max(BigDecimal.ZERO);
                        }
                ),
                Map.of(),
                null
        );

        // Hard Reserve Calculations

        // DOT Hard Reserve ATS YES constraint
        engine.addStep(
                new ConstraintStep("dotHardResYesConstraint", "dotHardReserveAtsYes", "uncommittedAfs"),
                Map.of(),
                null
        );

        // Running after DOT Hard YES
        engine.addStep(
                new CalculationStep(
                        "runningAfterDotHardYes",
                        List.of("uncommittedAfs", "dotHardResYesConstraint"),
                        (inputs) -> inputs.get("uncommittedAfs").subtract(inputs.get("dotHardResYesConstraint"))
                ),
                Map.of(),
                null
        );

        // Similar for other hard reserves...

        // DOT Reserve constraint
        engine.addStep(
                new ConstraintStep("dotReserveConstraint", "dotReserve", "runningAfterDotHardYes"),
                Map.of(),
                null
        );

        // Running after DOT Reserve
        engine.addStep(
                new CalculationStep(
                        "runningAfterDotReserve",
                        List.of("runningAfterDotHardYes", "dotReserveConstraint"),
                        (inputs) -> inputs.get("runningAfterDotHardYes").subtract(inputs.get("dotReserveConstraint"))
                ),
                Map.of(),
                null
        );

        // RET Reserve constraint
        engine.addStep(
                new ConstraintStep("retReserveConstraint", "retReserve", "runningAfterDotReserve"),
                Map.of(),
                null
        );

        // Final Reserve (after all deductions)
        engine.addStep(
                new CalculationStep(
                        "finalReserve",
                        List.of("runningAfterDotReserve", "retReserveConstraint"),
                        (inputs) -> {
                            BigDecimal result = inputs.get("runningAfterDotReserve").subtract(inputs.get("retReserveConstraint"));
                            return result.max(BigDecimal.ZERO);
                        }
                ),
                Map.of(),
                null
        );

        // DOT ATS Calculation
        engine.addStep(
                new CalculationStep(
                        "dotAts",
                        List.of("dotHardResYesConstraint", "dotReserveConstraint"),
                        (inputs) -> inputs.get("dotHardResYesConstraint").add(inputs.get("dotReserveConstraint"))
                ),
                Map.of(),
                null
        );

        // Adjusted OUTB calculation
        engine.addStep(
                new CalculationStep(
                        "adjustedOutb",
                        List.of("dotOutb", "dotAts"),
                        (inputs) -> {
                            BigDecimal outb = inputs.get("dotOutb");
                            BigDecimal dotAts = inputs.get("dotAts");
                            return outb.subtract(dotAts).max(BigDecimal.ZERO);
                        }
                ),
                Map.of(),
                null
        );

        // RET ATS Calculation
        engine.addStep(
                new CalculationStep(
                        "retAts",
                        List.of("retReserveConstraint"),
                        (inputs) -> inputs.get("retReserveConstraint")
                ),
                Map.of(),
                null
        );

        // Adjusted Need calculation
        engine.addStep(
                new CalculationStep(
                        "adjustedNeed",
                        List.of("retNeed", "retAts"),
                        (inputs) -> {
                            BigDecimal need = inputs.get("retNeed");
                            BigDecimal retAts = inputs.get("retAts");
                            return need.subtract(retAts).max(BigDecimal.ZERO);
                        }
                ),
                Map.of(),
                null
        );

        // Summary fields
        engine.addStep(
                new CalculationStep(
                        "committed",
                        List.of("rohpConstraint", "dtcoConstraint", "snbConstraint"),
                        (inputs) -> inputs.get("rohpConstraint")
                                .add(inputs.get("dtcoConstraint"))
                                .add(inputs.get("snbConstraint"))
                ),
                Map.of(),
                null
        );

        engine.addStep(
                new CalculationStep(
                        "uncommitted",
                        List.of("initialAfs", "committed", "dotReserveConstraint", "retReserveConstraint"),
                        (inputs) -> inputs.get("initialAfs")
                                .subtract(inputs.get("committed"))
                                .subtract(inputs.get("dotReserveConstraint"))
                                .subtract(inputs.get("retReserveConstraint"))
                                .max(BigDecimal.ZERO)
                ),
                Map.of(),
                null
        );

        return engine;
    }
}