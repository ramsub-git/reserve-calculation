// ReserveCalculationEngine.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

import com.sephora.ism.reserve.Steps;

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
        // Base Key Fields
        engine.addStep("DIV",
                new Steps.ConstantStep("DIV", new BigDecimal("30")),
                Map.of(), null, false);
        // Base SKULOC Input Fields
        engine.addStep("BYCL",
                new Steps.SkulocFieldStep("BYCL"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("BYCL"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("BYCL")),
                null, false);
        engine.addStep("SKUSTS",
                new Steps.SkulocFieldStep("SKUSTS"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("SKUSTS"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("SKUSTS")),
                null, false);
        engine.addStep("ONHAND",
                new Steps.SkulocFieldStep("ONHAND"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("ONHAND"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("ONHAND")),
                null, false);
        engine.addStep("ROHM",
                new Steps.SkulocFieldStep("ROHM"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("ROHM"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("ROHM")),
                null, false);
        engine.addStep("LOST",
                new Steps.SkulocFieldStep("LOST"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("LOST"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("LOST")),
                null, false);
        engine.addStep("OOBADJ",
                new Steps.SkulocFieldStep("OOBADJ"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("OOBADJ"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("OOBADJ")),
                null, false);
        engine.addStep("SNB",
                new Steps.SkulocFieldStep("SNB"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("SNB"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("SNB")),
                null, false);
        engine.addStep("DTCO",
                new Steps.SkulocFieldStep("DTCO"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("DTCO"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("DTCO")),
                null, false);
        engine.addStep("ROHP",
                new Steps.SkulocFieldStep("ROHP"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("ROHP"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("ROHP")),
                null, false);
        engine.addStep("DOTHRY",
                new Steps.SkulocFieldStep("DOTHRY"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("DOTHRY"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("DOTHRY")),
                null, false);
        engine.addStep("DOTHRN",
                new Steps.SkulocFieldStep("DOTHRN"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("DOTHRN"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("DOTHRN")),
                null, false);
        engine.addStep("RETHRY",
                new Steps.SkulocFieldStep("RETHRY"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("RETHRY"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("RETHRY")),
                null, false);
        engine.addStep("RETHRN",
                new Steps.SkulocFieldStep("RETHRN"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("RETHRN"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("RETHRN")),
                null, false);
        engine.addStep("HLDHR",
                new Steps.SkulocFieldStep("HLDHR"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("HLDHR"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("HLDHR")),
                null, false);
        engine.addStep("DOTRSV",
                new Steps.SkulocFieldStep("DOTRSV"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("DOTRSV"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("DOTRSV")),
                null, false);
        engine.addStep("RETRSV",
                new Steps.SkulocFieldStep("RETRSV"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("RETRSV"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("RETRSV")),
                null, false);
        engine.addStep("DOTOUTB",
                new Steps.SkulocFieldStep("DOTOUTB"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("DOTOUTB"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("DOTOUTB")),
                null, false);
        engine.addStep("NEED",
                new Steps.SkulocFieldStep("NEED"),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep("NEED"),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep("NEED")),
                null, false);

        // Initial AFS Calculation (INITAFS) with alternate flow for JEI
        engine.addStep("INITAFS",
                new Steps.CalculationStep("INITAFS", List.of("ONHAND", "ROHM", "LOST", "OOBADJ"),
                        values -> values.get(0).subtract(values.get(1)).subtract(values.get(2)).subtract(values.get(3).max(BigDecimal.ZERO)), null, null, null, null),
                Map.of(
                        CalculationFlow.JEI,
                        new Steps.CalculationStep("INITAFS", List.of("ONHAND", "LOST"),
                                values -> values.get(0).subtract(values.get(1)), null, null, null, null),
                        CalculationFlow.FRM,
                        new Steps.CalculationStep("INITAFS", List.of("ONHAND", "ROHM", "LOST", "OOBADJ"),
                                values -> values.get(0).subtract(values.get(1)).subtract(values.get(2)).subtract(values.get(3).max(BigDecimal.ZERO)), null, null, null, null)
                ),
                new Steps.ContextConditionStep("INITAFS", List.of(),
                        ctx -> ctx.get("_flowResult_JEI_INITAFS").compareTo(BigDecimal.ZERO) > 0
                                && ctx.get("_flowResult_JEI_INITAFS").compareTo(ctx.get("_flowResult_OMS_INITAFS")) != 0
                                ? ctx.get("_flowResult_JEI_INITAFS") : ctx.get("_flowResult_OMS_INITAFS")
                ), false);

        // Constraint and Derived Steps
        engine.addStep("SNBX",
                new Steps.CalculationStep("SNBX", List.of("INITAFS", "SNB"),
                        values -> {
                            BigDecimal afs = values.get(0);
                            BigDecimal snb = values.get(1);
                            return afs.compareTo(BigDecimal.ZERO) > 0 && snb.compareTo(afs) > 0 ? afs : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("SNBX", List.of("INITAFS", "SNB"),
                                values -> {
                                    BigDecimal afs = values.get(0);
                                    BigDecimal snb = values.get(1);
                                    return afs.compareTo(BigDecimal.ZERO) > 0 && snb.compareTo(afs) > 0 ? afs : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("SNBX", List.of("INITAFS", "SNB"),
                                values -> {
                                    BigDecimal afs = values.get(0);
                                    BigDecimal snb = values.get(1);
                                    return afs.compareTo(BigDecimal.ZERO) > 0 && snb.compareTo(afs) > 0 ? afs : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("DTCOX",
                new Steps.CalculationStep("DTCOX", List.of("INITAFS", "SNB", "DTCO"),
                        values -> {
                            BigDecimal afs = values.get(0);
                            BigDecimal snb = values.get(1);
                            BigDecimal dtco = values.get(2);
                            BigDecimal leftover1 = afs.subtract(snb);
                            if (leftover1.compareTo(BigDecimal.ZERO) < 0) leftover1 = BigDecimal.ZERO;
                            return leftover1.compareTo(dtco) < 0 ? leftover1 : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("DTCOX", List.of("INITAFS", "SNB", "DTCO"),
                                values -> {
                                    BigDecimal afs = values.get(0);
                                    BigDecimal snb = values.get(1);
                                    BigDecimal dtco = values.get(2);
                                    BigDecimal leftover1 = afs.subtract(snb);
                                    if (leftover1.compareTo(BigDecimal.ZERO) < 0) leftover1 = BigDecimal.ZERO;
                                    return leftover1.compareTo(dtco) < 0 ? leftover1 : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("DTCOX", List.of("INITAFS", "SNB", "DTCO"),
                                values -> {
                                    BigDecimal afs = values.get(0);
                                    BigDecimal snb = values.get(1);
                                    BigDecimal dtco = values.get(2);
                                    BigDecimal leftover1 = afs.subtract(snb);
                                    if (leftover1.compareTo(BigDecimal.ZERO) < 0) leftover1 = BigDecimal.ZERO;
                                    return leftover1.compareTo(dtco) < 0 ? leftover1 : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("ROHPX",
                new Steps.CalculationStep("ROHPX", List.of("INITAFS", "SNB", "DTCO", "ROHP"),
                        values -> {
                            BigDecimal afs = values.get(0);
                            BigDecimal snb = values.get(1);
                            BigDecimal dtco = values.get(2);
                            BigDecimal rohp = values.get(3);
                            BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                            BigDecimal leftover2 = leftover1.compareTo(dtco) < 0 ? BigDecimal.ZERO : leftover1.subtract(dtco);
                            return leftover2.compareTo(rohp) < 0 ? leftover2 : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("ROHPX", List.of("INITAFS", "SNB", "DTCO", "ROHP"),
                                values -> {
                                    BigDecimal afs = values.get(0);
                                    BigDecimal snb = values.get(1);
                                    BigDecimal dtco = values.get(2);
                                    BigDecimal rohp = values.get(3);
                                    BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                                    BigDecimal leftover2 = leftover1.compareTo(dtco) < 0 ? BigDecimal.ZERO : leftover1.subtract(dtco);
                                    return leftover2.compareTo(rohp) < 0 ? leftover2 : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("ROHPX", List.of("INITAFS", "SNB", "DTCO", "ROHP"),
                                values -> {
                                    BigDecimal afs = values.get(0);
                                    BigDecimal snb = values.get(1);
                                    BigDecimal dtco = values.get(2);
                                    BigDecimal rohp = values.get(3);
                                    BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                                    BigDecimal leftover2 = leftover1.compareTo(dtco) < 0 ? BigDecimal.ZERO : leftover1.subtract(dtco);
                                    return leftover2.compareTo(rohp) < 0 ? leftover2 : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("UNCOMAFS",
                new Steps.CalculationStep("UNCOMAFS", List.of("INITAFS", "SNB", "DTCO", "ROHP"),
                        values -> {
                            BigDecimal result = values.get(0).subtract(values.get(1)).subtract(values.get(2)).subtract(values.get(3));
                            return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("UNCOMAFS", List.of("INITAFS", "SNB", "DTCO", "ROHP"),
                                values -> {
                                    BigDecimal result = values.get(0).subtract(values.get(1)).subtract(values.get(2)).subtract(values.get(3));
                                    return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("UNCOMAFS", List.of("INITAFS", "SNB", "DTCO", "ROHP"),
                                values -> {
                                    BigDecimal result = values.get(0).subtract(values.get(1)).subtract(values.get(2)).subtract(values.get(3));
                                    return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("DOTHRYX",
                new Steps.CalculationStep("DOTHRYX", List.of("UNCOMAFS", "DOTHRY"),
                        values -> {
                            BigDecimal left = values.get(0);
                            BigDecimal dty = values.get(1);
                            return left.compareTo(dty) < 0 ? left : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("DOTHRYX", List.of("UNCOMAFS", "DOTHRY"),
                                values -> {
                                    BigDecimal left = values.get(0);
                                    BigDecimal dty = values.get(1);
                                    return left.compareTo(dty) < 0 ? left : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("DOTHRYX", List.of("UNCOMAFS", "DOTHRY"),
                                values -> {
                                    BigDecimal left = values.get(0);
                                    BigDecimal dty = values.get(1);
                                    return left.compareTo(dty) < 0 ? left : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("DOTHRNX",
                new Steps.CalculationStep("DOTHRNX", List.of("UNCOMAFS", "DOTHRY", "DOTHRN"),
                        values -> {
                            BigDecimal left3 = values.get(0).subtract(values.get(1));
                            if (left3.compareTo(BigDecimal.ZERO) < 0) left3 = BigDecimal.ZERO;
                            BigDecimal dtn = values.get(2);
                            return left3.compareTo(dtn) < 0 ? left3 : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("DOTHRNX", List.of("UNCOMAFS", "DOTHRY", "DOTHRN"),
                                values -> {
                                    BigDecimal left3 = values.get(0).subtract(values.get(1));
                                    if (left3.compareTo(BigDecimal.ZERO) < 0) left3 = BigDecimal.ZERO;
                                    BigDecimal dtn = values.get(2);
                                    return left3.compareTo(dtn) < 0 ? left3 : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("DOTHRNX", List.of("UNCOMAFS", "DOTHRY", "DOTHRN"),
                                values -> {
                                    BigDecimal left3 = values.get(0).subtract(values.get(1));
                                    if (left3.compareTo(BigDecimal.ZERO) < 0) left3 = BigDecimal.ZERO;
                                    BigDecimal dtn = values.get(2);
                                    return left3.compareTo(dtn) < 0 ? left3 : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("RETHRYX",
                new Steps.CalculationStep("RETHRYX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY"),
                        values -> {
                            BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                            BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                            BigDecimal leftAfterDot = values.get(0).subtract(dotHryAct).subtract(dotHrnAct);
                            if (leftAfterDot.compareTo(BigDecimal.ZERO) < 0) leftAfterDot = BigDecimal.ZERO;
                            BigDecimal rhy = values.get(5);
                            return leftAfterDot.compareTo(rhy) < 0 ? leftAfterDot : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("RETHRYX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal leftAfterDot = values.get(0).subtract(dotHryAct).subtract(dotHrnAct);
                                    if (leftAfterDot.compareTo(BigDecimal.ZERO) < 0) leftAfterDot = BigDecimal.ZERO;
                                    BigDecimal rhy = values.get(5);
                                    return leftAfterDot.compareTo(rhy) < 0 ? leftAfterDot : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("RETHRYX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal leftAfterDot = values.get(0).subtract(dotHryAct).subtract(dotHrnAct);
                                    if (leftAfterDot.compareTo(BigDecimal.ZERO) < 0) leftAfterDot = BigDecimal.ZERO;
                                    BigDecimal rhy = values.get(5);
                                    return leftAfterDot.compareTo(rhy) < 0 ? leftAfterDot : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("RETHRNX",
                new Steps.CalculationStep("RETHRNX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN"),
                        values -> {
                            BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                            BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                            BigDecimal leftAfterDot = values.get(0).subtract(dotHryAct).subtract(dotHrnAct);
                            if (leftAfterDot.compareTo(BigDecimal.ZERO) < 0) leftAfterDot = BigDecimal.ZERO;
                            BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                            BigDecimal leftAfterRHY = leftAfterDot.subtract(rethryAct);
                            if (leftAfterRHY.compareTo(BigDecimal.ZERO) < 0) leftAfterRHY = BigDecimal.ZERO;
                            BigDecimal rhn = values.get(7);
                            return leftAfterRHY.compareTo(rhn) < 0 ? leftAfterRHY : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("RETHRNX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal leftAfterDot = values.get(0).subtract(dotHryAct).subtract(dotHrnAct);
                                    if (leftAfterDot.compareTo(BigDecimal.ZERO) < 0) leftAfterDot = BigDecimal.ZERO;
                                    BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                                    BigDecimal leftAfterRHY = leftAfterDot.subtract(rethryAct);
                                    if (leftAfterRHY.compareTo(BigDecimal.ZERO) < 0) leftAfterRHY = BigDecimal.ZERO;
                                    BigDecimal rhn = values.get(7);
                                    return leftAfterRHY.compareTo(rhn) < 0 ? leftAfterRHY : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("RETHRNX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal leftAfterDot = values.get(0).subtract(dotHryAct).subtract(dotHrnAct);
                                    if (leftAfterDot.compareTo(BigDecimal.ZERO) < 0) leftAfterDot = BigDecimal.ZERO;
                                    BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                                    BigDecimal leftAfterRHY = leftAfterDot.subtract(rethryAct);
                                    if (leftAfterRHY.compareTo(BigDecimal.ZERO) < 0) leftAfterRHY = BigDecimal.ZERO;
                                    BigDecimal rhn = values.get(7);
                                    return leftAfterRHY.compareTo(rhn) < 0 ? leftAfterRHY : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("HLDHRX",
                new Steps.CalculationStep("HLDHRX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR"),
                        values -> {
                            BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                            BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                            BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                            BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                            BigDecimal leftAfterRes = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct);
                            if (leftAfterRes.compareTo(BigDecimal.ZERO) < 0) leftAfterRes = BigDecimal.ZERO;
                            BigDecimal hld = values.get(9);
                            return leftAfterRes.compareTo(hld) < 0 ? leftAfterRes : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("HLDHRX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                                    BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                                    BigDecimal leftAfterRes = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct);
                                    if (leftAfterRes.compareTo(BigDecimal.ZERO) < 0) leftAfterRes = BigDecimal.ZERO;
                                    BigDecimal hld = values.get(9);
                                    return leftAfterRes.compareTo(hld) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("HLDHRX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                                    BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                                    BigDecimal leftAfterRes = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct);
                                    if (leftAfterRes.compareTo(BigDecimal.ZERO) < 0) leftAfterRes = BigDecimal.ZERO;
                                    BigDecimal hld = values.get(9);
                                    return leftAfterRes.compareTo(hld) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("DOTRSVX",
                new Steps.CalculationStep("DOTRSVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV"),
                        values -> {
                            BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                            BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                            BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                            BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                            BigDecimal heldAct = values.get(10).compareTo(BigDecimal.ZERO) > 0 ? values.get(10) : values.get(9);
                            BigDecimal leftAfterRes = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct);
                            if (leftAfterRes.compareTo(BigDecimal.ZERO) < 0) leftAfterRes = BigDecimal.ZERO;
                            BigDecimal drsv = values.get(11);
                            return leftAfterRes.compareTo(drsv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("DOTRSVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                                    BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                                    BigDecimal heldAct = values.get(10).compareTo(BigDecimal.ZERO) > 0 ? values.get(10) : values.get(9);
                                    BigDecimal leftAfterRes = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct);
                                    if (leftAfterRes.compareTo(BigDecimal.ZERO) < 0) leftAfterRes = BigDecimal.ZERO;
                                    BigDecimal drsv = values.get(11);
                                    return leftAfterRes.compareTo(drsv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("DOTRSVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                                    BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                                    BigDecimal heldAct = values.get(10).compareTo(BigDecimal.ZERO) > 0 ? values.get(10) : values.get(9);
                                    BigDecimal leftAfterRes = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct);
                                    if (leftAfterRes.compareTo(BigDecimal.ZERO) < 0) leftAfterRes = BigDecimal.ZERO;
                                    BigDecimal drsv = values.get(11);
                                    return leftAfterRes.compareTo(drsv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("RETRSVX",
                new Steps.CalculationStep("RETRSVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV"),
                        values -> {
                            BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                            BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                            BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                            BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                            BigDecimal heldAct = values.get(10).compareTo(BigDecimal.ZERO) > 0 ? values.get(10) : values.get(9);
                            BigDecimal dotrsvAct = values.get(12).compareTo(BigDecimal.ZERO) > 0 ? values.get(12) : values.get(11);
                            BigDecimal leftAfterDotRes = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).subtract(dotrsvAct);
                            if (leftAfterDotRes.compareTo(BigDecimal.ZERO) < 0) leftAfterDotRes = BigDecimal.ZERO;
                            BigDecimal rrsv = values.get(13);
                            return leftAfterDotRes.compareTo(rrsv) < 0 ? leftAfterDotRes : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("RETRSVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                                    BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                                    BigDecimal heldAct = values.get(10).compareTo(BigDecimal.ZERO) > 0 ? values.get(10) : values.get(9);
                                    BigDecimal dotrsvAct = values.get(12).compareTo(BigDecimal.ZERO) > 0 ? values.get(12) : values.get(11);
                                    BigDecimal leftAfterDotRes = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).subtract(dotrsvAct);
                                    if (leftAfterDotRes.compareTo(BigDecimal.ZERO) < 0)
                                        leftAfterDotRes = BigDecimal.ZERO;
                                    BigDecimal rrsv = values.get(13);
                                    return leftAfterDotRes.compareTo(rrsv) < 0 ? leftAfterDotRes : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("RETRSVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                                    BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                                    BigDecimal heldAct = values.get(10).compareTo(BigDecimal.ZERO) > 0 ? values.get(10) : values.get(9);
                                    BigDecimal dotrsvAct = values.get(12).compareTo(BigDecimal.ZERO) > 0 ? values.get(12) : values.get(11);
                                    BigDecimal leftAfterDotRes = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).subtract(dotrsvAct);
                                    if (leftAfterDotRes.compareTo(BigDecimal.ZERO) < 0)
                                        leftAfterDotRes = BigDecimal.ZERO;
                                    BigDecimal rrsv = values.get(13);
                                    return leftAfterDotRes.compareTo(rrsv) < 0 ? leftAfterDotRes : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("AOUTBV",
                new Steps.CalculationStep("AOUTBV", List.of("DOTOUTB", "@DOTATS"),
                        values -> {
                            BigDecimal diff = values.get(0).subtract(values.get(1));
                            return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("AOUTBV", List.of("DOTOUTB", "@DOTATS"),
                                values -> {
                                    BigDecimal diff = values.get(0).subtract(values.get(1));
                                    return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("AOUTBV", List.of("DOTOUTB", "@DOTATS"),
                                values -> {
                                    BigDecimal diff = values.get(0).subtract(values.get(1));
                                    return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("AOUTBVX",
                new Steps.CalculationStep("AOUTBVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV", "RETRSVX", "AOUTBV"),
                        values -> {
                            BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                            BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                            BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                            BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                            BigDecimal heldAct = values.get(10).compareTo(BigDecimal.ZERO) > 0 ? values.get(10) : values.get(9);
                            BigDecimal dotrsvAct = values.get(12).compareTo(BigDecimal.ZERO) > 0 ? values.get(12) : values.get(11);
                            BigDecimal retrsvAct = values.get(14).compareTo(BigDecimal.ZERO) > 0 ? values.get(14) : values.get(13);
                            BigDecimal leftAfterRes = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                    .subtract(dotrsvAct).subtract(retrsvAct);
                            if (leftAfterRes.compareTo(BigDecimal.ZERO) < 0) leftAfterRes = BigDecimal.ZERO;
                            BigDecimal abv = values.get(15);
                            return leftAfterRes.compareTo(abv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("AOUTBVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV", "RETRSVX", "AOUTBV"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                                    BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                                    BigDecimal heldAct = values.get(10).compareTo(BigDecimal.ZERO) > 0 ? values.get(10) : values.get(9);
                                    BigDecimal dotrsvAct = values.get(12).compareTo(BigDecimal.ZERO) > 0 ? values.get(12) : values.get(11);
                                    BigDecimal retrsvAct = values.get(14).compareTo(BigDecimal.ZERO) > 0 ? values.get(14) : values.get(13);
                                    BigDecimal leftAfterRes = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                            .subtract(dotrsvAct).subtract(retrsvAct);
                                    if (leftAfterRes.compareTo(BigDecimal.ZERO) < 0) leftAfterRes = BigDecimal.ZERO;
                                    BigDecimal abv = values.get(15);
                                    return leftAfterRes.compareTo(abv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("AOUTBVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV", "RETRSVX", "AOUTBV"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                                    BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                                    BigDecimal heldAct = values.get(10).compareTo(BigDecimal.ZERO) > 0 ? values.get(10) : values.get(9);
                                    BigDecimal dotrsvAct = values.get(12).compareTo(BigDecimal.ZERO) > 0 ? values.get(12) : values.get(11);
                                    BigDecimal retrsvAct = values.get(14).compareTo(BigDecimal.ZERO) > 0 ? values.get(14) : values.get(13);
                                    BigDecimal leftAfterRes = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                            .subtract(dotrsvAct).subtract(retrsvAct);
                                    if (leftAfterRes.compareTo(BigDecimal.ZERO) < 0) leftAfterRes = BigDecimal.ZERO;
                                    BigDecimal abv = values.get(15);
                                    return leftAfterRes.compareTo(abv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("ANEED",
                new Steps.CalculationStep("ANEED", List.of("NEED", "@RETAILATS"),
                        values -> {
                            BigDecimal diff = values.get(0).subtract(values.get(1));
                            return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("ANEED", List.of("NEED", "@RETAILATS"),
                                values -> {
                                    BigDecimal diff = values.get(0).subtract(values.get(1));
                                    return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("ANEED", List.of("NEED", "@RETAILATS"),
                                values -> {
                                    BigDecimal diff = values.get(0).subtract(values.get(1));
                                    return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("NEEDX",
                new Steps.CalculationStep("NEEDX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV", "RETRSVX", "AOUTBV", "AOUTBVX", "ANEED"),
                        values -> {
                            BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                            BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                            BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                            BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                            BigDecimal heldAct = values.get(10).compareTo(BigDecimal.ZERO) > 0 ? values.get(10) : values.get(9);
                            BigDecimal dotrsvAct = values.get(12).compareTo(BigDecimal.ZERO) > 0 ? values.get(12) : values.get(11);
                            BigDecimal retrsvAct = values.get(14).compareTo(BigDecimal.ZERO) > 0 ? values.get(14) : values.get(13);
                            BigDecimal outbAct = values.get(16).compareTo(BigDecimal.ZERO) > 0 ? values.get(16) : values.get(15);
                            BigDecimal leftAfterOut = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                    .subtract(dotrsvAct).subtract(retrsvAct).subtract(outbAct);
                            if (leftAfterOut.compareTo(BigDecimal.ZERO) < 0) leftAfterOut = BigDecimal.ZERO;
                            BigDecimal need = values.get(17);
                            return leftAfterOut.compareTo(need) < 0 ? leftAfterOut : BigDecimal.ZERO;
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("NEEDX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV", "RETRSVX", "AOUTBV", "AOUTBVX", "ANEED"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                                    BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                                    BigDecimal heldAct = values.get(10).compareTo(BigDecimal.ZERO) > 0 ? values.get(10) : values.get(9);
                                    BigDecimal dotrsvAct = values.get(12).compareTo(BigDecimal.ZERO) > 0 ? values.get(12) : values.get(11);
                                    BigDecimal retrsvAct = values.get(14).compareTo(BigDecimal.ZERO) > 0 ? values.get(14) : values.get(13);
                                    BigDecimal outbAct = values.get(16).compareTo(BigDecimal.ZERO) > 0 ? values.get(16) : values.get(15);
                                    BigDecimal leftAfterOut = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                            .subtract(dotrsvAct).subtract(retrsvAct).subtract(outbAct);
                                    if (leftAfterOut.compareTo(BigDecimal.ZERO) < 0) leftAfterOut = BigDecimal.ZERO;
                                    BigDecimal need = values.get(17);
                                    return leftAfterOut.compareTo(need) < 0 ? leftAfterOut : BigDecimal.ZERO;
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("NEEDX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV", "RETRSVX", "AOUTBV", "AOUTBVX", "ANEED"),
                                values -> {
                                    BigDecimal dotHryAct = values.get(2).compareTo(BigDecimal.ZERO) > 0 ? values.get(2) : values.get(1);
                                    BigDecimal dotHrnAct = values.get(4).compareTo(BigDecimal.ZERO) > 0 ? values.get(4) : values.get(3);
                                    BigDecimal rethryAct = values.get(6).compareTo(BigDecimal.ZERO) > 0 ? values.get(6) : values.get(5);
                                    BigDecimal rethrnAct = values.get(8).compareTo(BigDecimal.ZERO) > 0 ? values.get(8) : values.get(7);
                                    BigDecimal heldAct = values.get(10).compareTo(BigDecimal.ZERO) > 0 ? values.get(10) : values.get(9);
                                    BigDecimal dotrsvAct = values.get(12).compareTo(BigDecimal.ZERO) > 0 ? values.get(12) : values.get(11);
                                    BigDecimal retrsvAct = values.get(14).compareTo(BigDecimal.ZERO) > 0 ? values.get(14) : values.get(13);
                                    BigDecimal outbAct = values.get(16).compareTo(BigDecimal.ZERO) > 0 ? values.get(16) : values.get(15);
                                    BigDecimal leftAfterOut = values.get(0).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                            .subtract(dotrsvAct).subtract(retrsvAct).subtract(outbAct);
                                    if (leftAfterOut.compareTo(BigDecimal.ZERO) < 0) leftAfterOut = BigDecimal.ZERO;
                                    BigDecimal need = values.get(17);
                                    return leftAfterOut.compareTo(need) < 0 ? leftAfterOut : BigDecimal.ZERO;
                                },
                                null, null, null, null)
                ),
                null, false);

        // Dynamic Snapshot Steps
        engine.addStep("@RETAILATS",
                new Steps.CalculationStep("@RETAILATS", List.of("RETHRY", "RETHRYX", "RETRSV", "RETRSVX", "ANEED", "NEEDX"),
                        values -> {
                            BigDecimal retHardYes = values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0);
                            BigDecimal retRes = values.get(3).compareTo(BigDecimal.ZERO) > 0 ? values.get(3) : values.get(2);
                            BigDecimal adjNeed = values.get(5).compareTo(BigDecimal.ZERO) > 0 ? values.get(5) : values.get(4);
                            return retHardYes.add(retRes).add(adjNeed);
                        },
                        null, null, null, null),
                Map.of(), null, true);
        engine.addStep("@DOTATS",
                new Steps.CalculationStep("@DOTATS", List.of("DOTHRY", "DOTHRYX", "DOTRSV", "DOTRSVX", "AOUTBV", "AOUTBVX"),
                        values -> {
                            BigDecimal dotHardYes = values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0);
                            BigDecimal dotRes = values.get(3).compareTo(BigDecimal.ZERO) > 0 ? values.get(3) : values.get(2);
                            BigDecimal adjOut = values.get(5).compareTo(BigDecimal.ZERO) > 0 ? values.get(5) : values.get(4);
                            return dotHardYes.add(dotRes).add(adjOut);
                        },
                        null, null, null, null),
                Map.of(), null, true);
        engine.addStep("@UNCOMMIT",
                new Steps.CalculationStep("@UNCOMMIT", List.of("INITAFS", "@COMMITTED", "@DOTHRYA", "@DOTHRNA", "@RETHRYA", "@RETHRNA", "@HLDHRA", "@DOTRSVA", "@RETRSVA", "@AOUTBVA", "@NEEDA"),
                        values -> {
                            BigDecimal init = values.get(0);
                            BigDecimal committed = values.get(1);
                            BigDecimal totalRes = BigDecimal.ZERO;
                            for (int i = 2; i < values.size(); i++) {
                                totalRes = totalRes.add(values.get(i));
                            }
                            BigDecimal result = init.subtract(committed).subtract(totalRes);
                            return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
                        },
                        null, null, null, null),
                Map.of(), null, true);
        engine.addStep("@COMMITTED",
                new Steps.CalculationStep("@COMMITTED", List.of("SNB", "DTCO", "ROHP"),
                        values -> values.get(0).add(values.get(1)).add(values.get(2)),
                        null, null, null, null),
                Map.of(), null, true);
        engine.addStep("@UNCOMMHR",
                new Steps.CalculationStep("@UNCOMMHR", List.of("DOTHRN", "DOTHRNX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX"),
                        values -> {
                            BigDecimal dotNo = values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0);
                            BigDecimal retNo = values.get(3).compareTo(BigDecimal.ZERO) > 0 ? values.get(3) : values.get(2);
                            BigDecimal heldNo = values.get(5).compareTo(BigDecimal.ZERO) > 0 ? values.get(5) : values.get(4);
                            return dotNo.add(retNo).add(heldNo);
                        },
                        null, null, null, null),
                Map.of(), null, true);

        // Final Output Calculation Steps
        engine.addStep("@OMSSUP",
                new Steps.CalculationStep("@OMSSUP", List.of("INITAFS", "@DOTATS", "DTCO", "DTCOX"),
                        values -> {
                            BigDecimal afs = values.get(0);
                            if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                            BigDecimal dtcoAct = values.get(3).compareTo(BigDecimal.ZERO) > 0 ? values.get(3) : values.get(2);
                            return values.get(1).add(dtcoAct);
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("@OMSSUP", List.of("INITAFS", "@DOTATS", "DTCO", "DTCOX", "SNB", "SNBX", "DOTHRN", "DOTHRNX"),
                                values -> {
                                    BigDecimal afs = values.get(0);
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                                    BigDecimal dtcoAct = values.get(3).compareTo(BigDecimal.ZERO) > 0 ? values.get(3) : values.get(2);
                                    BigDecimal snbAct = values.get(5).compareTo(BigDecimal.ZERO) > 0 ? values.get(5) : values.get(4);
                                    BigDecimal dhrnAct = values.get(7).compareTo(BigDecimal.ZERO) > 0 ? values.get(7) : values.get(6);
                                    return values.get(1).add(dtcoAct).add(snbAct).add(dhrnAct);
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("@OMSSUP", List.of("INITAFS", "@DOTATS", "DTCO", "DTCOX"),
                                values -> {
                                    BigDecimal afs = values.get(0);
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                                    BigDecimal dtcoAct = values.get(3).compareTo(BigDecimal.ZERO) > 0 ? values.get(3) : values.get(2);
                                    return values.get(1).add(dtcoAct);
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("@RETFINAL",
                new Steps.CalculationStep("@RETFINAL", List.of("INITAFS", "@RETAILATS"),
                        values -> {
                            BigDecimal afs = values.get(0);
                            return afs.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : values.get(1);
                        },
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("@RETFINAL", List.of("INITAFS", "@RETAILATS", "RETHRN", "RETHRNX", "ROHP", "ROHPX", "HLDHR", "HLDHRX"),
                                values -> {
                                    BigDecimal afs = values.get(0);
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return afs;
                                    BigDecimal rethrnAct = values.get(3).compareTo(BigDecimal.ZERO) > 0 ? values.get(3) : values.get(2);
                                    BigDecimal rohpAct = values.get(5).compareTo(BigDecimal.ZERO) > 0 ? values.get(5) : values.get(4);
                                    BigDecimal heldAct = values.get(7).compareTo(BigDecimal.ZERO) > 0 ? values.get(7) : values.get(6);
                                    return values.get(1).add(rethrnAct).add(rohpAct).add(heldAct);
                                },
                                null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("@RETFINAL", List.of("INITAFS", "@RETAILATS", "@AOUTBVA"),
                                values -> {
                                    BigDecimal afs = values.get(0);
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                                    return values.get(1).add(values.get(2));
                                },
                                null, null, null, null)
                ),
                null, false);
        engine.addStep("@OMSFINAL",
                new Steps.CalculationStep("@OMSFINAL", List.of("@OMSSUP"),
                        values -> BigDecimal.ZERO,
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("@OMSFINAL", List.of("@OMSSUP"),
                                values -> values.get(0),
                                null, null, null, null)
                ),
                null, false);

        // Actual Value Steps for constrained fields
        engine.addStep("@SNBA",
                new Steps.ConstraintStep("@SNBA", "SNB", "INITAFS"),
                Map.of(), null, false);
        engine.addStep("@DTCOA",
                new Steps.CalculationStep("@DTCOA", List.of("DTCO", "DTCOX"),
                        values -> values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0),
                        null, null, null, null),
                Map.of(), null, false);
        engine.addStep("@ROHPA",
                new Steps.CalculationStep("@ROHPA", List.of("ROHP", "ROHPX"),
                        values -> values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0),
                        null, null, null, null),
                Map.of(), null, false);
        engine.addStep("@DOTHRYA",
                new Steps.CalculationStep("@DOTHRYA", List.of("DOTHRY", "DOTHRYX"),
                        values -> values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0),
                        null, null, null, null),
                Map.of(), null, false);
        engine.addStep("@DOTHRNA",
                new Steps.CalculationStep("@DOTHRNA", List.of("DOTHRN", "DOTHRNX"),
                        values -> values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0),
                        null, null, null, null),
                Map.of(), null, false);
        engine.addStep("@RETHRYA",
                new Steps.CalculationStep("@RETHRYA", List.of("RETHRY", "RETHRYX"),
                        values -> values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0),
                        null, null, null, null),
                Map.of(), null, false);
        engine.addStep("@RETHRNA",
                new Steps.CalculationStep("@RETHRNA", List.of("RETHRN", "RETHRNX"),
                        values -> values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0),
                        null, null, null, null),
                Map.of(), null, false);
        engine.addStep("@HLDHRA",
                new Steps.CalculationStep("@HLDHRA", List.of("HLDHR", "HLDHRX"),
                        values -> values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0),
                        null, null, null, null),
                Map.of(), null, false);
        engine.addStep("@DOTRSVA",
                new Steps.CalculationStep("@DOTRSVA", List.of("DOTRSV", "DOTRSVX"),
                        values -> values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0),
                        null, null, null, null),
                Map.of(), null, false);
        engine.addStep("@RETRSVA",
                new Steps.CalculationStep("@RETRSVA", List.of("RETRSV", "RETRSVX"),
                        values -> values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0),
                        null, null, null, null),
                Map.of(), null, false);
        engine.addStep("@AOUTBVA",
                new Steps.CalculationStep("@AOUTBVA", List.of("AOUTBV", "AOUTBVX"),
                        values -> values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0),
                        null, null, null, null),
                Map.of(), null, false);
        engine.addStep("@NEEDA",
                new Steps.CalculationStep("@NEEDA", List.of("ANEED", "NEEDX"),
                        values -> values.get(1).compareTo(BigDecimal.ZERO) > 0 ? values.get(1) : values.get(0),
                        null, null, null, null),
                Map.of(), null, false);
    }
}