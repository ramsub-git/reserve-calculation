// ReserveCalculationEngine.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sephora.ism.reserve.ReserveField.*;

/**
 * ReserveCalculationEngine manages flow setup and calculation execution.
 * - Holds step lists per flow.
 * - Passes control to ReserveCalcContext for step execution and snapshotting.
 */
public class ReserveCalculationEngine {

    private static final Logger logger = LoggerFactory.getLogger(ReserveCalculationEngine.class);

    private final Map<CalculationFlow, List<ReserveCalcStep>> flowSteps = new EnumMap<>(CalculationFlow.class);
    private final Map<ReserveField, ReserveCalcStep> contextConditionSteps = new HashMap<>();
    private final List<ReserveCalcStep> dynamicSteps = new ArrayList<>();
    private Predicate<ReserveCalcContext> enginePreCheck = ctx -> true;
    private Predicate<ReserveCalcContext> enginePostCheck = ctx -> true;
    private final Map<CalculationFlow, List<Steps.RunningCalculationStep>> runningSteps = new EnumMap<>(CalculationFlow.class);


    // 1. FIX: Add initialization of runningSteps in constructor
    public ReserveCalculationEngine() {
        for (CalculationFlow flow : CalculationFlow.values()) {
            flowSteps.put(flow, new ArrayList<>());
            runningSteps.put(flow, new ArrayList<>());  // ADD THIS LINE
        }
    }

    // 2. FIX: In calculate method, ensure context is properly initialized
    public void calculate(ReserveCalcContext context) {
        if (!enginePreCheck.test(context)) {
            throw new IllegalStateException("Engine pre-check failed: Required conditions not met.");
        }

        // ADD: Verify initial values are set
        if (context.getInitialValueWrapper() == null) {
            throw new IllegalStateException("InitialValueWrapper must be set before calculation");
        }

        // Register running steps with context
        for (Map.Entry<CalculationFlow, List<Steps.RunningCalculationStep>> entry : runningSteps.entrySet()) {
            for (Steps.RunningCalculationStep runningStep : entry.getValue()) {
                context.registerRunningStep(entry.getKey(), runningStep);
            }
        }

        context.setDynamicSteps(dynamicSteps);

        // Log initial state
        // ReserveCalculationLogger.logInitialState(context);

        // ADD: Ensure all flows have the same number of steps
        alignFlowSteps();

        int maxStepCount = flowSteps.values().stream().mapToInt(List::size).max().orElse(0);

        for (int stepIndex = 0; stepIndex < maxStepCount; stepIndex++) {
            Map<CalculationFlow, ReserveCalcStep> currentSteps = new EnumMap<>(CalculationFlow.class);

            for (CalculationFlow flow : CalculationFlow.values()) {
                List<ReserveCalcStep> steps = flowSteps.get(flow);
                if (stepIndex < steps.size()) {
                    currentSteps.put(flow, steps.get(stepIndex));
                }
            }

            if (currentSteps.isEmpty()) {
                continue;
            }

            // FIX: Get field name more safely
            ReserveField fieldName = null;
            for (ReserveCalcStep step : currentSteps.values()) {
                if (step != null) {
                    fieldName = step.getFieldName();
                    break;
                }
            }

            if (fieldName == null) {
                logger.error("Warning: No field name found for step {}", stepIndex);
                continue;
            }

            ReserveCalcStep contextConditionStep = contextConditionSteps.get(fieldName);

            context.calculateSteps(stepIndex, currentSteps, contextConditionStep);

            // Log after calculation
            // ReserveCalculationLogger.logStepCalculation(stepIndex, currentSteps, context);

//            // Show propagation every 10 steps or for important fields
//            if (stepIndex % 2 == 0 || fieldName == ReserveField.INITAFS ||
//                    fieldName == ReserveField.UNCOMAFS || fieldName.name().contains("X")) {
//                context.showResultSetsPropagation();
//            }


        }


        // Show final propagation
        logger.info("\n=== FINAL PROPAGATION STATE ===");
        context.showResultSetsPropagation();

        // Log final summary
        ReserveCalculationLogger.logFinalSummary(context);

        // Perform post-checks
        if (!enginePostCheck.test(context)) {
            throw new IllegalStateException("Engine post-check failed: Validation conditions not met.");
        }
    }

