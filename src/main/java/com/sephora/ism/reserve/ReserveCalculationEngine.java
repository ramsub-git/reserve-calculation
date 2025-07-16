// ReserveCalculationEngine.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

public class ReserveCalculationEngine {

    private final Map<CalculationFlow, List<ReserveCalcStep>> flowSteps = new EnumMap<>(CalculationFlow.class);
    private final Map<String, ReserveCalcStep> contextConditionSteps = new LinkedHashMap<>();
    private Function<ReserveCalcContext, Boolean> enginePreCheck = ctx -> true;
    private Function<ReserveCalcContext, Boolean> enginePostCheck = ctx -> true;
    private final List<ReserveCalcStep> dynamicSteps = new ArrayList<>();

    public ReserveCalculationEngine() {
        for (CalculationFlow flow : CalculationFlow.values()) {
            flowSteps.put(flow, new ArrayList<>());
        }
    }

    public void addStep(String fieldName, ReserveCalcStep mainStep, Map<CalculationFlow, ReserveCalcStep> alternateSteps, ReserveCalcStep contextConditionStep, boolean isDynamic) {
        alignFlowStepSizes();
        flowSteps.get(CalculationFlow.OMS).add(mainStep);
        for (Map.Entry<CalculationFlow, ReserveCalcStep> entry : alternateSteps.entrySet()) {
            flowSteps.get(entry.getKey()).add(entry.getValue());
        }
        contextConditionSteps.put(fieldName, contextConditionStep);

        if (isDynamic) {
            dynamicSteps.add(mainStep);
        }
    }

    public void setEnginePreCheck(Function<ReserveCalcContext, Boolean> preCheck) {
        this.enginePreCheck = preCheck != null ? preCheck : ctx -> true;
    }

    public void setEnginePostCheck(Function<ReserveCalcContext, Boolean> postCheck) {
        this.enginePostCheck = postCheck != null ? postCheck : ctx -> true;
    }

    public void calculate(ReserveCalcContext context) {
        if (!enginePreCheck.apply(context)) {
            throw new IllegalStateException("Engine pre-check failed: Required conditions not met.");
        }

        context.setDynamicSteps(dynamicSteps);

        int maxStepCount = flowSteps.values().stream().mapToInt(List::size).max().orElse(0);
        Map<CalculationFlow, ReserveCalcContext> flowContexts = new EnumMap<>(CalculationFlow.class);
        for (CalculationFlow flow : CalculationFlow.values()) {
            flowContexts.put(flow, new ReserveCalcContext());
        }

        for (int i = 0; i < maxStepCount; i++) {
            Map<CalculationFlow, ReserveCalcStep> currentSteps = new EnumMap<>(CalculationFlow.class);
            for (CalculationFlow flow : CalculationFlow.values()) {
                List<ReserveCalcStep> steps = flowSteps.get(flow);
                currentSteps.put(flow, i < steps.size() ? steps.get(i) : new ReserveCalcStep("noop", Collections.emptyList(), null, null, null, null));
            }
            String fieldName = currentSteps.get(CalculationFlow.OMS).getFieldName();
            ReserveCalcStep contextConditionStep = contextConditionSteps.getOrDefault(fieldName, new ReserveCalcStep(fieldName, Collections.emptyList(), null, null, null, null));
            context.calculateSteps(i, currentSteps, contextConditionStep, flowContexts);
        }

        if (!enginePostCheck.apply(context)) {
            throw new IllegalStateException("Engine post-check failed: Validation conditions not met.");
        }
    }

    private void alignFlowStepSizes() {
        int maxSize = flowSteps.values().stream().mapToInt(List::size).max().orElse(0);
        for (Map.Entry<CalculationFlow, List<ReserveCalcStep>> entry : flowSteps.entrySet()) {
            List<ReserveCalcStep> stepList = entry.getValue();
            while (stepList.size() < maxSize) {
                stepList.add(new ReserveCalcStep("noop", Collections.emptyList(), null, null, null, null));
            }
        }
    }


