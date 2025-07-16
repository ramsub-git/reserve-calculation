package com.sephora.ism.reserve;

import java.util.*;
import java.util.logging.Logger;
import java.math.BigDecimal;
import java.util.stream.Collectors;

public class ReserveCalculationEngine {
    private static final Logger LOGGER = Logger.getLogger(ReserveCalculationEngine.class.getName());

    private final Map<CalculationFlow, List<ReserveCalcStep>> flowSteps = new LinkedHashMap<>();
    private final Map<String, ReserveCalcStep> contextConditionSteps = new LinkedHashMap<>();

    public void addStep(
            ReserveCalcStep mainStep,
            Map<CalculationFlow, ReserveCalcStep> alternateSteps,
            ReserveCalcStep contextConditionStep
    ) {
        LOGGER.info("Adding step: " + mainStep.getFieldName());
        flowSteps.computeIfAbsent(CalculationFlow.OMS, k -> new ArrayList<>()).add(mainStep);

        alternateSteps.forEach((flow, step) -> {
            LOGGER.info("Adding alternate step for flow " + flow + ": " + step.getFieldName());
            flowSteps.computeIfAbsent(flow, k -> new ArrayList<>()).add(step);
        });

        if (contextConditionStep != null) {
            LOGGER.info("Adding context condition step for: " + mainStep.getFieldName());
            contextConditionSteps.put(mainStep.getFieldName(), contextConditionStep);
        }
    }

    public void calculate(ReserveCalcContext context) {
        LOGGER.info("Starting calculation process");
        int maxSteps = flowSteps.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        LOGGER.info("Maximum steps across flows: " + maxSteps);

        for (int stepIndex = 0; stepIndex < maxSteps; stepIndex++) {
            LOGGER.info("Processing step index: " + stepIndex);
            List<ReserveCalcStep> currentSteps = new ArrayList<>();

            for (CalculationFlow flow : flowSteps.keySet()) {
                List<ReserveCalcStep> flowStepList = flowSteps.get(flow);
                if (stepIndex < flowStepList.size()) {
                    currentSteps.add(flowStepList.get(stepIndex));
                }
            }

            LOGGER.info("Current steps for this iteration: " +
                    currentSteps.stream()
                            .map(ReserveCalcStep::getFieldName)
                            .collect(Collectors.toList())
            );

            context.calculateSteps(
                    currentSteps,
                    contextConditionSteps.get(currentSteps.get(0).getFieldName())
            );
        }

        LOGGER.info("Calculation process completed");
    }

    public static ReserveCalculationEngine setupEngine() {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();

        // Skuloc Field Steps
        // Skuloc Field Steps
        engine.addStep(
                new SkulocFieldStep("onHand"),
                Map.of(),
                null
        );
        engine.addStep(
                new SkulocFieldStep("rohm"),
                Map.of(),
                null
        );
        engine.addStep(
                new SkulocFieldStep("lost"),
                Map.of(),
                null
        );
        engine.addStep(
                new SkulocFieldStep("damaged"),
                Map.of(),
                null
        );
        engine.addStep(
                new SkulocFieldStep("retPickReserve"),
                Map.of(),
                null
        );
        engine.addStep(
                new SkulocFieldStep("dotPickReserve"),
                Map.of(),
                null
        );
        engine.addStep(
                new SkulocFieldStep("dotShipNotBill"),
                Map.of(),
                null
        );
        engine.addStep(
                new SkulocFieldStep("dotOpenCustOrder"),
                Map.of(),
                null
        );
        engine.addStep(
                new SkulocFieldStep("retHardReserveAtsYes"),
                Map.of(),
                null
        );
        engine.addStep(
                new SkulocFieldStep("dotHardReserveAtsYes"),
                Map.of(),
                null
        );
        // Initial AFS Calculation
        engine.addStep(
                new CalculationStep(
                        "initialAfs",
                        List.of("onHand", "rohm", "lost", "damaged"),
                        (inputs) -> inputs.get("onHand")
                                .subtract(inputs.get("rohm"))
                                .subtract(inputs.get("lost"))
                                .subtract(inputs.get("damaged"))
                ),
                Map.of(
                        CalculationFlow.JEI, new CalculationStep(
                                "initialAfsJei",
                                List.of("onHand", "lost"),
                                (inputs) -> inputs.get("onHand").subtract(inputs.get("lost"))
                        )
                ),
                new ContextConditionStep(
                        "initialAfsContextCondition",
                        List.of("flowValues"),
                        (inputs) -> {
                            // Debug print to understand input
                            System.out.println("Inputs: " + inputs);

                            // Check if flowValues exists and is a Map
                            Object flowValuesObj = inputs.get("flowValues");
                            if (!(flowValuesObj instanceof Map)) {
                                System.out.println("FlowValues is not a Map: " + flowValuesObj);
                                return BigDecimal.ZERO;
                            }

                            Map<String, BigDecimal> flowValues = (Map<String, BigDecimal>) flowValuesObj;

                            BigDecimal omsValue = flowValues.getOrDefault(CalculationFlow.OMS.name(), BigDecimal.ZERO);
                            BigDecimal jeiValue = flowValues.getOrDefault(CalculationFlow.JEI.name(), BigDecimal.ZERO);

                            return jeiValue.compareTo(BigDecimal.ZERO) > 0 && !jeiValue.equals(omsValue)
                                    ? jeiValue
                                    : omsValue;
                        }
                )
        );

        // Uncommitted Calculation
        engine.addStep(
                new CalculationStep(
                        "uncommit",
                        List.of("initialAfs", "retPickReserve", "dotPickReserve"),
                        (inputs) -> inputs.get("initialAfs")
                                .subtract(inputs.get("retPickReserve"))
                                .subtract(inputs.get("dotPickReserve"))
                ),
                Map.of(),
                null
        );

        // Final Reserve Calculation
        engine.addStep(
                new CalculationStep(
                        "finalReserve",
                        List.of("uncommit", "dotOpenCustOrder", "dotShipNotBill"),
                        (inputs) -> inputs.get("uncommit")
                                .subtract(inputs.get("dotOpenCustOrder"))
                                .subtract(inputs.get("dotShipNotBill"))
                ),
                Map.of(),
                null
        );

        return engine;
    }
}