    // 3. ADD: Method to align flow steps (ensure all flows have same number of steps)
    private void alignFlowSteps() {
        // Find the maximum number of steps across all flows
        int maxSteps = flowSteps.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        // Pad shorter flows with no-op steps
        for (Map.Entry<CalculationFlow, List<ReserveCalcStep>> entry : flowSteps.entrySet()) {
            List<ReserveCalcStep> steps = entry.getValue();
            CalculationFlow flow = entry.getKey();

            while (steps.size() < maxSteps) {
                // Add a no-op step that just returns zero
                Steps.ConstantStep noOp = new Steps.ConstantStep(
                        ReserveField.DIV, // Use a dummy field
                        BigDecimal.ZERO
                );
                noOp.setFlow(flow);
                steps.add(noOp);
            }
        }
    }

    // 4. FIX: Improve addStep to handle edge cases better
    public void addStep(ReserveField fieldName, ReserveCalcStep mainStep,
                        Map<CalculationFlow, ReserveCalcStep> alternateSteps,
                        ReserveCalcStep contextConditionStep, boolean isDynamic) {

        // Validate inputs
        if (fieldName == null || mainStep == null) {
            throw new IllegalArgumentException("fieldName and mainStep cannot be null");
        }

        if (alternateSteps == null) {
            alternateSteps = new EnumMap<>(CalculationFlow.class);
        }

        // Handle RunningCalculationStep separately
        if (mainStep instanceof Steps.RunningCalculationStep) {
            Steps.RunningCalculationStep runningStep = (Steps.RunningCalculationStep) mainStep;

            // Add to each flow's running steps
            for (CalculationFlow flow : CalculationFlow.values()) {
                Steps.RunningCalculationStep flowRunningStep = (Steps.RunningCalculationStep) runningStep.copy();
                flowRunningStep.setFlow(flow);
                runningSteps.get(flow).add(flowRunningStep);
            }

            // Also add to dynamic steps if marked as dynamic
            if (isDynamic) {
                dynamicSteps.add(mainStep);
            }

            // Don't add to regular flow steps - these are triggered differently
            return;
        }

        // Regular step handling
        for (CalculationFlow flow : CalculationFlow.values()) {
            ReserveCalcStep stepForFlow = alternateSteps.get(flow);

            // If no alternate provided, use copy of main step
            if (stepForFlow == null) {
                stepForFlow = mainStep.copy();
            }

            stepForFlow.setFlow(flow);
            flowSteps.get(flow).add(stepForFlow);
        }

        if (contextConditionStep != null) {
            contextConditionSteps.put(fieldName, contextConditionStep);
        }

        if (isDynamic && !(mainStep instanceof Steps.RunningCalculationStep)) {
            dynamicSteps.add(mainStep);
        }
    }

    // 5. ADD: Utility methods for debugging
    public void printEngineStructure() {
        logger.info("\n=== Engine Structure ===");

        for (CalculationFlow flow : CalculationFlow.values()) {
            logger.info("\nFlow: " + flow);
            List<ReserveCalcStep> steps = flowSteps.get(flow);
            for (int i = 0; i < steps.size(); i++) {
                ReserveCalcStep step = steps.get(i);
                logger.info("  Step %d: %s (%s)\n",
                        i, step.getFieldName(), step.getClass().getSimpleName());
            }

            logger.info("  Running Steps: " + runningSteps.get(flow).size());
        }

        logger.info("\nDynamic Steps: " + dynamicSteps.size());
        logger.info("Context Condition Steps: " + contextConditionSteps.size());
    }