    public static void setupReserveCalculationSteps(ReserveCalculationEngine engine) {
        // Base Skuloc Input Fields
        engine.addStep("BYCL", new Steps.SkulocFieldStep("BYCL"), Map.of(), null, false);
        engine.addStep("SKUSTS", new Steps.SkulocFieldStep("SKUSTS"), Map.of(), null, false);
        engine.addStep("ONHAND", new Steps.SkulocFieldStep("ONHAND"), Map.of(), null, false);
        engine.addStep("ROHM", new Steps.SkulocFieldStep("ROHM"), Map.of(), null, false);
        engine.addStep("LOST", new Steps.SkulocFieldStep("LOST"), Map.of(), null, false);
        engine.addStep("OOBADJ", new Steps.SkulocFieldStep("OOBADJ"), Map.of(), null, false);

        // Calculation Step for INITAFS
        engine.addStep("INITAFS",
                new Steps.CalculationStep("INITAFS", List.of("ONHAND", "ROHM", "LOST", "OOBADJ"),
                        values -> values.get(0).subtract(values.get(1)).subtract(values.get(2)).subtract(values.get(3).max(BigDecimal.ZERO)),
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI,
                        new Steps.CalculationStep("INITAFS", List.of("ONHAND", "LOST"),
                                values -> values.get(0).subtract(values.get(1)),
                                null, null, null, null)
                ),
                new Steps.ContextConditionStep("INITAFS", List.of(),
                        ctx -> ctx.get("_flowResult_JEI_INITAFS").compareTo(BigDecimal.ZERO) > 0
                                && ctx.get("_flowResult_JEI_INITAFS").compareTo(ctx.get("_flowResult_OMS_INITAFS")) != 0
                                ? ctx.get("_flowResult_JEI_INITAFS") : ctx.get("_flowResult_OMS_INITAFS")
                ), false
        );

        // Example Dynamic Snapshots: @RETAILATS etc.
        engine.addStep("@RETAILATS",
                new Steps.CalculationStep("@RETAILATS", List.of("INITAFS"), values -> values.get(0), null, null, null, null),
                Map.of(), null, true);

        engine.addStep("@DOTATS",
                new Steps.CalculationStep("@DOTATS", List.of("INITAFS"), values -> values.get(0), null, null, null, null),
                Map.of(), null, true);

        engine.addStep("@UNCOMMIT",
                new Steps.CalculationStep("@UNCOMMIT", List.of("INITAFS"), values -> values.get(0), null, null, null, null),
                Map.of(), null, true);

        engine.addStep("@COMMITTED",
                new Steps.CalculationStep("@COMMITTED", List.of("INITAFS"), values -> values.get(0), null, null, null, null),
                Map.of(), null, true);

        engine.addStep("@UNCOMMHR",
                new Steps.CalculationStep("@UNCOMMHR", List.of("INITAFS"), values -> values.get(0), null, null, null, null),
                Map.of(), null, true);

        // Output Fields
        engine.addStep("@OMSSUP", new Steps.CopyStep("@OMSSUP", "@RETAILATS"), Map.of(), null, false);
        engine.addStep("@RETFINAL", new Steps.CopyStep("@RETFINAL", "@RETAILATS"), Map.of(), null, false);
        engine.addStep("@OMSFINAL", new Steps.CopyStep("@OMSFINAL", "@DOTATS"), Map.of(), null, false);

        // Reserve and Allocation Fields
        List.of("@SNBA", "@DTCOA", "@ROHPA", "@DOTHRYA", "@DOTHRNA", "@RETHRYA",
                        "@RETHRNA", "@HLDHRA", "@DOTRSVA", "@RETRSVA", "@AOUTBVA", "@NEEDA")
                .forEach(field -> engine.addStep(field, new Steps.CopyStep(field, "@RETAILATS"), Map.of(), null, false));
    }
}