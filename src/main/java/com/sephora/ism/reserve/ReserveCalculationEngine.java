// ReserveCalculationEngine.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;

import static com.sephora.ism.reserve.ReserveField.*;

/**
 * ReserveCalculationEngine manages flow setup and calculation execution.
 * - Holds step lists per flow.
 * - Passes control to ReserveCalcContext for step execution and snapshotting.
 */
public class ReserveCalculationEngine {

    private final Map<CalculationFlow, List<ReserveCalcStep>> flowSteps = new EnumMap<>(CalculationFlow.class);
    private final Map<ReserveField, ReserveCalcStep> contextConditionSteps = new HashMap<>();
    private final List<ReserveCalcStep> dynamicSteps = new ArrayList<>();
    private Predicate<ReserveCalcContext> enginePreCheck = ctx -> true;
    private Predicate<ReserveCalcContext> enginePostCheck = ctx -> true;

    public ReserveCalculationEngine() {
        for (CalculationFlow flow : CalculationFlow.values()) {
            flowSteps.put(flow, new ArrayList<>());
        }
    }

    public void addStep(ReserveField fieldName, ReserveCalcStep mainStep, Map<CalculationFlow, ReserveCalcStep> alternateSteps, ReserveCalcStep contextConditionStep, boolean isDynamic) {
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

            ReserveField fieldName = currentSteps.get(CalculationFlow.OMS).getFieldName();
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
        engine.addStep(DIV,
                new Steps.ConstantStep(DIV, new BigDecimal("30")),
                Map.of(), null, false);

        // Base SKULOC Input Fields
        engine.addStep(ReserveField.BYCL,
                new Steps.SkulocFieldStep(ReserveField.BYCL),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.BYCL),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.BYCL)),
                null, false);

        engine.addStep(ReserveField.SKUSTS,
                new Steps.SkulocFieldStep(ReserveField.SKUSTS),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.SKUSTS),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.SKUSTS)),
                null, false);

        engine.addStep(ReserveField.ONHAND,
                new Steps.SkulocFieldStep(ReserveField.ONHAND),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.ONHAND),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.ONHAND)),
                null, false);

        engine.addStep(ReserveField.ROHM,
                new Steps.SkulocFieldStep(ReserveField.ROHM),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.ROHM),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.ROHM)),
                null, false);

        engine.addStep(ReserveField.LOST,
                new Steps.SkulocFieldStep(ReserveField.LOST),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.LOST),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.LOST)),
                null, false);

        engine.addStep(ReserveField.OOBADJ,
                new Steps.SkulocFieldStep(ReserveField.OOBADJ),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.OOBADJ),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.OOBADJ)),
                null, false);


        // Initial AFS Calculation (INITAFS) with alternate flow for JEI
        engine.addStep(ReserveField.INITAFS,
                new Steps.CalculationStep(ReserveField.INITAFS, List.of(ReserveField.ONHAND, ReserveField.ROHM, ReserveField.LOST, ReserveField.OOBADJ),
                        inputs -> inputs.get(ReserveField.ONHAND)
                                .subtract(inputs.get(ReserveField.ROHM))
                                .subtract(inputs.get(ReserveField.LOST))
                                .subtract(inputs.get(ReserveField.OOBADJ).max(BigDecimal.ZERO)),
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI,
                        new Steps.CalculationStep(ReserveField.INITAFS, List.of(ReserveField.ONHAND, ReserveField.LOST),
                                inputs -> inputs.get(ReserveField.ONHAND)
                                        .subtract(inputs.get(ReserveField.LOST)),
                                null, null, null, null),
                        CalculationFlow.FRM,
                        new Steps.CalculationStep(ReserveField.INITAFS, List.of(ReserveField.ONHAND, ReserveField.ROHM, ReserveField.LOST, ReserveField.OOBADJ),
                                inputs -> inputs.get(ReserveField.ONHAND)
                                        .subtract(inputs.get(ReserveField.ROHM))
                                        .subtract(inputs.get(ReserveField.LOST))
                                        .subtract(inputs.get(ReserveField.OOBADJ).max(BigDecimal.ZERO)),
                                null, null, null, null)
                ),
//                new Steps.ContextConditionStep("INITAFS", List.of(),
//                        ctx -> ctx.get("_flowResult_JEI_INITAFS").compareTo(BigDecimal.ZERO) > 0
//                                && ctx.get("_flowResult_JEI_INITAFS").compareTo(ctx.get("_flowResult_OMS_INITAFS")) != 0
//                                ? ctx.get("_flowResult_JEI_INITAFS") : ctx.get("_flowResult_OMS_INITAFS")
//                ),
                null,
                false);


        engine.addStep(ReserveField.SNB,
                new Steps.SkulocFieldStep(ReserveField.SNB),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.SNB),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.SNB)),
                null, false);

        engine.addStep(ReserveField.DTCO,
                new Steps.SkulocFieldStep(ReserveField.DTCO),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.DTCO),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.DTCO)),
                null, false);

        engine.addStep(ReserveField.ROHP,
                new Steps.SkulocFieldStep(ReserveField.ROHP),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.ROHP),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.ROHP)),
                null, false);

        engine.addStep(ReserveField.DOTHRY,
                new Steps.SkulocFieldStep(ReserveField.DOTHRY),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.DOTHRY),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.DOTHRY)),
                null, false);

        engine.addStep(ReserveField.DOTHRN,
                new Steps.SkulocFieldStep(ReserveField.DOTHRN),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.DOTHRN),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.DOTHRN)),
                null, false);

        engine.addStep(ReserveField.RETHRY,
                new Steps.SkulocFieldStep(ReserveField.RETHRY),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.RETHRY),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.RETHRY)),
                null, false);

        engine.addStep(ReserveField.RETHRN,
                new Steps.SkulocFieldStep(ReserveField.RETHRN),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.RETHRN),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.RETHRN)),
                null, false);

        engine.addStep(ReserveField.HLDHR,
                new Steps.SkulocFieldStep(ReserveField.HLDHR),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.HLDHR),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.HLDHR)),
                null, false);

        engine.addStep(ReserveField.DOTRSV,
                new Steps.SkulocFieldStep(ReserveField.DOTRSV),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.DOTRSV),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.DOTRSV)),
                null, false);

        engine.addStep(ReserveField.RETRSV,
                new Steps.SkulocFieldStep(ReserveField.RETRSV),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.RETRSV),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.RETRSV)),
                null, false);

        engine.addStep(ReserveField.DOTOUTB,
                new Steps.SkulocFieldStep(ReserveField.DOTOUTB),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.DOTOUTB),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.DOTOUTB)),
                null, false);

        engine.addStep(ReserveField.NEED,
                new Steps.SkulocFieldStep(ReserveField.NEED),
                Map.of(CalculationFlow.JEI, new Steps.SkulocFieldStep(ReserveField.NEED),
                        CalculationFlow.FRM, new Steps.SkulocFieldStep(ReserveField.NEED)),
                null, false);


        // Constraint and Derived Steps
        engine.addStep(ReserveField.SNBX,
                new Steps.CalculationStep(ReserveField.SNBX, List.of(ReserveField.INITAFS, ReserveField.SNB),
                        inputs -> {
                            BigDecimal afs = inputs.get(ReserveField.INITAFS);
                            BigDecimal snb = inputs.get(ReserveField.SNB);
                            return afs.compareTo(BigDecimal.ZERO) > 0 && snb.compareTo(afs) > 0 ? afs : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.SNBX, List.of(ReserveField.INITAFS, ReserveField.SNB),
                                inputs -> {
                                    BigDecimal afs = inputs.get(ReserveField.INITAFS);
                                    BigDecimal snb = inputs.get(ReserveField.SNB);
                                    return afs.compareTo(BigDecimal.ZERO) > 0 && snb.compareTo(afs) > 0 ? afs : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.SNBX, List.of(ReserveField.INITAFS, ReserveField.SNB),
                                inputs -> {
                                    BigDecimal afs = inputs.get(ReserveField.INITAFS);
                                    BigDecimal snb = inputs.get(ReserveField.SNB);
                                    return afs.compareTo(BigDecimal.ZERO) > 0 && snb.compareTo(afs) > 0 ? afs : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.DTCOX,
                new Steps.CalculationStep(ReserveField.DTCOX, List.of(ReserveField.INITAFS, ReserveField.SNB, ReserveField.DTCO),
                        inputs -> {
                            BigDecimal afs = inputs.get(ReserveField.INITAFS);
                            BigDecimal snb = inputs.get(ReserveField.SNB);
                            BigDecimal dtco = inputs.get(ReserveField.DTCO);
                            BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                            return leftover1.compareTo(dtco) < 0 ? leftover1 : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.DTCOX, List.of(ReserveField.INITAFS, ReserveField.SNB, ReserveField.DTCO),
                                inputs -> {
                                    BigDecimal afs = inputs.get(ReserveField.INITAFS);
                                    BigDecimal snb = inputs.get(ReserveField.SNB);
                                    BigDecimal dtco = inputs.get(ReserveField.DTCO);
                                    BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                                    return leftover1.compareTo(dtco) < 0 ? leftover1 : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.DTCOX, List.of(ReserveField.INITAFS, ReserveField.SNB, ReserveField.DTCO),
                                inputs -> {
                                    BigDecimal afs = inputs.get(ReserveField.INITAFS);
                                    BigDecimal snb = inputs.get(ReserveField.SNB);
                                    BigDecimal dtco = inputs.get(ReserveField.DTCO);
                                    BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                                    return leftover1.compareTo(dtco) < 0 ? leftover1 : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.ROHPX,
                new Steps.CalculationStep(ReserveField.ROHPX, List.of(ReserveField.INITAFS, ReserveField.SNB, ReserveField.DTCO, ReserveField.ROHP),
                        inputs -> {
                            BigDecimal afs = inputs.get(ReserveField.INITAFS);
                            BigDecimal snb = inputs.get(ReserveField.SNB);
                            BigDecimal dtco = inputs.get(ReserveField.DTCO);
                            BigDecimal rohp = inputs.get(ReserveField.ROHP);
                            BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                            BigDecimal leftover2 = leftover1.compareTo(dtco) < 0 ? BigDecimal.ZERO : leftover1.subtract(dtco);
                            return leftover2.compareTo(rohp) < 0 ? leftover2 : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.ROHPX, List.of(ReserveField.INITAFS, ReserveField.SNB, ReserveField.DTCO, ReserveField.ROHP),
                                inputs -> {
                                    BigDecimal afs = inputs.get(ReserveField.INITAFS);
                                    BigDecimal snb = inputs.get(ReserveField.SNB);
                                    BigDecimal dtco = inputs.get(ReserveField.DTCO);
                                    BigDecimal rohp = inputs.get(ReserveField.ROHP);
                                    BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                                    BigDecimal leftover2 = leftover1.compareTo(dtco) < 0 ? BigDecimal.ZERO : leftover1.subtract(dtco);
                                    return leftover2.compareTo(rohp) < 0 ? leftover2 : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.ROHPX, List.of(ReserveField.INITAFS, ReserveField.SNB, ReserveField.DTCO, ReserveField.ROHP),
                                inputs -> {
                                    BigDecimal afs = inputs.get(ReserveField.INITAFS);
                                    BigDecimal snb = inputs.get(ReserveField.SNB);
                                    BigDecimal dtco = inputs.get(ReserveField.DTCO);
                                    BigDecimal rohp = inputs.get(ReserveField.ROHP);
                                    BigDecimal leftover1 = afs.subtract(snb).max(BigDecimal.ZERO);
                                    BigDecimal leftover2 = leftover1.compareTo(dtco) < 0 ? BigDecimal.ZERO : leftover1.subtract(dtco);
                                    return leftover2.compareTo(rohp) < 0 ? leftover2 : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.UNCOMAFS,
                new Steps.CalculationStep(ReserveField.UNCOMAFS, List.of(ReserveField.INITAFS, ReserveField.SNB, ReserveField.DTCO, ReserveField.ROHP),
                        inputs -> {
                            BigDecimal result = inputs.get(ReserveField.INITAFS).subtract(inputs.get(ReserveField.SNB))
                                    .subtract(inputs.get(ReserveField.DTCO)).subtract(inputs.get(ReserveField.ROHP));
                            return result.max(BigDecimal.ZERO);
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.UNCOMAFS, List.of(ReserveField.INITAFS, ReserveField.SNB, ReserveField.DTCO, ReserveField.ROHP),
                                inputs -> {
                                    BigDecimal result = inputs.get(ReserveField.INITAFS).subtract(inputs.get(ReserveField.SNB))
                                            .subtract(inputs.get(ReserveField.DTCO)).subtract(inputs.get(ReserveField.ROHP));
                                    return result.max(BigDecimal.ZERO);
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.UNCOMAFS, List.of(ReserveField.INITAFS, ReserveField.SNB, ReserveField.DTCO, ReserveField.ROHP),
                                inputs -> {
                                    BigDecimal result = inputs.get(ReserveField.INITAFS).subtract(inputs.get(ReserveField.SNB))
                                            .subtract(inputs.get(ReserveField.DTCO)).subtract(inputs.get(ReserveField.ROHP));
                                    return result.max(BigDecimal.ZERO);
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.DOTHRYX,
                new Steps.CalculationStep(ReserveField.DOTHRYX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY),
                        inputs -> {
                            BigDecimal left = inputs.get(ReserveField.UNCOMAFS);
                            BigDecimal dty = inputs.get(ReserveField.DOTHRY);
                            return left.compareTo(dty) < 0 ? left : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.DOTHRYX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY),
                                inputs -> {
                                    BigDecimal left = inputs.get(ReserveField.UNCOMAFS);
                                    BigDecimal dty = inputs.get(ReserveField.DOTHRY);
                                    return left.compareTo(dty) < 0 ? left : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.DOTHRYX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY),
                                inputs -> {
                                    BigDecimal left = inputs.get(ReserveField.UNCOMAFS);
                                    BigDecimal dty = inputs.get(ReserveField.DOTHRY);
                                    return left.compareTo(dty) < 0 ? left : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.DOTHRNX,
                new Steps.CalculationStep(ReserveField.DOTHRNX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRN),
                        inputs -> {
                            BigDecimal left3 = inputs.get(ReserveField.UNCOMAFS).subtract(inputs.get(ReserveField.DOTHRY)).max(BigDecimal.ZERO);
                            BigDecimal dtn = inputs.get(ReserveField.DOTHRN);
                            return left3.compareTo(dtn) < 0 ? left3 : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.DOTHRNX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRN),
                                inputs -> {
                                    BigDecimal left3 = inputs.get(ReserveField.UNCOMAFS).subtract(inputs.get(ReserveField.DOTHRY)).max(BigDecimal.ZERO);
                                    BigDecimal dtn = inputs.get(ReserveField.DOTHRN);
                                    return left3.compareTo(dtn) < 0 ? left3 : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.DOTHRNX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRN),
                                inputs -> {
                                    BigDecimal left3 = inputs.get(ReserveField.UNCOMAFS).subtract(inputs.get(ReserveField.DOTHRY)).max(BigDecimal.ZERO);
                                    BigDecimal dtn = inputs.get(ReserveField.DOTHRN);
                                    return left3.compareTo(dtn) < 0 ? left3 : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.RETHRYX,
                new Steps.CalculationStep(ReserveField.RETHRYX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                            BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                            BigDecimal leftAfterDot = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct).max(BigDecimal.ZERO);
                            BigDecimal rhy = inputs.get(ReserveField.RETHRY);
                            return leftAfterDot.compareTo(rhy) < 0 ? leftAfterDot : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.RETHRYX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal leftAfterDot = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct).max(BigDecimal.ZERO);
                                    BigDecimal rhy = inputs.get(ReserveField.RETHRY);
                                    return leftAfterDot.compareTo(rhy) < 0 ? leftAfterDot : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.RETHRYX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal leftAfterDot = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct).max(BigDecimal.ZERO);
                                    BigDecimal rhy = inputs.get(ReserveField.RETHRY);
                                    return leftAfterDot.compareTo(rhy) < 0 ? leftAfterDot : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.RETHRNX,
                new Steps.CalculationStep(ReserveField.RETHRNX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                            BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                            BigDecimal leftAfterDot = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct).max(BigDecimal.ZERO);
                            BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                            BigDecimal leftAfterRHY = leftAfterDot.subtract(rethryAct).max(BigDecimal.ZERO);
                            BigDecimal rhn = inputs.get(ReserveField.RETHRN);
                            return leftAfterRHY.compareTo(rhn) < 0 ? leftAfterRHY : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.RETHRNX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal leftAfterDot = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct).max(BigDecimal.ZERO);
                                    BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                                    BigDecimal leftAfterRHY = leftAfterDot.subtract(rethryAct).max(BigDecimal.ZERO);
                                    BigDecimal rhn = inputs.get(ReserveField.RETHRN);
                                    return leftAfterRHY.compareTo(rhn) < 0 ? leftAfterRHY : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.RETHRNX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal leftAfterDot = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct).max(BigDecimal.ZERO);
                                    BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                                    BigDecimal leftAfterRHY = leftAfterDot.subtract(rethryAct).max(BigDecimal.ZERO);
                                    BigDecimal rhn = inputs.get(ReserveField.RETHRN);
                                    return leftAfterRHY.compareTo(rhn) < 0 ? leftAfterRHY : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.HLDHRX,
                new Steps.CalculationStep(ReserveField.HLDHRX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                            BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                            BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                            BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                            BigDecimal leftAfterRes = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).max(BigDecimal.ZERO);
                            BigDecimal hld = inputs.get(ReserveField.HLDHR);
                            return leftAfterRes.compareTo(hld) < 0 ? leftAfterRes : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.HLDHRX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                                    BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                                    BigDecimal leftAfterRes = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).max(BigDecimal.ZERO);
                                    BigDecimal hld = inputs.get(ReserveField.HLDHR);

                                    return leftAfterRes.compareTo(hld) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.HLDHRX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                                    BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                                    BigDecimal leftAfterRes = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).max(BigDecimal.ZERO);
                                    BigDecimal hld = inputs.get(ReserveField.HLDHR);
                                    return leftAfterRes.compareTo(hld) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.DOTRSVX,
                new Steps.CalculationStep(ReserveField.DOTRSVX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR, ReserveField.HLDHRX, ReserveField.DOTRSV),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                            BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                            BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                            BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                            BigDecimal heldAct = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                            BigDecimal leftAfterRes = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).max(BigDecimal.ZERO);
                            BigDecimal drsv = inputs.get(ReserveField.DOTRSV);
                            return leftAfterRes.compareTo(drsv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.DOTRSVX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR, ReserveField.HLDHRX, ReserveField.DOTRSV),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                                    BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                                    BigDecimal heldAct = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                                    BigDecimal leftAfterRes = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).max(BigDecimal.ZERO);
                                    BigDecimal drsv = inputs.get(ReserveField.DOTRSV);
                                    return leftAfterRes.compareTo(drsv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.DOTRSVX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR, ReserveField.HLDHRX, ReserveField.DOTRSV),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                                    BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                                    BigDecimal heldAct = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                                    BigDecimal leftAfterRes = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).max(BigDecimal.ZERO);
                                    BigDecimal drsv = inputs.get(ReserveField.DOTRSV);
                                    return leftAfterRes.compareTo(drsv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.RETRSVX,
                new Steps.CalculationStep(ReserveField.RETRSVX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR, ReserveField.HLDHRX, ReserveField.DOTRSV, ReserveField.DOTRSVX, ReserveField.RETRSV),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                            BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                            BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                            BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                            BigDecimal heldAct = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                            BigDecimal dotrsvAct = inputs.get(ReserveField.DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTRSVX) : inputs.get(ReserveField.DOTRSV);
                            BigDecimal leftAfterDotRes = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).subtract(dotrsvAct)
                                    .max(BigDecimal.ZERO);
                            BigDecimal rrsv = inputs.get(ReserveField.RETRSV);
                            return leftAfterDotRes.compareTo(rrsv) < 0 ? leftAfterDotRes : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.RETRSVX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR, ReserveField.HLDHRX, ReserveField.DOTRSV, ReserveField.DOTRSVX, ReserveField.RETRSV),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                                    BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                                    BigDecimal heldAct = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                                    BigDecimal dotrsvAct = inputs.get(ReserveField.DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTRSVX) : inputs.get(ReserveField.DOTRSV);
                                    BigDecimal leftAfterDotRes = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).subtract(dotrsvAct)
                                            .max(BigDecimal.ZERO);
                                    BigDecimal rrsv = inputs.get(ReserveField.RETRSV);
                                    return leftAfterDotRes.compareTo(rrsv) < 0 ? leftAfterDotRes : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.RETRSVX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR, ReserveField.HLDHRX, ReserveField.DOTRSV, ReserveField.DOTRSVX, ReserveField.RETRSV),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                                    BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                                    BigDecimal heldAct = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                                    BigDecimal dotrsvAct = inputs.get(ReserveField.DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTRSVX) : inputs.get(ReserveField.DOTRSV);
                                    BigDecimal leftAfterDotRes = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct).subtract(dotrsvAct)
                                            .max(BigDecimal.ZERO);
                                    BigDecimal rrsv = inputs.get(ReserveField.RETRSV);
                                    return leftAfterDotRes.compareTo(rrsv) < 0 ? leftAfterDotRes : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.AOUTBV,
                new Steps.CalculationStep(ReserveField.AOUTBV, List.of(ReserveField.DOTOUTB, ReserveField.DOTATS),
                        inputs -> {
                            BigDecimal diff = inputs.get(ReserveField.DOTOUTB).subtract(inputs.get(ReserveField.DOTATS));
                            return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.AOUTBV, List.of(ReserveField.DOTOUTB, ReserveField.DOTATS),
                                inputs -> {
                                    BigDecimal diff = inputs.get(ReserveField.DOTOUTB).subtract(inputs.get(ReserveField.DOTATS));
                                    return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.AOUTBV, List.of(ReserveField.DOTOUTB, ReserveField.DOTATS),
                                inputs -> {
                                    BigDecimal diff = inputs.get(ReserveField.DOTOUTB).subtract(inputs.get(ReserveField.DOTATS));
                                    return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.AOUTBVX,
                new Steps.CalculationStep(ReserveField.AOUTBVX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR, ReserveField.HLDHRX, ReserveField.DOTRSV, ReserveField.DOTRSVX, ReserveField.RETRSV, ReserveField.RETRSVX, ReserveField.AOUTBV),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRY) : inputs.get(ReserveField.DOTHRY);
                            BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                            BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                            BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                            BigDecimal heldAct = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                            BigDecimal dotrsvAct = inputs.get(ReserveField.DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTRSVX) : inputs.get(ReserveField.DOTRSV);
                            BigDecimal retrsvAct = inputs.get(ReserveField.RETRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETRSVX) : inputs.get(ReserveField.RETRSV);
                            BigDecimal leftAfterRes = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                    .subtract(dotrsvAct).subtract(retrsvAct).max(BigDecimal.ZERO);
                            BigDecimal abv = inputs.get(ReserveField.AOUTBV);
                            return leftAfterRes.compareTo(abv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.AOUTBVX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR, ReserveField.HLDHRX, ReserveField.DOTRSV, ReserveField.DOTRSVX, ReserveField.RETRSV, ReserveField.RETRSVX, ReserveField.AOUTBV),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                                    BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                                    BigDecimal heldAct = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                                    BigDecimal dotrsvAct = inputs.get(ReserveField.DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTRSVX) : inputs.get(ReserveField.DOTRSV);
                                    BigDecimal retrsvAct = inputs.get(ReserveField.RETRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETRSVX) : inputs.get(ReserveField.RETRSV);
                                    BigDecimal leftAfterRes = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                            .subtract(dotrsvAct).subtract(retrsvAct).max(BigDecimal.ZERO);
                                    BigDecimal abv = inputs.get(ReserveField.AOUTBV);
                                    return leftAfterRes.compareTo(abv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.AOUTBVX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR, ReserveField.HLDHRX, ReserveField.DOTRSV, ReserveField.DOTRSVX, ReserveField.RETRSV, ReserveField.RETRSVX, ReserveField.AOUTBV),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                                    BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                                    BigDecimal heldAct = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                                    BigDecimal dotrsvAct = inputs.get(ReserveField.DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTRSVX) : inputs.get(ReserveField.DOTRSV);
                                    BigDecimal retrsvAct = inputs.get(ReserveField.RETRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETRSVX) : inputs.get(ReserveField.RETRSV);
                                    BigDecimal leftAfterRes = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                            .subtract(dotrsvAct).subtract(retrsvAct).max(BigDecimal.ZERO);
                                    BigDecimal abv = inputs.get(ReserveField.AOUTBV);
                                    return leftAfterRes.compareTo(abv) < 0 ? leftAfterRes : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.ANEED,
                new Steps.CalculationStep(ReserveField.ANEED, List.of(ReserveField.NEED, ReserveField.RETAILATS),
                        inputs -> {
                            BigDecimal diff = inputs.get(ReserveField.NEED).subtract(inputs.get(ReserveField.RETAILATS));
                            return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.ANEED, List.of(ReserveField.NEED, ReserveField.RETAILATS),
                                inputs -> {
                                    BigDecimal diff = inputs.get(ReserveField.NEED).subtract(inputs.get(ReserveField.RETAILATS));
                                    return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.ANEED, List.of(ReserveField.NEED, ReserveField.RETAILATS),
                                inputs -> {
                                    BigDecimal diff = inputs.get(ReserveField.NEED).subtract(inputs.get(ReserveField.RETAILATS));
                                    return diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.NEEDX,
                new Steps.CalculationStep(ReserveField.NEEDX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR, ReserveField.HLDHRX, ReserveField.DOTRSV, ReserveField.DOTRSVX, ReserveField.RETRSV, ReserveField.RETRSVX, ReserveField.AOUTBV, ReserveField.AOUTBVX, ReserveField.ANEED),
                        inputs -> {
                            BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                            BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                            BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                            BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                            BigDecimal heldAct = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                            BigDecimal dotrsvAct = inputs.get(ReserveField.DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTRSVX) : inputs.get(ReserveField.DOTRSV);
                            BigDecimal retrsvAct = inputs.get(ReserveField.RETRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETRSVX) : inputs.get(ReserveField.RETRSV);
                            BigDecimal outbAct = inputs.get(ReserveField.AOUTBVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.AOUTBVX) : inputs.get(ReserveField.AOUTBV);
                            BigDecimal leftAfterOut = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                    .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                    .subtract(dotrsvAct).subtract(retrsvAct).subtract(outbAct).max(BigDecimal.ZERO);
                            BigDecimal need = inputs.get(ReserveField.ANEED);
                            return leftAfterOut.compareTo(need) < 0 ? leftAfterOut : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.NEEDX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR, ReserveField.HLDHRX, ReserveField.DOTRSV, ReserveField.DOTRSVX, ReserveField.RETRSV, ReserveField.RETRSVX, ReserveField.AOUTBV, ReserveField.AOUTBVX, ReserveField.ANEED),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                                    BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                                    BigDecimal heldAct = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                                    BigDecimal dotrsvAct = inputs.get(ReserveField.DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTRSVX) : inputs.get(ReserveField.DOTRSV);
                                    BigDecimal retrsvAct = inputs.get(ReserveField.RETRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETRSVX) : inputs.get(ReserveField.RETRSV);
                                    BigDecimal outbAct = inputs.get(ReserveField.AOUTBVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.AOUTBVX) : inputs.get(ReserveField.AOUTBV);
                                    BigDecimal leftAfterOut = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                            .subtract(dotrsvAct).subtract(retrsvAct).subtract(outbAct).max(BigDecimal.ZERO);
                                    BigDecimal need = inputs.get(ReserveField.ANEED);
                                    return leftAfterOut.compareTo(need) < 0 ? leftAfterOut : BigDecimal.ZERO;
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.NEEDX, List.of(ReserveField.UNCOMAFS, ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR, ReserveField.HLDHRX, ReserveField.DOTRSV, ReserveField.DOTRSVX, ReserveField.RETRSV, ReserveField.RETRSVX, ReserveField.AOUTBV, ReserveField.AOUTBVX, ReserveField.ANEED),
                                inputs -> {
                                    BigDecimal dotHryAct = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                                    BigDecimal dotHrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    BigDecimal rethryAct = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                                    BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                                    BigDecimal heldAct = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                                    BigDecimal dotrsvAct = inputs.get(ReserveField.DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTRSVX) : inputs.get(ReserveField.DOTRSV);
                                    BigDecimal retrsvAct = inputs.get(ReserveField.RETRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETRSVX) : inputs.get(ReserveField.RETRSV);
                                    BigDecimal outbAct = inputs.get(ReserveField.AOUTBVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.AOUTBVX) : inputs.get(ReserveField.AOUTBV);
                                    BigDecimal leftAfterOut = inputs.get(ReserveField.UNCOMAFS).subtract(dotHryAct).subtract(dotHrnAct)
                                            .subtract(rethryAct).subtract(rethrnAct).subtract(heldAct)
                                            .subtract(dotrsvAct).subtract(retrsvAct).subtract(outbAct).max(BigDecimal.ZERO);
                                    BigDecimal need = inputs.get(ReserveField.ANEED);
                                    return leftAfterOut.compareTo(need) < 0 ? leftAfterOut : BigDecimal.ZERO;
                                }, null, null, null, null)
                ),
                null, false);

        // Dynamic Snapshot Steps
        engine.addStep(ReserveField.RETAILATS,
                new Steps.CalculationStep(ReserveField.RETAILATS, List.of(ReserveField.RETHRY, ReserveField.RETHRYX, ReserveField.RETRSV, ReserveField.RETRSVX, ReserveField.ANEED, ReserveField.NEEDX),
                        inputs -> {
                            BigDecimal retHardYes = inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY);
                            BigDecimal retRes = inputs.get(ReserveField.RETRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETRSVX) : inputs.get(ReserveField.RETRSV);
                            BigDecimal adjNeed = inputs.get(ReserveField.NEEDX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.NEEDX) : inputs.get(ReserveField.ANEED);
                            return retHardYes.add(retRes).add(adjNeed);
                        }, null, null, null, null),
                Map.of(), null, true);

        engine.addStep(ReserveField.DOTATS,
                new Steps.CalculationStep(ReserveField.DOTATS, List.of(ReserveField.DOTHRY, ReserveField.DOTHRYX, ReserveField.DOTRSV, ReserveField.DOTRSVX, ReserveField.AOUTBV, ReserveField.AOUTBVX),
                        inputs -> {
                            BigDecimal dotHardYes = inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY);
                            BigDecimal dotRes = inputs.get(ReserveField.DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTRSVX) : inputs.get(ReserveField.DOTRSV);
                            BigDecimal adjOut = inputs.get(ReserveField.AOUTBVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.AOUTBVX) : inputs.get(ReserveField.AOUTBV);
                            return dotHardYes.add(dotRes).add(adjOut);
                        }, null, null, null, null),
                Map.of(), null, true);

        engine.addStep(ReserveField.UNCOMMIT,
                new Steps.CalculationStep(ReserveField.UNCOMMIT, List.of(ReserveField.INITAFS, ReserveField.COMMITTED, ReserveField.DOTHRYA, ReserveField.DOTHRNA, ReserveField.RETHRYA, ReserveField.RETHRNA, ReserveField.HLDHRA, ReserveField.DOTRSVA, ReserveField.RETRSVA, ReserveField.AOUTBVA, ReserveField.NEEDA),
                        inputs -> {
                            BigDecimal init = inputs.get(ReserveField.INITAFS);
                            BigDecimal committed = inputs.get(ReserveField.COMMITTED);
                            BigDecimal totalRes = BigDecimal.ZERO;
                            // Sum all the reserve fields
                            totalRes = totalRes.add(inputs.get(ReserveField.DOTHRYA))
                                    .add(inputs.get(ReserveField.DOTHRNA))
                                    .add(inputs.get(ReserveField.RETHRYA))
                                    .add(inputs.get(ReserveField.RETHRNA))
                                    .add(inputs.get(ReserveField.HLDHRA))
                                    .add(inputs.get(ReserveField.DOTRSVA))
                                    .add(inputs.get(ReserveField.RETRSVA))
                                    .add(inputs.get(ReserveField.AOUTBVA))
                                    .add(inputs.get(ReserveField.NEEDA));
                            BigDecimal result = init.subtract(committed).subtract(totalRes);
                            return result.max(BigDecimal.ZERO);
                        }, null, null, null, null),
                Map.of(), null, true);

        engine.addStep(ReserveField.COMMITTED,
                new Steps.CalculationStep(ReserveField.COMMITTED, List.of(ReserveField.SNB, ReserveField.DTCO, ReserveField.ROHP),
                        inputs -> inputs.get(ReserveField.SNB).add(inputs.get(ReserveField.DTCO)).add(inputs.get(ReserveField.ROHP)),
                        null, null, null, null),
                Map.of(), null, true);

        engine.addStep(ReserveField.UNCOMMHR,
                new Steps.CalculationStep(ReserveField.UNCOMMHR, List.of(ReserveField.DOTHRN, ReserveField.DOTHRNX, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.HLDHR, ReserveField.HLDHRX),
                        inputs -> {
                            BigDecimal dotNo = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                            BigDecimal retNo = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                            BigDecimal heldNo = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                            return dotNo.add(retNo).add(heldNo);
                        }, null, null, null, null),
                Map.of(), null, true);

        // Final Output Calculation Steps
        engine.addStep(ReserveField.OMSSUP,
                new Steps.CalculationStep(ReserveField.OMSSUP, List.of(ReserveField.INITAFS, ReserveField.DOTATS, ReserveField.DTCO, ReserveField.DTCOX),
                        inputs -> {
                            BigDecimal afs = inputs.get(ReserveField.INITAFS);
                            if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                            BigDecimal dtcoAct = inputs.get(ReserveField.DTCOX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DTCOX) : inputs.get(ReserveField.DTCO);
                            return inputs.get(ReserveField.DOTATS).add(dtcoAct);
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.OMSSUP, List.of(ReserveField.INITAFS, ReserveField.DOTATS, ReserveField.DTCO, ReserveField.DTCOX, ReserveField.SNB, ReserveField.SNBX, ReserveField.DOTHRN, ReserveField.DOTHRNX),
                                inputs -> {
                                    BigDecimal afs = inputs.get(ReserveField.INITAFS);
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                                    BigDecimal dtcoAct = inputs.get(ReserveField.DTCOX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DTCOX) : inputs.get(ReserveField.DTCO);
                                    BigDecimal snbAct = inputs.get(ReserveField.SNBX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.SNBX) : inputs.get(ReserveField.SNB);
                                    BigDecimal dhrnAct = inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN);
                                    return inputs.get(ReserveField.DOTATS).add(dtcoAct).add(snbAct).add(dhrnAct);
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.OMSSUP, List.of(ReserveField.INITAFS, ReserveField.DOTATS, ReserveField.DTCO, ReserveField.DTCOX),
                                inputs -> {
                                    BigDecimal afs = inputs.get(ReserveField.INITAFS);
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                                    BigDecimal dtcoAct = inputs.get(ReserveField.DTCOX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DTCOX) : inputs.get(ReserveField.DTCO);
                                    return inputs.get(ReserveField.DOTATS).add(dtcoAct);
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.RETFINAL,
                new Steps.CalculationStep(ReserveField.RETFINAL, List.of(ReserveField.INITAFS, ReserveField.RETAILATS),
                        inputs -> {
                            BigDecimal afs = inputs.get(ReserveField.INITAFS);
                            return afs.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : inputs.get(ReserveField.RETAILATS);
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.RETFINAL, List.of(ReserveField.INITAFS, ReserveField.RETAILATS, ReserveField.RETHRN, ReserveField.RETHRNX, ReserveField.ROHP, ReserveField.ROHPX, ReserveField.HLDHR, ReserveField.HLDHRX),
                                inputs -> {
                                    BigDecimal afs = inputs.get(ReserveField.INITAFS);
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return afs;
                                    BigDecimal rethrnAct = inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN);
                                    BigDecimal rohpAct = inputs.get(ReserveField.ROHPX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.ROHPX) : inputs.get(ReserveField.ROHP);
                                    BigDecimal heldAct = inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR);
                                    return inputs.get(ReserveField.RETAILATS).add(rethrnAct).add(rohpAct).add(heldAct);
                                }, null, null, null, null),
                        CalculationFlow.FRM, new Steps.CalculationStep(ReserveField.RETFINAL, List.of(ReserveField.INITAFS, ReserveField.RETAILATS, ReserveField.AOUTBVA),
                                inputs -> {
                                    BigDecimal afs = inputs.get(ReserveField.INITAFS);
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                                    return inputs.get(ReserveField.RETAILATS).add(inputs.get(ReserveField.AOUTBVA));
                                }, null, null, null, null)
                ),
                null, false);

        engine.addStep(ReserveField.OMSFINAL,
                new Steps.CalculationStep(ReserveField.OMSFINAL, List.of(ReserveField.OMSSUP),
                        inputs -> BigDecimal.ZERO,
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI, new Steps.CalculationStep(ReserveField.OMSFINAL, List.of(ReserveField.OMSSUP),
                                inputs -> inputs.get(ReserveField.OMSSUP),
                                null, null, null, null)
                ),
                null, false);

        // Actual Value Steps for constrained fields
        engine.addStep(ReserveField.SNBA,
                new Steps.ConstraintStep(ReserveField.SNBA, ReserveField.SNB, ReserveField.INITAFS),
                Map.of(), null, false);

        engine.addStep(ReserveField.DTCOA,
                new Steps.CalculationStep(ReserveField.DTCOA, List.of(ReserveField.DTCO, ReserveField.DTCOX),
                        inputs -> inputs.get(ReserveField.DTCOX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DTCOX) : inputs.get(ReserveField.DTCO),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep(ReserveField.ROHPA,
                new Steps.CalculationStep(ReserveField.ROHPA, List.of(ReserveField.ROHP, ReserveField.ROHPX),
                        inputs -> inputs.get(ReserveField.ROHPX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.ROHPX) : inputs.get(ReserveField.ROHP),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep(ReserveField.DOTHRYA,
                new Steps.CalculationStep(ReserveField.DOTHRYA, List.of(ReserveField.DOTHRY, ReserveField.DOTHRYX),
                        inputs -> inputs.get(ReserveField.DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRYX) : inputs.get(ReserveField.DOTHRY),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep(ReserveField.DOTHRNA,
                new Steps.CalculationStep(ReserveField.DOTHRNA, List.of(ReserveField.DOTHRN, ReserveField.DOTHRNX),
                        inputs -> inputs.get(ReserveField.DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTHRNX) : inputs.get(ReserveField.DOTHRN),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep(ReserveField.RETHRYA,
                new Steps.CalculationStep(ReserveField.RETHRYA, List.of(ReserveField.RETHRY, ReserveField.RETHRYX),
                        inputs -> inputs.get(ReserveField.RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRYX) : inputs.get(ReserveField.RETHRY),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep(ReserveField.RETHRNA,
                new Steps.CalculationStep(ReserveField.RETHRNA, List.of(ReserveField.RETHRN, ReserveField.RETHRNX),
                        inputs -> inputs.get(ReserveField.RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETHRNX) : inputs.get(ReserveField.RETHRN),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep(ReserveField.HLDHRA,
                new Steps.CalculationStep(ReserveField.HLDHRA, List.of(ReserveField.HLDHR, ReserveField.HLDHRX),
                        inputs -> inputs.get(ReserveField.HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.HLDHRX) : inputs.get(ReserveField.HLDHR),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep(ReserveField.DOTRSVA,
                new Steps.CalculationStep(ReserveField.DOTRSVA, List.of(ReserveField.DOTRSV, ReserveField.DOTRSVX),
                        inputs -> inputs.get(ReserveField.DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.DOTRSVX) : inputs.get(ReserveField.DOTRSV),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep(ReserveField.RETRSVA,
                new Steps.CalculationStep(ReserveField.RETRSVA, List.of(ReserveField.RETRSV, ReserveField.RETRSVX),
                        inputs -> inputs.get(ReserveField.RETRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.RETRSVX) : inputs.get(ReserveField.RETRSV),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep(ReserveField.AOUTBVA,
                new Steps.CalculationStep(ReserveField.AOUTBVA, List.of(ReserveField.AOUTBV, ReserveField.AOUTBVX),
                        inputs -> inputs.get(ReserveField.AOUTBVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.AOUTBVX) : inputs.get(ReserveField.AOUTBV),
                        null, null, null, null),
                Map.of(), null, false);

        engine.addStep(ReserveField.NEEDA,
                new Steps.CalculationStep(ReserveField.NEEDA, List.of(ReserveField.ANEED, ReserveField.NEEDX),
                        inputs -> inputs.get(ReserveField.NEEDX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ReserveField.NEEDX) : inputs.get(ReserveField.ANEED),
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