    // 6. ADD: Method to validate engine setup
    public boolean validateEngineSetup() {
        boolean valid = true;

        // Check all flows have same number of steps
        Set<Integer> stepCounts = flowSteps.values().stream()
                .map(List::size)
                .collect(Collectors.toSet());

        if (stepCounts.size() > 1) {
            logger.error("ERROR: Flows have different step counts: " + stepCounts);
            valid = false;
        }

        // Check each step has flow set
        for (Map.Entry<CalculationFlow, List<ReserveCalcStep>> entry : flowSteps.entrySet()) {
            CalculationFlow flow = entry.getKey();
            for (ReserveCalcStep step : entry.getValue()) {
                if (step.getFlow() != flow) {
                    logger.error("ERROR: Step %s has wrong flow: expected %s, got %s\n",
                            step.getFieldName(), flow, step.getFlow());
                    valid = false;
                }
            }
        }

        return valid;
    }

    // Converted setupReserveCalculationSteps method with field name lookups
    public static void setupReserveCalculationSteps(ReserveCalculationEngine engine) {
    
        // ===== SECTION 1: BASE FIELDS =====
        
        // Key constant
        engine.addStep(DIV,
                new Steps.ConstantStep(DIV, new BigDecimal("30")),
                Map.of(), null, false);
    
        // Base SKULOC Input Fields (removed DAMAGED, added OOBADJ)
        engine.addStep(BYCL,
                new Steps.SkulocFieldStep(BYCL),
                Map.of(), null, false);
    
        engine.addStep(SKUSTS,
                new Steps.SkulocFieldStep(SKUSTS),
                Map.of(), null, false);
    
        engine.addStep(ONHAND,
                new Steps.SkulocFieldStep(ONHAND),
                Map.of(), null, false);
    
        engine.addStep(ROHM,
                new Steps.SkulocFieldStep(ROHM),
                Map.of(), null, false);
    
        engine.addStep(LOST,
                new Steps.SkulocFieldStep(LOST),
                Map.of(), null, false);
    
        engine.addStep(OOBADJ,
                new Steps.SkulocFieldStep(OOBADJ),
                Map.of(), null, false);
    
        // ===== SECTION 2: INITIAL AFS CALCULATION =====
        
        engine.addStep(INITAFS,
                new Steps.CalculationStep(INITAFS, 
                        List.of(ONHAND, ROHM, LOST, OOBADJ),
                        inputs -> {
                            // Non-JEI: ONHAND - ROHM - LOST - MAX(OOBADJ, 0)
                            BigDecimal result = inputs.get(ONHAND)
                                    .subtract(inputs.get(ROHM))
                                    .subtract(inputs.get(LOST))
                                    .subtract(inputs.get(OOBADJ).max(BigDecimal.ZERO));
                            return result.max(BigDecimal.ZERO);
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI,
                        new Steps.CalculationStep(INITAFS, 
                                List.of(ONHAND, LOST),
                                inputs -> {
                                    // JEI: ONHAND - LOST
                                    return inputs.get(ONHAND).subtract(inputs.get(LOST));
                                }, null, null, null, null),
                        CalculationFlow.FRM,
                        new Steps.CalculationStep(INITAFS, 
                                List.of(ONHAND, ROHM, LOST, OOBADJ),
                                inputs -> {
                                    // FRM: ONHAND - ROHM - LOST - MAX(OOBADJ, 0)
                                    BigDecimal result = inputs.get(ONHAND)
                                            .subtract(inputs.get(ROHM))
                                            .subtract(inputs.get(LOST))
                                            .subtract(inputs.get(OOBADJ).max(BigDecimal.ZERO));
                                    return result.max(BigDecimal.ZERO);
                                }, null, null, null, null)
                ),
                null, false);
    
        // ===== SECTION 3: COMMITMENT FIELDS =====
        
        engine.addStep(SNB,
                new Steps.SkulocFieldStep(SNB),
                Map.of(), null, false);
    
        engine.addStep(SNBX,
                new Steps.CalculationStep(SNBX, List.of(INITAFS, SNB),
                        inputs -> {
                            BigDecimal available = inputs.get(INITAFS);
                            BigDecimal requested = inputs.get(SNB);
                            return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(DTCO,
                new Steps.SkulocFieldStep(DTCO),
                Map.of(), null, false);
    
        engine.addStep(DTCOX,
                new Steps.CalculationStep(DTCOX, List.of(INITAFS, SNB, SNBX, DTCO),
                        inputs -> {
                            BigDecimal initAfs = inputs.get(INITAFS);
                            BigDecimal snb = inputs.get(SNB);
                            BigDecimal snbx = inputs.get(SNBX);
                            BigDecimal dtco = inputs.get(DTCO);
                            
                            // Calculate actual SNB used
                            BigDecimal snbActual = snbx.compareTo(BigDecimal.ZERO) > 0 ? snbx : snb;
                            BigDecimal available = initAfs.subtract(snbActual).max(BigDecimal.ZERO);
                            
                            return available.compareTo(dtco) < 0 ? available : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(ROHP,
                new Steps.SkulocFieldStep(ROHP),
                Map.of(), null, false);
    
        engine.addStep(ROHPX,
                new Steps.CalculationStep(ROHPX, List.of(INITAFS, SNB, SNBX, DTCO, DTCOX, ROHP),
                        inputs -> {
                            BigDecimal initAfs = inputs.get(INITAFS);
                            BigDecimal snbActual = inputs.get(SNBX).compareTo(BigDecimal.ZERO) > 0 ? 
                                    inputs.get(SNBX) : inputs.get(SNB);
                            BigDecimal dtcoActual = inputs.get(DTCOX).compareTo(BigDecimal.ZERO) > 0 ? 
                                    inputs.get(DTCOX) : inputs.get(DTCO);
                            BigDecimal rohp = inputs.get(ROHP);
                            
                            BigDecimal available = initAfs.subtract(snbActual).subtract(dtcoActual).max(BigDecimal.ZERO);
                            return available.compareTo(rohp) < 0 ? available : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(), null, false);
    
        // ===== SECTION 4: UNCOMMITTED AFS =====
        
        engine.addStep(UNCOMAFS,
                new Steps.CalculationStep(UNCOMAFS, List.of(INITAFS, SNB, DTCO, ROHP),
                        inputs -> {
                            BigDecimal result = inputs.get(INITAFS)
                                    .subtract(inputs.get(SNB))
                                    .subtract(inputs.get(DTCO))
                                    .subtract(inputs.get(ROHP));
                            return result.max(BigDecimal.ZERO);
                        }, null, null, null, null),
                Map.of(), null, false);
    
        // ===== SECTION 5: RUNNING INVENTORY TRACKER =====
        // This replaces the need for getConsumedUpTo() helper function
        engine.addStep(RUNNING_AFS,
                new Steps.RunningCalculationStep(RUNNING_AFS,
                        UNCOMAFS,  // Start with uncommitted AFS
                        List.of(DOTHRY, DOTHRYX, DOTHRN, DOTHRNX, RETHRY, RETHRYX, 
                               RETHRN, RETHRNX, HLDHR, HLDHRX, DOTRSV, DOTRSVX, 
                               RETRSV, RETRSVX, AOUTBV, AOUTBVX, ANEED, NEEDX),
                        false, // Not self-driven, triggers on allocation fields
                        (running, allocated) -> running.subtract(allocated).max(BigDecimal.ZERO)
                ),
                Map.of(), null, true);
    
        // ===== SECTION 6: HARD RESERVES WITH CONSTRAINTS =====
        
        engine.addStep(DOTHRY,
                new Steps.SkulocFieldStep(DOTHRY),
                Map.of(), null, false);
    
        engine.addStep(DOTHRYX,
                new Steps.CalculationStep(DOTHRYX, List.of(RUNNING_AFS, DOTHRY),
                        inputs -> {
                            BigDecimal available = inputs.get(RUNNING_AFS);
                            BigDecimal requested = inputs.get(DOTHRY);
                            return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(DOTHRN,
                new Steps.SkulocFieldStep(DOTHRN),
                Map.of(), null, false);
    
        engine.addStep(DOTHRNX,
                new Steps.CalculationStep(DOTHRNX, List.of(RUNNING_AFS, DOTHRN),
                        inputs -> {
                            BigDecimal available = inputs.get(RUNNING_AFS);
                            BigDecimal requested = inputs.get(DOTHRN);
                            return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(RETHRY,
                new Steps.SkulocFieldStep(RETHRY),
                Map.of(), null, false);
    
        engine.addStep(RETHRYX,
                new Steps.CalculationStep(RETHRYX, List.of(RUNNING_AFS, RETHRY),
                        inputs -> {
                            BigDecimal available = inputs.get(RUNNING_AFS);
                            BigDecimal requested = inputs.get(RETHRY);
                            return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(RETHRN,
                new Steps.SkulocFieldStep(RETHRN),
                Map.of(), null, false);
    
        engine.addStep(RETHRNX,
                new Steps.CalculationStep(RETHRNX, List.of(RUNNING_AFS, RETHRN),
                        inputs -> {
                            BigDecimal available = inputs.get(RUNNING_AFS);
                            BigDecimal requested = inputs.get(RETHRN);
                            return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(HLDHR,
                new Steps.SkulocFieldStep(HLDHR),
                Map.of(), null, false);
    
        engine.addStep(HLDHRX,
                new Steps.CalculationStep(HLDHRX, List.of(RUNNING_AFS, HLDHR),
                        inputs -> {
                            BigDecimal available = inputs.get(RUNNING_AFS);
                            BigDecimal requested = inputs.get(HLDHR);
                            return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(), null, false);
    
        // ===== SECTION 7: PO RESERVES =====
        
        engine.addStep(DOTRSV,
                new Steps.SkulocFieldStep(DOTRSV),
                Map.of(), null, false);
    
        engine.addStep(DOTRSVX,
                new Steps.CalculationStep(DOTRSVX, List.of(RUNNING_AFS, DOTRSV),
                        inputs -> {
                            BigDecimal available = inputs.get(RUNNING_AFS);
                            BigDecimal requested = inputs.get(DOTRSV);
                            return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(RETRSV,
                new Steps.SkulocFieldStep(RETRSV),
                Map.of(), null, false);
    
        engine.addStep(RETRSVX,
                new Steps.CalculationStep(RETRSVX, List.of(RUNNING_AFS, RETRSV),
                        inputs -> {
                            BigDecimal available = inputs.get(RUNNING_AFS);
                            BigDecimal requested = inputs.get(RETRSV);
                            return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(), null, false);
    
        // ===== SECTION 8: OUTBOUND BACKFILL =====
        
        engine.addStep(DOTOUTB,
                new Steps.SkulocFieldStep(DOTOUTB),
                Map.of(), null, false);
    
        // AOUTBV - Special: MAX((DOTOUTB - @DOTATS), 0)
        engine.addStep(AOUTBV,
                new Steps.CalculationStep(AOUTBV, List.of(DOTOUTB, DOTATS),
                        inputs -> {
                            BigDecimal dotoutb = inputs.get(DOTOUTB);
                            BigDecimal dotats = inputs.get(DOTATS);
                            return dotoutb.subtract(dotats).max(BigDecimal.ZERO);
                        }, null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(AOUTBVX,
                new Steps.CalculationStep(AOUTBVX, List.of(RUNNING_AFS, AOUTBV),
                        inputs -> {
                            BigDecimal available = inputs.get(RUNNING_AFS);
                            BigDecimal requested = inputs.get(AOUTBV);
                            return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(), null, false);
    
        // ===== SECTION 9: RETAIL NEED BACKFILL =====
        
        engine.addStep(NEED,
                new Steps.SkulocFieldStep(NEED),
                Map.of(), null, false);
    
        // ANEED - Special: MAX((NEED - @RETAILATS), 0)
        engine.addStep(ANEED,
                new Steps.CalculationStep(ANEED, List.of(NEED, RETAILATS),
                        inputs -> {
                            BigDecimal need = inputs.get(NEED);
                            BigDecimal retailats = inputs.get(RETAILATS);
                            return need.subtract(retailats).max(BigDecimal.ZERO);
                        }, null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(NEEDX,
                new Steps.CalculationStep(NEEDX, List.of(RUNNING_AFS, ANEED),
                        inputs -> {
                            BigDecimal available = inputs.get(RUNNING_AFS);
                            BigDecimal requested = inputs.get(ANEED);
                            return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
                        }, null, null, null, null),
                Map.of(), null, false);
    
        // ===== SECTION 10: DYNAMIC AGGREGATE FIELDS =====
        
        // @RETAILATS - Retail Available To Sell
        engine.addStep(RETAILATS,
                new Steps.CalculationStep(RETAILATS, 
                        List.of(RETHRY, RETHRYX, RETRSV, RETRSVX, ANEED, NEEDX),
                        inputs -> {
                            BigDecimal retHardYes = inputs.get(RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? 
                                    inputs.get(RETHRYX) : inputs.get(RETHRY);
                            BigDecimal retRes = inputs.get(RETRSVX).compareTo(BigDecimal.ZERO) > 0 ? 
                                    inputs.get(RETRSVX) : inputs.get(RETRSV);
                            BigDecimal adjNeed = inputs.get(NEEDX).compareTo(BigDecimal.ZERO) > 0 ? 
                                    inputs.get(NEEDX) : inputs.get(ANEED);
                            return retHardYes.add(retRes).add(adjNeed);
                        }, null, null, null, null),
                Map.of(), null, true); // isDynamic = true
    
        // @DOTATS - Dotcom Available To Sell
        engine.addStep(DOTATS,
                new Steps.CalculationStep(DOTATS, 
                        List.of(DOTHRY, DOTHRYX, DOTRSV, DOTRSVX, AOUTBV, AOUTBVX),
                        inputs -> {
                            BigDecimal dotHardYes = inputs.get(DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? 
                                    inputs.get(DOTHRYX) : inputs.get(DOTHRY);
                            BigDecimal dotRes = inputs.get(DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? 
                                    inputs.get(DOTRSVX) : inputs.get(DOTRSV);
                            BigDecimal adjOut = inputs.get(AOUTBVX).compareTo(BigDecimal.ZERO) > 0 ? 
                                    inputs.get(AOUTBVX) : inputs.get(AOUTBV);
                            return dotHardYes.add(dotRes).add(adjOut);
                        }, null, null, null, null),
                Map.of(), null, true); // isDynamic = true
    
        // @COMMITTED - Total Committed Inventory
        engine.addStep(COMMITTED,
                new Steps.CalculationStep(COMMITTED, List.of(SNB, DTCO, ROHP),
                        inputs -> inputs.get(SNB).add(inputs.get(DTCO)).add(inputs.get(ROHP)),
                        null, null, null, null),
                Map.of(), null, true); // isDynamic = true
    
        // @UNCOMMIT - Uncommitted Inventory
        engine.addStep(UNCOMMIT,
                new Steps.CalculationStep(UNCOMMIT, 
                        List.of(INITAFS, COMMITTED, DOTATS, RETAILATS),
                        inputs -> {
                            BigDecimal init = inputs.get(INITAFS);
                            BigDecimal committed = inputs.get(COMMITTED);
                            BigDecimal dotats = inputs.get(DOTATS);
                            BigDecimal retailats = inputs.get(RETAILATS);
                            BigDecimal result = init.subtract(committed).subtract(dotats).subtract(retailats);
                            return result.max(BigDecimal.ZERO);
                        }, null, null, null, null),
                Map.of(), null, true); // isDynamic = true
    
        // @UNCOMMHR - Uncommitted Hard Reserve
        engine.addStep(UNCOMMHR,
                new Steps.CalculationStep(UNCOMMHR, 
                        List.of(DOTHRN, DOTHRNX, RETHRN, RETHRNX, HLDHR, HLDHRX),
                        inputs -> {
                            BigDecimal dotNo = inputs.get(DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? 
                                    inputs.get(DOTHRNX) : inputs.get(DOTHRN);
                            BigDecimal retNo = inputs.get(RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? 
                                    inputs.get(RETHRNX) : inputs.get(RETHRN);
                            BigDecimal held = inputs.get(HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? 
                                    inputs.get(HLDHRX) : inputs.get(HLDHR);
                            return dotNo.add(retNo).add(held);
                        }, null, null, null, null),
                Map.of(), null, true); // isDynamic = true
    
        // ===== SECTION 11: FINAL OUTPUT FIELDS =====
        
        // @OMSSUP - OMS Supply
        engine.addStep(OMSSUP,
                new Steps.CalculationStep(OMSSUP, List.of(INITAFS, DOTATS, DTCO, DTCOX),
                        inputs -> {
                            BigDecimal afs = inputs.get(INITAFS);
                            if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                            
                            BigDecimal dtcoAct = inputs.get(DTCOX).compareTo(BigDecimal.ZERO) > 0 ? 
                                    inputs.get(DTCOX) : inputs.get(DTCO);
                            return inputs.get(DOTATS).add(dtcoAct);
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI,
                        new Steps.CalculationStep(OMSSUP, 
                                List.of(INITAFS, DOTATS, DTCO, DTCOX, SNB, SNBX, DOTHRN, DOTHRNX, UNCOMAFS),
                                inputs -> {
                                    BigDecimal afs = inputs.get(INITAFS);
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                                    
                                    BigDecimal dtcoAct = inputs.get(DTCOX).compareTo(BigDecimal.ZERO) > 0 ? 
                                            inputs.get(DTCOX) : inputs.get(DTCO);
                                    BigDecimal snbAct = inputs.get(SNBX).compareTo(BigDecimal.ZERO) > 0 ? 
                                            inputs.get(SNBX) : inputs.get(SNB);
                                    BigDecimal dhrnAct = inputs.get(DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? 
                                            inputs.get(DOTHRNX) : inputs.get(DOTHRN);
                                    
                                    BigDecimal result = inputs.get(DOTATS).add(dtcoAct).add(snbAct).add(dhrnAct);
                                    
                                    // Special JEI logic
                                    if (inputs.get(UNCOMAFS).compareTo(BigDecimal.ZERO) <= 0) {
                                        result = dtcoAct.add(snbAct);
                                    }
                                    
                                    return result;
                                }, null, null, null, null)
                ),
                null, false);
    
        // @RETFINAL - Retail Supply Final
        engine.addStep(RETFINAL,
                new Steps.CalculationStep(RETFINAL, List.of(INITAFS, RETAILATS),
                        inputs -> {
                            BigDecimal afs = inputs.get(INITAFS);
                            return afs.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : inputs.get(RETAILATS);
                        }, null, null, null, null),
                Map.of(
                        CalculationFlow.JEI,
                        new Steps.CalculationStep(RETFINAL, 
                                List.of(INITAFS, RETAILATS, RETHRN, RETHRNX, ROHP, ROHPX, HLDHR, HLDHRX),
                                inputs -> {
                                    BigDecimal afs = inputs.get(INITAFS);
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return afs;
                                    
                                    BigDecimal rethrnAct = inputs.get(RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? 
                                            inputs.get(RETHRNX) : inputs.get(RETHRN);
                                    BigDecimal rohpAct = inputs.get(ROHPX).compareTo(BigDecimal.ZERO) > 0 ? 
                                            inputs.get(ROHPX) : inputs.get(ROHP);
                                    BigDecimal heldAct = inputs.get(HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? 
                                            inputs.get(HLDHRX) : inputs.get(HLDHR);
                                    
                                    return inputs.get(RETAILATS).add(rethrnAct).add(rohpAct).add(heldAct);
                                }, null, null, null, null),
                        CalculationFlow.FRM,
                        new Steps.CalculationStep(RETFINAL, 
                                List.of(INITAFS, RETAILATS, AOUTBVA),
                                inputs -> {
                                    BigDecimal afs = inputs.get(INITAFS);
                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
                                    
                                    return inputs.get(RETAILATS).add(inputs.get(AOUTBVA));
                                }, null, null, null, null)
                ),
                null, false);
    
        // @OMSFINAL - OMS Final Supply
        engine.addStep(OMSFINAL,
                new Steps.CalculationStep(OMSFINAL, List.of(OMSSUP),
                        inputs -> BigDecimal.ZERO, // Default OMS = 0
                        null, null, null, null),
                Map.of(
                        CalculationFlow.JEI,
                        new Steps.CalculationStep(OMSFINAL, List.of(OMSSUP),
                                inputs -> inputs.get(OMSSUP), // JEI returns OMSSUP
                                null, null, null, null)
                ),
                null, false);
    
        // ===== SECTION 12: ACTUAL VALUE FIELDS =====
        
        engine.addStep(SNBA,
                new Steps.CalculationStep(SNBA, List.of(SNB, SNBX),
                        inputs -> inputs.get(SNBX).compareTo(BigDecimal.ZERO) > 0 ? 
                                inputs.get(SNBX) : inputs.get(SNB),
                        null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(DTCOA,
                new Steps.CalculationStep(DTCOA, List.of(DTCO, DTCOX),
                        inputs -> inputs.get(DTCOX).compareTo(BigDecimal.ZERO) > 0 ? 
                                inputs.get(DTCOX) : inputs.get(DTCO),
                        null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(ROHPA,
                new Steps.CalculationStep(ROHPA, List.of(ROHP, ROHPX),
                        inputs -> inputs.get(ROHPX).compareTo(BigDecimal.ZERO) > 0 ? 
                                inputs.get(ROHPX) : inputs.get(ROHP),
                        null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(DOTHRYA,
                new Steps.CalculationStep(DOTHRYA, List.of(DOTHRY, DOTHRYX),
                        inputs -> inputs.get(DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? 
                                inputs.get(DOTHRYX) : inputs.get(DOTHRY),
                        null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(DOTHRNA,
                new Steps.CalculationStep(DOTHRNA, List.of(DOTHRN, DOTHRNX),
                        inputs -> inputs.get(DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? 
                                inputs.get(DOTHRNX) : inputs.get(DOTHRN),
                        null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(RETHRYA,
                new Steps.CalculationStep(RETHRYA, List.of(RETHRY, RETHRYX),
                        inputs -> inputs.get(RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? 
                                inputs.get(RETHRYX) : inputs.get(RETHRY),
                        null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(RETHRNA,
                new Steps.CalculationStep(RETHRNA, List.of(RETHRN, RETHRNX),
                        inputs -> inputs.get(RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? 
                                inputs.get(RETHRNX) : inputs.get(RETHRN),
                        null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(HLDHRA,
                new Steps.CalculationStep(HLDHRA, List.of(HLDHR, HLDHRX),
                        inputs -> inputs.get(HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? 
                                inputs.get(HLDHRX) : inputs.get(HLDHR),
                        null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(DOTRSVA,
                new Steps.CalculationStep(DOTRSVA, List.of(DOTRSV, DOTRSVX),
                        inputs -> inputs.get(DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? 
                                inputs.get(DOTRSVX) : inputs.get(DOTRSV),
                        null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(RETRSVA,
                new Steps.CalculationStep(RETRSVA, List.of(RETRSV, RETRSVX),
                        inputs -> inputs.get(RETRSVX).compareTo(BigDecimal.ZERO) > 0 ? 
                                inputs.get(RETRSVX) : inputs.get(RETRSV),
                        null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(AOUTBVA,
                new Steps.CalculationStep(AOUTBVA, List.of(AOUTBV, AOUTBVX),
                        inputs -> inputs.get(AOUTBVX).compareTo(BigDecimal.ZERO) > 0 ? 
                                inputs.get(AOUTBVX) : inputs.get(AOUTBV),
                        null, null, null, null),
                Map.of(), null, false);
    
        engine.addStep(NEEDA,
                new Steps.CalculationStep(NEEDA, List.of(ANEED, NEEDX),
                        inputs -> inputs.get(NEEDX).compareTo(BigDecimal.ZERO) > 0 ? 
                                inputs.get(NEEDX) : inputs.get(ANEED),
                        null, null, null, null),
                Map.of(), null, false);
    }
}