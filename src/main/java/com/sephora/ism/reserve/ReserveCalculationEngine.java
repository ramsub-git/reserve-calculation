// ReserveCalculationEngine.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;

/**
 * ReserveCalculationEngine manages flow setup and calculation execution.
 * - Holds step lists per flow.
 * - Passes control to ReserveCalcContext for step execution and snapshotting.
 */
public class ReserveCalculationEngine {

    private final Map<CalculationFlow, List<ReserveCalcStep>> flowSteps = new EnumMap<>(CalculationFlow.class);
    private final Map<String, ReserveCalcStep> contextConditionSteps = new HashMap<>();
    private final List<ReserveCalcStep> dynamicSteps = new ArrayList<>();
    private Predicate<ReserveCalcContext> enginePreCheck = ctx -> true;
    private Predicate<ReserveCalcContext> enginePostCheck = ctx -> true;

    public ReserveCalculationEngine() {
        for (CalculationFlow flow : CalculationFlow.values()) {
            flowSteps.put(flow, new ArrayList<>());
        }
    }

    public void addStep(String fieldName, ReserveCalcStep mainStep, Map<CalculationFlow, ReserveCalcStep> alternateSteps, ReserveCalcStep contextConditionStep, boolean isDynamic) {
        for (CalculationFlow flow : CalculationFlow.values()) {
            ReserveCalcStep stepForFlow = (alternateSteps != null && alternateSteps.containsKey(flow))
                    ? alternateSteps.get(flow).copy()
                    : mainStep.copy();

            stepForFlow.setFlow(flow);
            flowSteps.get(flow).add(stepForFlow);
        }

        if (contextConditionStep != null) {
            contextConditionSteps.put(fieldName, contextConditionStep);
        }

        if (isDynamic) {
            dynamicSteps.add(mainStep);
        }
    }

    public void calculate(ReserveCalcContext context) {
        if (!enginePreCheck.test(context)) {
            throw new IllegalStateException("Engine pre-check failed: Required conditions not met.");
        }

        context.setDynamicSteps(dynamicSteps);

        int maxStepCount = flowSteps.values().stream().mapToInt(List::size).max().orElse(0);

        for (int stepIndex = 0; stepIndex < maxStepCount; stepIndex++) {
            Map<CalculationFlow, ReserveCalcStep> currentSteps = new EnumMap<>(CalculationFlow.class);

            for (CalculationFlow flow : CalculationFlow.values()) {
                List<ReserveCalcStep> steps = flowSteps.get(flow);
                if (stepIndex >= steps.size()) {
                    throw new IllegalStateException("Flow " + flow + " has fewer steps than expected at position " + stepIndex);
                }
                currentSteps.put(flow, steps.get(stepIndex));
            }

            String fieldName = currentSteps.get(CalculationFlow.OMS).getFieldName();
            ReserveCalcStep contextConditionStep = contextConditionSteps.get(fieldName);

            context.calculateSteps(stepIndex, currentSteps, contextConditionStep);

            // Log after calculation
            ReserveCalculationLogger.logStepCalculation(stepIndex, currentSteps, context);


        }

        // Log final summary
        ReserveCalculationLogger.logFinalSummary(context);

        // Perform post-checks
        if (!enginePostCheck.test(context)) {
            throw new IllegalStateException("Engine post-check failed: Validation conditions not met.");
        }
    }

    // Converted setupReserveCalculationSteps method with field name lookups
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


        // Initial AFS Calculation (INITAFS) with alternate flow for JEI
        engine.addStep("INITAFS",
                new Steps.CalculationStep("INITAFS", List.of("ONHAND", "ROHM", "LOST", "OOBADJ"),
                        inputs -> inputs.get("ONHAND")
                                .subtract(inputs.get("ROHM"))
                                .subtract(inputs.get("LOST"))
                                .subtract(inputs.get("OOBADJ").max(BigDecimal.ZERO)),
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI,
                        new Steps.CalculationStep("INITAFS", List.of("ONHAND", "LOST"),
                                inputs -> inputs.get("ONHAND")
                                        .subtract(inputs.get("LOST")),
                                null, null, null, null),
                        CalculationFlow.FRM,
                        new Steps.CalculationStep("INITAFS", List.of("ONHAND", "ROHM", "LOST", "OOBADJ"),
                                inputs -> inputs.get("ONHAND")
                                        .subtract(inputs.get("ROHM"))
                                        .subtract(inputs.get("LOST"))
                                        .subtract(inputs.get("OOBADJ").max(BigDecimal.ZERO)),
                                null, null, null, null)
                ),
//                new Steps.ContextConditionStep("INITAFS", List.of(),
//                        ctx -> ctx.get("_flowResult_JEI_INITAFS").compareTo(BigDecimal.ZERO) > 0
//                                && ctx.get("_flowResult_JEI_INITAFS").compareTo(ctx.get("_flowResult_OMS_INITAFS")) != 0
//                                ? ctx.get("_flowResult_JEI_INITAFS") : ctx.get("_flowResult_OMS_INITAFS")
//                ),
                null,
                false);


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


        // Constraint and Derived Steps
        engine.addStep("SNBX",
                new Steps.CalculationStep("SNBX", List.of("INITAFS", "SNB"),
                        inputs -> {
                            BigDecimal afs = inputs.get("INITAFS");
                            BigDecimal snb = inputs.get("SNB");
                            return afs.compareTo(BigDecimal.ZERO) > 0 && snb.compareTo(afs) > 0 ? afs : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("SNBX", List.of("INITAFS", "SNB"),
                                inputs -> {
                                    BigDecimal afs = inputs.get("INITAFS");
                                    BigDecimal snb = inputs.get("SNB");
                                    return afs.compareTo(BigDecimal.ZERO) > 0 && snb.compareTo(afs) > 0 ? afs : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("SNBX", List.of("INITAFS", "SNB"),
                                inputs -> {
                                    BigDecimal afs = inputs.get("INITAFS");
                                    BigDecimal snb = inputs.get("SNB");
                                    return afs.compareTo(BigDecimal.ZERO) > 0 && snb.compareTo(afs) > 0 ? afs : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("DTCOX",
                new Steps.CalculationStep("DTCOX", List.of("INITAFS", "SNB", "DTCO"),
                        inputs -> {
                            BigDecimal afs = inputs.get("INITAFS");
                            BigDecimal snb = inputs.get("SNB");
                            BigDecimal dtco = inputs.get("DTCO");
                            BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                            return leftover1.compareTo(dtco) < 0 ? leftover1 : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("DTCOX", List.of("INITAFS", "SNB", "DTCO"),
                                inputs -> {
                                    BigDecimal afs = inputs.get("INITAFS");
                                    BigDecimal snb = inputs.get("SNB");
                                    BigDecimal dtco = inputs.get("DTCO");
                                    BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                                    return leftover1.compareTo(dtco) < 0 ? leftover1 : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("DTCOX", List.of("INITAFS", "SNB", "DTCO"),
                                inputs -> {
                                    BigDecimal afs = inputs.get("INITAFS");
                                    BigDecimal snb = inputs.get("SNB");
                                    BigDecimal dtco = inputs.get("DTCO");
                                    BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                                    return leftover1.compareTo(dtco) < 0 ? leftover1 : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("ROHPX",
                new Steps.CalculationStep("ROHPX", List.of("INITAFS", "SNB", "DTCO", "ROHP"),
                        inputs -> {
                            BigDecimal afs = inputs.get("INITAFS");
                            BigDecimal snb = inputs.get("SNB");
                            BigDecimal dtco = inputs.get("DTCO");
                            BigDecimal rohp = inputs.get("ROHP");
                            BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                            BigDecimal leftover2 = leftover1.compareTo(dtco) < 0 ? BigDecimal.ZERO : leftover1.subtract(dtco);
                            return leftover2.compareTo(rohp) < 0 ? leftover2 : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("ROHPX", List.of("INITAFS", "SNB", "DTCO", "ROHP"),
                                inputs -> {
                                    BigDecimal afs = inputs.get("INITAFS");
                                    BigDecimal snb = inputs.get("SNB");
                                    BigDecimal dtco = inputs.get("DTCO");
                                    BigDecimal rohp = inputs.get("ROHP");
                                    BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                                    BigDecimal leftover2 = leftover1.compareTo(dtco) < 0 ? BigDecimal.ZERO : leftover1.subtract(dtco);
                                    return leftover2.compareTo(rohp) < 0 ? leftover2 : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("ROHPX", List.of("INITAFS", "SNB", "DTCO", "ROHP"),
                                inputs -> {
                                    BigDecimal afs = inputs.get("INITAFS");
                                    BigDecimal snb = inputs.get("SNB");
                                    BigDecimal dtco = inputs.get("DTCO");
                                    BigDecimal rohp = inputs.get("ROHP");
                                    BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                                    BigDecimal leftover2 = leftover1.compareTo(dtco) < 0 ? BigDecimal.ZERO : leftover1.subtract(dtco);
                                    return leftover2.compareTo(rohp) < 0 ? leftover2 : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("UNCOMAFS",
                new Steps.CalculationStep("UNCOMAFS", List.of("INITAFS", "SNB", "DTCO", "ROHP"),
                        inputs -> {
                            BigDecimal result = inputs.get("INITAFS").subtract(inputs.get("SNB"))
                                    .subtract(inputs.get("DTCO")).subtract(inputs.get("ROHP"));
                            return result.max(BigDecimal.ZERO);
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("UNCOMAFS", List.of("INITAFS", "SNB", "DTCO", "ROHP"),
                                inputs -> {
                                    BigDecimal result = inputs.get("INITAFS").subtract(inputs.get("SNB"))
                                            .subtract(inputs.get("DTCO")).subtract(inputs.get("ROHP"));
                                    return result.max(BigDecimal.ZERO);
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("UNCOMAFS", List.of("INITAFS", "SNB", "DTCO", "ROHP"),
                                inputs -> {
                                    BigDecimal result = inputs.get("INITAFS").subtract(inputs.get("SNB"))
                                            .subtract(inputs.get("DTCO")).subtract(inputs.get("ROHP"));
                                    return result.max(BigDecimal.ZERO);
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("DOTHRYX",
                new Steps.CalculationStep("DOTHRYX", List.of("UNCOMAFS", "DOTHRY"),
                        inputs -> {
                            BigDecimal left = inputs.get("UNCOMAFS");
                            BigDecimal dty = inputs.get("DOTHRY");
                            return left.compareTo(dty) < 0 ? left : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("DOTHRYX", List.of("UNCOMAFS", "DOTHRY"),
                                inputs -> {
                                    BigDecimal left = inputs.get("UNCOMAFS");
                                    BigDecimal dty = inputs.get("DOTHRY");
                                    return left.compareTo(dty) < 0 ? left : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("DOTHRYX", List.of("UNCOMAFS", "DOTHRY"),
                                inputs -> {
                                    BigDecimal left = inputs.get("UNCOMAFS");
                                    BigDecimal dty = inputs.get("DOTHRY");
                                    return left.compareTo(dty) < 0 ? left : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("DOTHRNX",
                new Steps.CalculationStep("DOTHRNX", List.of("UNCOMAFS", "DOTHRY", "DOTHRN"),
                        inputs -> {
                            BigDecimal left3 = inputs.get("UNCOMAFS").subtract(inputs.get("DOTHRY")).max(BigDecimal.ZERO);
                            BigDecimal dtn = inputs.get("DOTHRN");
                            return left3.compareTo(dtn) < 0 ? left3 : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("DOTHRNX", List.of("UNCOMAFS", "DOTHRY", "DOTHRN"),
                                inputs -> {
                                    BigDecimal left3 = inputs.get("UNCOMAFS").subtract(inputs.get("DOTHRY")).max(BigDecimal.ZERO);
                                    BigDecimal dtn = inputs.get("DOTHRN");
                                    return left3.compareTo(dtn) < 0 ? left3 : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("DOTHRNX", List.of("UNCOMAFS", "DOTHRY", "DOTHRN"),
                                inputs -> {
                                    BigDecimal left3 = inputs.get("UNCOMAFS").subtract(inputs.get("DOTHRY")).max(BigDecimal.ZERO);
                                    BigDecimal dtn = inputs.get("DOTHRN");
                                    return left3.compareTo(dtn) < 0 ? left3 : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("RETHRYX",
                new Steps.CalculationStep("RETHRYX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY"),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                            BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                            BigDecimal leftAfterDot = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct).max(BigDecimal.ZERO);
                            BigDecimal rhy = inputs.get("RETHRY");
                            return leftAfterDot.compareTo(rhy) < 0 ? leftAfterDot : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("RETHRYX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal leftAfterDot = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct).max(BigDecimal.ZERO);
                                    BigDecimal rhy = inputs.get("RETHRY");
                                    return leftAfterDot.compareTo(rhy) < 0 ? leftAfterDot : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("RETHRYX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal leftAfterDot = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct).max(BigDecimal.ZERO);
                                    BigDecimal rhy = inputs.get("RETHRY");
                                    return leftAfterDot.compareTo(rhy) < 0 ? leftAfterDot : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("RETHRNX",
                new Steps.CalculationStep("RETHRNX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN"),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                            BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                            BigDecimal leftAfterDot = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct).max(BigDecimal.ZERO);
                            BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                            BigDecimal leftAfterRHY = leftAfterDot.subtract(rethryAct).max(BigDecimal.ZERO);
                            BigDecimal rhn = inputs.get("RETHRN");
                            return leftAfterRHY.compareTo(rhn) < 0 ? leftAfterRHY : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("RETHRNX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal leftAfterDot = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct).max(BigDecimal.ZERO);
                                    BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                                    BigDecimal leftAfterRHY = leftAfterDot.subtract(rethryAct).max(BigDecimal.ZERO);
                                    BigDecimal rhn = inputs.get("RETHRN");
                                    return leftAfterRHY.compareTo(rhn) < 0 ? leftAfterRHY : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("RETHRNX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal leftAfterDot = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct).max(BigDecimal.ZERO);
                                    BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                                    BigDecimal leftAfterRHY = leftAfterDot.subtract(rethryAct).max(BigDecimal.ZERO);
                                    BigDecimal rhn = inputs.get("RETHRN");
                                    return leftAfterRHY.compareTo(rhn) < 0 ? leftAfterRHY : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("HLDHRX",
                new Steps.CalculationStep("HLDHRX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR"),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                            BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                            BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                            BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                            BigDecimal leftAfterRes = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).max(BigDecimal.ZERO);
                            BigDecimal hld = inputs.get("HLDHR");
                            return leftAfterRes.compareTo(hld) < 0 ? leftAfterRes : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("HLDHRX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                                    BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                                    BigDecimal leftAfterRes = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).max(BigDecimal.ZERO);
                                    BigDecimal hld = inputs.get("HLDHR");
                                    return leftAfterRes.compareTo(hld) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("HLDHRX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                                    BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                                    BigDecimal leftAfterRes = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).max(BigDecimal.ZERO);
                                    BigDecimal hld = inputs.get("HLDHR");
                                    return leftAfterRes.compareTo(hld) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("DOTRSVX",
                new Steps.CalculationStep("DOTRSVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV"),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                            BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                            BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                            BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                            BigDecimal heldAct = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                            BigDecimal leftAfterRes = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).max(BigDecimal.ZERO);
                            BigDecimal drsv = inputs.get("DOTRSV");
                            return leftAfterRes.compareTo(drsv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("DOTRSVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                                    BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                                    BigDecimal heldAct = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                                    BigDecimal leftAfterRes = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).max(BigDecimal.ZERO);
                                    BigDecimal drsv = inputs.get("DOTRSV");
                                    return leftAfterRes.compareTo(drsv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("DOTRSVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                                    BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                                    BigDecimal heldAct = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                                    BigDecimal leftAfterRes = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).max(BigDecimal.ZERO);
                                    BigDecimal drsv = inputs.get("DOTRSV");
                                    return leftAfterRes.compareTo(drsv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("RETRSVX",
                new Steps.CalculationStep("RETRSVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV"),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                            BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                            BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                            BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                            BigDecimal heldAct = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                            BigDecimal dotrsvAct = inputs.get("DOTRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTRSVX") : inputs.get("DOTRSV");
                            BigDecimal leftAfterDotRes = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).subtract(dotrsvAct)
                                    .max(BigDecimal.ZERO);
                            BigDecimal rrsv = inputs.get("RETRSV");
                            return leftAfterDotRes.compareTo(rrsv) < 0 ? leftAfterDotRes : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("RETRSVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                                    BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                                    BigDecimal heldAct = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                                    BigDecimal dotrsvAct = inputs.get("DOTRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTRSVX") : inputs.get("DOTRSV");
                                    BigDecimal leftAfterDotRes = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).subtract(dotrsvAct)
                                            .max(BigDecimal.ZERO);
                                    BigDecimal rrsv = inputs.get("RETRSV");
                                    return leftAfterDotRes.compareTo(rrsv) < 0 ? leftAfterDotRes : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("RETRSVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                                    BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                                    BigDecimal heldAct = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                                    BigDecimal dotrsvAct = inputs.get("DOTRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTRSVX") : inputs.get("DOTRSV");
                                    BigDecimal leftAfterDotRes = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).subtract(dotrsvAct)
                                            .max(BigDecimal.ZERO);
                                    BigDecimal rrsv = inputs.get("RETRSV");
                                    return leftAfterDotRes.compareTo(rrsv) < 0 ? leftAfterDotRes : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("AOUTBV",
                new Steps.CalculationStep("AOUTBV", List.of("DOTOUTB", "@DOTATS"),
                        inputs -> {
                            BigDecimal diff = inputs.get("DOTOUTB").subtract(inputs.get("@DOTATS"));
                            return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("AOUTBV", List.of("DOTOUTB", "@DOTATS"),
                                inputs -> {
                                    BigDecimal diff = inputs.get("DOTOUTB").subtract(inputs.get("@DOTATS"));
                                    return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("AOUTBV", List.of("DOTOUTB", "@DOTATS"),
                                inputs -> {
                                    BigDecimal diff = inputs.get("DOTOUTB").subtract(inputs.get("@DOTATS"));
                                    return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("AOUTBVX",
                new Steps.CalculationStep("AOUTBVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV", "RETRSVX", "AOUTBV"),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                            BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                            BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                            BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                            BigDecimal heldAct = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                            BigDecimal dotrsvAct = inputs.get("DOTRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTRSVX") : inputs.get("DOTRSV");
                            BigDecimal retrsvAct = inputs.get("RETRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETRSVX") : inputs.get("RETRSV");
                            BigDecimal leftAfterRes = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                    .subtract(dotrsvAct).subtract(retrsvAct).max(BigDecimal.ZERO);
                            BigDecimal abv = inputs.get("AOUTBV");
                            return leftAfterRes.compareTo(abv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("AOUTBVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV", "RETRSVX", "AOUTBV"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                                    BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                                    BigDecimal heldAct = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                                    BigDecimal dotrsvAct = inputs.get("DOTRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTRSVX") : inputs.get("DOTRSV");
                                    BigDecimal retrsvAct = inputs.get("RETRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETRSVX") : inputs.get("RETRSV");
                                    BigDecimal leftAfterRes = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                            .subtract(dotrsvAct).subtract(retrsvAct).max(BigDecimal.ZERO);
                                    BigDecimal abv = inputs.get("AOUTBV");
                                    return leftAfterRes.compareTo(abv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("AOUTBVX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV", "RETRSVX", "AOUTBV"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                                    BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                                    BigDecimal heldAct = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                                    BigDecimal dotrsvAct = inputs.get("DOTRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTRSVX") : inputs.get("DOTRSV");
                                    BigDecimal retrsvAct = inputs.get("RETRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETRSVX") : inputs.get("RETRSV");
                                    BigDecimal leftAfterRes = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                            .subtract(dotrsvAct).subtract(retrsvAct).max(BigDecimal.ZERO);
                                    BigDecimal abv = inputs.get("AOUTBV");
                                    return leftAfterRes.compareTo(abv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("ANEED",
                new Steps.CalculationStep("ANEED", List.of("NEED", "@RETAILATS"),
                        inputs -> {
                            BigDecimal diff = inputs.get("NEED").subtract(inputs.get("@RETAILATS"));
                            return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("ANEED", List.of("NEED", "@RETAILATS"),
                                inputs -> {
                                    BigDecimal diff = inputs.get("NEED").subtract(inputs.get("@RETAILATS"));
                                    return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("ANEED", List.of("NEED", "@RETAILATS"),
                                inputs -> {
                                    BigDecimal diff = inputs.get("NEED").subtract(inputs.get("@RETAILATS"));
                                    return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("NEEDX",
                new Steps.CalculationStep("NEEDX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV", "RETRSVX", "AOUTBV", "AOUTBVX", "ANEED"),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                            BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                            BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                            BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                            BigDecimal heldAct = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                            BigDecimal dotrsvAct = inputs.get("DOTRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTRSVX") : inputs.get("DOTRSV");
                            BigDecimal retrsvAct = inputs.get("RETRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETRSVX") : inputs.get("RETRSV");
                            BigDecimal outbAct = inputs.get("AOUTBVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("AOUTBVX") : inputs.get("AOUTBV");
                            BigDecimal leftAfterOut = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                    .subtract(dotrsvAct).subtract(retrsvAct).subtract(outbAct).max(BigDecimal.ZERO);
                            BigDecimal need = inputs.get("ANEED");
                            return leftAfterOut.compareTo(need) < 0 ? leftAfterOut : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("NEEDX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV", "RETRSVX", "AOUTBV", "AOUTBVX", "ANEED"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                                    BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                                    BigDecimal heldAct = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                                    BigDecimal dotrsvAct = inputs.get("DOTRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTRSVX") : inputs.get("DOTRSV");
                                    BigDecimal retrsvAct = inputs.get("RETRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETRSVX") : inputs.get("RETRSV");
                                    BigDecimal outbAct = inputs.get("AOUTBVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("AOUTBVX") : inputs.get("AOUTBV");
                                    BigDecimal leftAfterOut = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                            .subtract(dotrsvAct).subtract(retrsvAct).subtract(outbAct).max(BigDecimal.ZERO);
                                    BigDecimal need = inputs.get("ANEED");
                                    return leftAfterOut.compareTo(need) < 0 ? leftAfterOut : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("NEEDX", List.of("UNCOMAFS", "DOTHRY", "DOTHRYX", "DOTHRN", "DOTHRNX", "RETHRY", "RETHRYX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX", "DOTRSV", "DOTRSVX", "RETRSV", "RETRSVX", "AOUTBV", "AOUTBVX", "ANEED"),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                                    BigDecimal dotHrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    BigDecimal rethryAct = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                                    BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                                    BigDecimal heldAct = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                                    BigDecimal dotrsvAct = inputs.get("DOTRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTRSVX") : inputs.get("DOTRSV");
                                    BigDecimal retrsvAct = inputs.get("RETRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETRSVX") : inputs.get("RETRSV");
                                    BigDecimal outbAct = inputs.get("AOUTBVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("AOUTBVX") : inputs.get("AOUTBV");
                                    BigDecimal leftAfterOut = inputs.get("UNCOMAFS").subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                            .subtract(dotrsvAct).subtract(retrsvAct).subtract(outbAct).max(BigDecimal.ZERO);
                                    BigDecimal need = inputs.get("ANEED");
                                    return leftAfterOut.compareTo(need) < 0 ? leftAfterOut : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        // Dynamic Snapshot Steps
        engine.addStep("@RETAILATS",
                new Steps.CalculationStep("@RETAILATS", List.of("RETHRY", "RETHRYX", "RETRSV", "RETRSVX", "ANEED", "NEEDX"),
                        inputs -> {
                            BigDecimal retHardYes = inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY");
                            BigDecimal retRes = inputs.get("RETRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETRSVX") : inputs.get("RETRSV");
                            BigDecimal adjNeed = inputs.get("NEEDX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("NEEDX") : inputs.get("ANEED");
                            return retHardYes.add(retRes).add(adjNeed);
                        }, null, null, null, null),
                Map.of(), null, true);

        engine.addStep("@DOTATS",
                new Steps.CalculationStep("@DOTATS", List.of("DOTHRY", "DOTHRYX", "DOTRSV", "DOTRSVX", "AOUTBV", "AOUTBVX"),
                        inputs -> {
                            BigDecimal dotHardYes = inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY");
                            BigDecimal dotRes = inputs.get("DOTRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTRSVX") : inputs.get("DOTRSV");
                            BigDecimal adjOut = inputs.get("AOUTBVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("AOUTBVX") : inputs.get("AOUTBV");
                            return dotHardYes.add(dotRes).add(adjOut);
                        }, null, null, null, null),
                Map.of(), null, true);

        engine.addStep("@UNCOMMIT",
                new Steps.CalculationStep("@UNCOMMIT", List.of("INITAFS", "@COMMITTED", "@DOTHRYA", "@DOTHRNA", "@RETHRYA", "@RETHRNA", "@HLDHRA", "@DOTRSVA", "@RETRSVA", "@AOUTBVA", "@NEEDA"),
                        inputs -> {
                            BigDecimal init = inputs.get("INITAFS");
                            BigDecimal committed = inputs.get("@COMMITTED");
                            BigDecimal totalRes = BigDecimal.ZERO;
                            // Sum all the reserve fields
                            totalRes = totalRes.add(inputs.get("@DOTHRYA"))
                                    .add(inputs.get("@DOTHRNA"))
                                    .add(inputs.get("@RETHRYA"))
                                    .add(inputs.get("@RETHRNA"))
                                    .add(inputs.get("@HLDHRA"))
                                    .add(inputs.get("@DOTRSVA"))
                                    .add(inputs.get("@RETRSVA"))
                                    .add(inputs.get("@AOUTBVA"))
                                    .add(inputs.get("@NEEDA"));
                            BigDecimal result = init.subtract(committed).subtract(totalRes);
                            return result.max(BigDecimal.ZERO);
                        }, null, null, null, null),
                Map.of(), null, true);

        engine.addStep("@COMMITTED",
                new Steps.CalculationStep("@COMMITTED", List.of("SNB", "DTCO", "ROHP"),
                        inputs -> inputs.get("SNB").add(inputs.get("DTCO")).add(inputs.get("ROHP")),
                        null, null, null, null),
                Map.of(), null, true);

        engine.addStep("@UNCOMMHR",
                new Steps.CalculationStep("@UNCOMMHR", List.of("DOTHRN", "DOTHRNX", "RETHRN", "RETHRNX", "HLDHR", "HLDHRX"),
                        inputs -> {
                            BigDecimal dotNo = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                            BigDecimal retNo = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                            BigDecimal heldNo = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                            return dotNo.add(retNo).add(heldNo);
                        }, null, null, null, null),
                Map.of(), null, true);

        // Final Output Calculation Steps
        engine.addStep("@OMSSUP",
                new Steps.CalculationStep("@OMSSUP", List.of("INITAFS", "@DOTATS", "DTCO", "DTCOX"),
                        inputs -> {
                            BigDecimal afs = inputs.get("INITAFS");
                            if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                            BigDecimal dtcoAct = inputs.get("DTCOX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DTCOX") : inputs.get("DTCO");
                            return inputs.get("@DOTATS").add(dtcoAct);
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("@OMSSUP", List.of("INITAFS", "@DOTATS", "DTCO", "DTCOX", "SNB", "SNBX", "DOTHRN", "DOTHRNX"),
                                inputs -> {
                                    BigDecimal afs = inputs.get("INITAFS");
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                                    BigDecimal dtcoAct = inputs.get("DTCOX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DTCOX") : inputs.get("DTCO");
                                    BigDecimal snbAct = inputs.get("SNBX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("SNBX") : inputs.get("SNB");
                                    BigDecimal dhrnAct = inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN");
                                    return inputs.get("@DOTATS").add(dtcoAct).add(snbAct).add(dhrnAct);
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("@OMSSUP", List.of("INITAFS", "@DOTATS", "DTCO", "DTCOX"),
                                inputs -> {
                                    BigDecimal afs = inputs.get("INITAFS");
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                                    BigDecimal dtcoAct = inputs.get("DTCOX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DTCOX") : inputs.get("DTCO");
                                    return inputs.get("@DOTATS").add(dtcoAct);
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("@RETFINAL",
                new Steps.CalculationStep("@RETFINAL", List.of("INITAFS", "@RETAILATS"),
                        inputs -> {
                            BigDecimal afs = inputs.get("INITAFS");
                            return afs.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : inputs.get("@RETAILATS");
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("@RETFINAL", List.of("INITAFS", "@RETAILATS", "RETHRN", "RETHRNX", "ROHP", "ROHPX", "HLDHR", "HLDHRX"),
                                inputs -> {
                                    BigDecimal afs = inputs.get("INITAFS");
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return afs;
                                    BigDecimal rethrnAct = inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN");
                                    BigDecimal rohpAct = inputs.get("ROHPX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("ROHPX") : inputs.get("ROHP");
                                    BigDecimal heldAct = inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR");
                                    return inputs.get("@RETAILATS").add(rethrnAct).add(rohpAct).add(heldAct);
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep("@RETFINAL", List.of("INITAFS", "@RETAILATS", "@AOUTBVA"),
                                inputs -> {
                                    BigDecimal afs = inputs.get("INITAFS");
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                                    return inputs.get("@RETAILATS").add(inputs.get("@AOUTBVA"));
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep("@OMSFINAL",
                new Steps.CalculationStep("@OMSFINAL", List.of("@OMSSUP"),
                        inputs -> BigDecimal.ZERO,
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep("@OMSFINAL", List.of("@OMSSUP"),
                                inputs -> inputs.get("@OMSSUP"),
                                null, null, null, null)
                ),
                null, false);

        // Actual Value Steps for constrained fields
        engine.addStep("@SNBA",
                new Steps.ConstraintStep("@SNBA", "SNB", "INITAFS"),
                Map.of(), null, false);

        engine.addStep("@DTCOA",
                new Steps.CalculationStep("@DTCOA", List.of("DTCO", "DTCOX"),
                        inputs -> inputs.get("DTCOX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DTCOX") : inputs.get("DTCO"),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep("@ROHPA",
                new Steps.CalculationStep("@ROHPA", List.of("ROHP", "ROHPX"),
                        inputs -> inputs.get("ROHPX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("ROHPX") : inputs.get("ROHP"),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep("@DOTHRYA",
                new Steps.CalculationStep("@DOTHRYA", List.of("DOTHRY", "DOTHRYX"),
                        inputs -> inputs.get("DOTHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRYX") : inputs.get("DOTHRY"),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep("@DOTHRNA",
                new Steps.CalculationStep("@DOTHRNA", List.of("DOTHRN", "DOTHRNX"),
                        inputs -> inputs.get("DOTHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTHRNX") : inputs.get("DOTHRN"),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep("@RETHRYA",
                new Steps.CalculationStep("@RETHRYA", List.of("RETHRY", "RETHRYX"),
                        inputs -> inputs.get("RETHRYX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRYX") : inputs.get("RETHRY"),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep("@RETHRNA",
                new Steps.CalculationStep("@RETHRNA", List.of("RETHRN", "RETHRNX"),
                        inputs -> inputs.get("RETHRNX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETHRNX") : inputs.get("RETHRN"),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep("@HLDHRA",
                new Steps.CalculationStep("@HLDHRA", List.of("HLDHR", "HLDHRX"),
                        inputs -> inputs.get("HLDHRX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("HLDHRX") : inputs.get("HLDHR"),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep("@DOTRSVA",
                new Steps.CalculationStep("@DOTRSVA", List.of("DOTRSV", "DOTRSVX"),
                        inputs -> inputs.get("DOTRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("DOTRSVX") : inputs.get("DOTRSV"),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep("@RETRSVA",
                new Steps.CalculationStep("@RETRSVA", List.of("RETRSV", "RETRSVX"),
                        inputs -> inputs.get("RETRSVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("RETRSVX") : inputs.get("RETRSV"),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep("@AOUTBVA",
                new Steps.CalculationStep("@AOUTBVA", List.of("AOUTBV", "AOUTBVX"),
                        inputs -> inputs.get("AOUTBVX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("AOUTBVX") : inputs.get("AOUTBV"),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep("@NEEDA",
                new Steps.CalculationStep("@NEEDA", List.of("ANEED", "NEEDX"),
                        inputs -> inputs.get("NEEDX").compareTo(BigDecimal.ZERO) > 0 ? inputs.get("NEEDX") : inputs.get("ANEED"),
                        null, null, null, null),
                Map.of(), null, false);
    }
//    private void alignFlowStepSizes() {
//        int maxSize = flowSteps.values().stream().mapToInt(List::size).max().orElse(0);
//        for (Map.Entry<CalculationFlow, List<ReserveCalcStep>> entry : flowSteps.entrySet()) {
//            List<ReserveCalcStep> stepList = entry.getValue();
//            while (stepList.size() < maxSize) {
//                stepList.add(new ReserveCalcStep("noop", Collections.emptyList(), null, null, null, null));
//            }
//        }
//    }


}