// ReserveCalculationEngine.java
package com.sephora.ism.reserve;

import static com.sephora.ism.reserve.ReserveField.ANEED;
import static com.sephora.ism.reserve.ReserveField.ANEEDZ;
import static com.sephora.ism.reserve.ReserveField.AOUTBV;
import static com.sephora.ism.reserve.ReserveField.AOUTBVA;
import static com.sephora.ism.reserve.ReserveField.AOUTBVX;
import static com.sephora.ism.reserve.ReserveField.AOUTBVZ;
import static com.sephora.ism.reserve.ReserveField.BYCL;
import static com.sephora.ism.reserve.ReserveField.COMMITTED;
import static com.sephora.ism.reserve.ReserveField.DIV;
import static com.sephora.ism.reserve.ReserveField.DOTATS;
import static com.sephora.ism.reserve.ReserveField.DOTHRN;
import static com.sephora.ism.reserve.ReserveField.DOTHRNA;
import static com.sephora.ism.reserve.ReserveField.DOTHRNX;
import static com.sephora.ism.reserve.ReserveField.DOTHRNZ;
import static com.sephora.ism.reserve.ReserveField.DOTHRY;
import static com.sephora.ism.reserve.ReserveField.DOTHRYA;
import static com.sephora.ism.reserve.ReserveField.DOTHRYX;
import static com.sephora.ism.reserve.ReserveField.DOTHRYZ;
import static com.sephora.ism.reserve.ReserveField.DOTOUTB;
import static com.sephora.ism.reserve.ReserveField.DOTRSV;
import static com.sephora.ism.reserve.ReserveField.DOTRSVA;
import static com.sephora.ism.reserve.ReserveField.DOTRSVX;
import static com.sephora.ism.reserve.ReserveField.DOTRSVZ;
import static com.sephora.ism.reserve.ReserveField.DTCO;
import static com.sephora.ism.reserve.ReserveField.DTCOA;
import static com.sephora.ism.reserve.ReserveField.DTCOX;
import static com.sephora.ism.reserve.ReserveField.HLDHR;
import static com.sephora.ism.reserve.ReserveField.HLDHRA;
import static com.sephora.ism.reserve.ReserveField.HLDHRX;
import static com.sephora.ism.reserve.ReserveField.HLDHRZ;
import static com.sephora.ism.reserve.ReserveField.INITAFS;
import static com.sephora.ism.reserve.ReserveField.LOST;
import static com.sephora.ism.reserve.ReserveField.NEED;
import static com.sephora.ism.reserve.ReserveField.NEEDA;
import static com.sephora.ism.reserve.ReserveField.NEEDX;
import static com.sephora.ism.reserve.ReserveField.OMSFINAL;
import static com.sephora.ism.reserve.ReserveField.OMSSUP;
import static com.sephora.ism.reserve.ReserveField.ONHAND;
import static com.sephora.ism.reserve.ReserveField.OOBADJ;
import static com.sephora.ism.reserve.ReserveField.RETAILATS;
import static com.sephora.ism.reserve.ReserveField.RETFINAL;
import static com.sephora.ism.reserve.ReserveField.RETHRN;
import static com.sephora.ism.reserve.ReserveField.RETHRNA;
import static com.sephora.ism.reserve.ReserveField.RETHRNX;
import static com.sephora.ism.reserve.ReserveField.RETHRNZ;
import static com.sephora.ism.reserve.ReserveField.RETHRY;
import static com.sephora.ism.reserve.ReserveField.RETHRYA;
import static com.sephora.ism.reserve.ReserveField.RETHRYX;
import static com.sephora.ism.reserve.ReserveField.RETHRYZ;
import static com.sephora.ism.reserve.ReserveField.RETRSV;
import static com.sephora.ism.reserve.ReserveField.RETRSVA;
import static com.sephora.ism.reserve.ReserveField.RETRSVX;
import static com.sephora.ism.reserve.ReserveField.RETRSVZ;
import static com.sephora.ism.reserve.ReserveField.ROHM;
import static com.sephora.ism.reserve.ReserveField.ROHP;
import static com.sephora.ism.reserve.ReserveField.ROHPA;
import static com.sephora.ism.reserve.ReserveField.ROHPX;
import static com.sephora.ism.reserve.ReserveField.RUNNING_AFS;
import static com.sephora.ism.reserve.ReserveField.SNB;
import static com.sephora.ism.reserve.ReserveField.SNBA;
import static com.sephora.ism.reserve.ReserveField.SNBX;
import static com.sephora.ism.reserve.ReserveField.UNCOMAFS;
import static com.sephora.ism.reserve.ReserveField.UNCOMMHR;
import static com.sephora.ism.reserve.ReserveField.UNCOMMIT;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ReserveCalculationEngine manages flow setup and calculation execution. -
 * Holds step lists per flow. - Passes control to ReserveCalcContext for step
 * execution and snapshotting.
 */
public class ReserveCalculationEngine {

	private static final Logger logger = LoggerFactory.getLogger(ReserveCalculationEngine.class);

	private final Map<CalculationFlow, List<ReserveCalcStep>> flowSteps = new EnumMap<>(CalculationFlow.class);
	private final Map<ReserveField, ReserveCalcStep> contextConditionSteps = new HashMap<>();
	private final List<ReserveCalcStep> dynamicSteps = new ArrayList<>();
	private Predicate<ReserveCalcContext> enginePreCheck = ctx -> true;
	private Predicate<ReserveCalcContext> enginePostCheck = ctx -> true;
	private final Map<CalculationFlow, List<Steps.RunningCalculationStep>> runningSteps = new EnumMap<>(
			CalculationFlow.class);

	// 1. FIX: Add initialization of runningSteps in constructor
	public ReserveCalculationEngine() {
		for (CalculationFlow flow : CalculationFlow.values()) {
			flowSteps.put(flow, new ArrayList<>());
			runningSteps.put(flow, new ArrayList<>()); // ADD THIS LINE
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
			// ReserveCalculationLogger.logStepCalculation(stepIndex, currentSteps,
			// context);

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

	// 3. ADD: Method to align flow steps (ensure all flows have same number of
	// steps)
	private void alignFlowSteps() {
		// Find the maximum number of steps across all flows
		int maxSteps = flowSteps.values().stream().mapToInt(List::size).max().orElse(0);

		// Pad shorter flows with no-op steps
		for (Map.Entry<CalculationFlow, List<ReserveCalcStep>> entry : flowSteps.entrySet()) {
			List<ReserveCalcStep> steps = entry.getValue();
			CalculationFlow flow = entry.getKey();

			while (steps.size() < maxSteps) {
				// Add a no-op step that just returns zero
				Steps.ConstantStep noOp = new Steps.ConstantStep(ReserveField.DIV, // Use a dummy field
						BigDecimal.ZERO);
				noOp.setFlow(flow);
				steps.add(noOp);
			}
		}
	}

	// 4. FIX: Improve addStep to handle edge cases better
	public void addStep(ReserveField fieldName, ReserveCalcStep mainStep,
			Map<CalculationFlow, ReserveCalcStep> alternateSteps, ReserveCalcStep contextConditionStep,
			boolean isDynamic) {

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
				logger.info("  Step %d: %s (%s)\n", i, step.getFieldName(), step.getClass().getSimpleName());
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
		Set<Integer> stepCounts = flowSteps.values().stream().map(List::size).collect(Collectors.toSet());

		if (stepCounts.size() > 1) {
			logger.error("ERROR: Flows have different step counts: " + stepCounts);
			valid = false;
		}

		// Check each step has flow set
		for (Map.Entry<CalculationFlow, List<ReserveCalcStep>> entry : flowSteps.entrySet()) {
			CalculationFlow flow = entry.getKey();
			for (ReserveCalcStep step : entry.getValue()) {
				if (step.getFlow() != flow) {
					logger.error("ERROR: Step %s has wrong flow: expected %s, got %s\n", step.getFieldName(), flow,
							step.getFlow());
					valid = false;
				}
			}
		}

		return valid;
	}

	// Converted setupReserveCalculationSteps method with field name lookups
	public static void setupReserveCalculationSteps(ReserveCalculationEngine engine) {

		// ===== PHASE 1: INPUT FIELDS (SkulocFieldStep) =====

		// Key constant
		engine.addStep(DIV, new Steps.ConstantStep(DIV, new BigDecimal("30")), Map.of(), null, false);

		// Base SKULOC Input Fields
		engine.addStep(ONHAND, new Steps.SkulocFieldStep(ONHAND), Map.of(), null, false);

		engine.addStep(ROHM, new Steps.SkulocFieldStep(ROHM), Map.of(), null, false);

		engine.addStep(LOST, new Steps.SkulocFieldStep(LOST), Map.of(), null, false);

		engine.addStep(OOBADJ, new Steps.SkulocFieldStep(OOBADJ), Map.of(), null, false);

		engine.addStep(BYCL, new Steps.SkulocStringFieldStep(BYCL), Map.of(), null, false);

		// INITAFS with flow-specific logic
		engine.addStep(INITAFS, new Steps.CalculationStep(INITAFS, List.of(ONHAND, ROHM, LOST, OOBADJ), inputs -> {
			// Non-JEI: ONHAND - ROHM - LOST - MAX(OOBADJ, 0) then max(ZERO)
			BigDecimal result = BigDecimal.ZERO;
			BigDecimal onHand = inputs.get(ONHAND);
			BigDecimal rohm = inputs.get(ROHM);
			BigDecimal lost = inputs.get(LOST);
			BigDecimal oob = inputs.get(OOBADJ);
			BigDecimal oobA = oob.max(BigDecimal.ZERO);
			result = onHand.subtract(rohm);
			result = result.subtract(lost);
			result = result.subtract(oobA);
			return result;
		}, null, null, null, null),
				Map.of(CalculationFlow.JEI, new Steps.CalculationStep(INITAFS, List.of(ONHAND, LOST), inputs -> {
					// JEI: ONHAND - LOST
					return inputs.get(ONHAND).subtract(inputs.get(LOST));
				}, null, null, null, null)), null, false);

		// Commitment Fields
		engine.addStep(SNB, new Steps.SkulocFieldStep(SNB), Map.of(), null, false);
		engine.addStep(SNBX, new Steps.CalculationStep(SNBX, List.of(INITAFS, SNB), inputs -> {
			BigDecimal available = inputs.get(INITAFS);
			BigDecimal requested = inputs.get(SNB);
			return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(SNBA,
				new Steps.CalculationStep(SNBA, List.of(SNB, SNBX),
						inputs -> inputs.get(SNBX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(SNBX) : inputs.get(SNB),
						null, null, null, null),
				Map.of(), null, false);

		engine.addStep(DTCO, new Steps.SkulocFieldStep(DTCO), Map.of(), null, false);
		engine.addStep(DTCOX, new Steps.CalculationStep(DTCOX, List.of(INITAFS, SNB, SNBX, DTCO), inputs -> {
			BigDecimal initAfs = inputs.get(INITAFS);
			BigDecimal snb = inputs.get(SNB);
			BigDecimal snbx = inputs.get(SNBX);
			BigDecimal dtco = inputs.get(DTCO);

			// Calculate actual SNB used: constraint > 0 ? constraint : base
			BigDecimal snbActual = snbx.compareTo(BigDecimal.ZERO) > 0 ? snbx : snb;
			BigDecimal available = initAfs.subtract(snbActual).max(BigDecimal.ZERO);

			return available.compareTo(dtco) < 0 ? available : BigDecimal.ZERO;
		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(DTCOA, new Steps.CalculationStep(DTCOA, List.of(DTCO, DTCOX),
				inputs -> inputs.get(DTCOX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(DTCOX) : inputs.get(DTCO), null,
				null, null, null), Map.of(), null, false);

		engine.addStep(ROHP, new Steps.SkulocFieldStep(ROHP), Map.of(), null, false);
		engine.addStep(ROHPX,
				new Steps.CalculationStep(ROHPX, List.of(INITAFS, SNB, SNBX, DTCO, DTCOX, ROHP), inputs -> {
					BigDecimal initAfs = inputs.get(INITAFS);
					BigDecimal snbActual = inputs.get(SNBX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(SNBX)
							: inputs.get(SNB);
					BigDecimal dtcoActual = inputs.get(DTCOX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(DTCOX)
							: inputs.get(DTCO);
					BigDecimal rohp = inputs.get(ROHP);

					BigDecimal available = initAfs.subtract(snbActual).subtract(dtcoActual).max(BigDecimal.ZERO);
					return available.compareTo(rohp) < 0 ? available : BigDecimal.ZERO;
				}, null, null, null, null), Map.of(), null, false);
		engine.addStep(ROHPA, new Steps.CalculationStep(ROHPA, List.of(ROHP, ROHPX),
				inputs -> inputs.get(ROHPX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(ROHPX) : inputs.get(ROHP), null,
				null, null, null), Map.of(), null, false);

		// Hard Reserve Fields
		engine.addStep(DOTHRY, new Steps.SkulocFieldStep(DOTHRY), Map.of(), null, false);
		engine.addStep(DOTHRYX, new Steps.CalculationStep(DOTHRYX, List.of(RUNNING_AFS, DOTHRY), inputs -> {
			BigDecimal available = inputs.get(RUNNING_AFS);
			BigDecimal requested = inputs.get(DOTHRY);
			return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(DOTHRYZ, new Steps.CalculationStep(DOTHRYZ, List.of(DOTHRY, DOTHRYX, RUNNING_AFS), inputs -> {
			BigDecimal result = BigDecimal.ZERO;
			BigDecimal dotHRY = inputs.get(DOTHRY);
			BigDecimal dotHRYX = inputs.get(DOTHRYX);
			BigDecimal runningAFS = inputs.get(RUNNING_AFS);
			if (dotHRYX.compareTo(BigDecimal.ZERO) > 0) {
				result = dotHRYX;
			} else if (dotHRY.compareTo(runningAFS) < 0) {
				result = dotHRY;
			} else {
				result = BigDecimal.ZERO;
			}

			return result;

		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(DOTHRYA, new Steps.CalculationStep(DOTHRYA, List.of(DOTHRY, DOTHRYX),
				inputs -> inputs.get(DOTHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(DOTHRYX) : inputs.get(DOTHRY),
				null, null, null, null), Map.of(), null, false);

		engine.addStep(DOTHRN, new Steps.SkulocFieldStep(DOTHRN), Map.of(), null, false);
		engine.addStep(DOTHRNX, new Steps.CalculationStep(DOTHRNX, List.of(RUNNING_AFS, DOTHRN), inputs -> {
			BigDecimal available = inputs.get(RUNNING_AFS);
			BigDecimal requested = inputs.get(DOTHRN);
			return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(DOTHRNZ, new Steps.CalculationStep(DOTHRNZ, List.of(DOTHRN, DOTHRNX, RUNNING_AFS), inputs -> {
			BigDecimal result = BigDecimal.ZERO;
			BigDecimal dotHRN = inputs.get(DOTHRN);
			BigDecimal dotHRNX = inputs.get(DOTHRNX);
			BigDecimal runningAFS = inputs.get(RUNNING_AFS);
			if (dotHRNX.compareTo(BigDecimal.ZERO) > 0) {
				result = dotHRNX;
			} else if (dotHRN.compareTo(runningAFS) < 0) {
				result = dotHRN;
			} else {
				result = BigDecimal.ZERO;
			}

			return result;

		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(DOTHRNA, new Steps.CalculationStep(DOTHRNA, List.of(DOTHRN, DOTHRNX),
				inputs -> inputs.get(DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(DOTHRNX) : inputs.get(DOTHRN),
				null, null, null, null), Map.of(), null, false);

		engine.addStep(RETHRY, new Steps.SkulocFieldStep(RETHRY), Map.of(), null, false);
		engine.addStep(RETHRYX, new Steps.CalculationStep(RETHRYX, List.of(RUNNING_AFS, RETHRY), inputs -> {
			BigDecimal available = inputs.get(RUNNING_AFS);
			BigDecimal requested = inputs.get(RETHRY);
			return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(RETHRYZ,
				new Steps.CalculationStep(RETHRYZ, List.of(RETHRY, RETHRYX, RETHRYA, RUNNING_AFS), inputs -> {
					BigDecimal result = BigDecimal.ZERO;
					BigDecimal retHRY = inputs.get(RETHRY);
					BigDecimal retHRYX = inputs.get(RETHRYX);
					BigDecimal runningAFS = inputs.get(RUNNING_AFS);
					if (retHRYX.compareTo(BigDecimal.ZERO) > 0) {
						result = retHRYX;
					} else if (retHRY.compareTo(runningAFS) < 0) {
						result = retHRY;
					} else {
						result = BigDecimal.ZERO;
					}

					return result;

				}, null, null, null, null), Map.of(), null, false);
		engine.addStep(RETHRYA, new Steps.CalculationStep(RETHRYA, List.of(RETHRY, RETHRYX),
				inputs -> inputs.get(RETHRYX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(RETHRYX) : inputs.get(RETHRY),
				null, null, null, null), Map.of(), null, false);

		engine.addStep(RETHRN, new Steps.SkulocFieldStep(RETHRN), Map.of(), null, false);
		engine.addStep(RETHRNX, new Steps.CalculationStep(RETHRNX, List.of(RUNNING_AFS, RETHRN), inputs -> {
			BigDecimal available = inputs.get(RUNNING_AFS);
			BigDecimal requested = inputs.get(RETHRN);
			return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(RETHRNZ,
				new Steps.CalculationStep(RETHRNZ, List.of(RETHRN, RETHRNX, RETHRNA, RUNNING_AFS), inputs -> {
					BigDecimal result = BigDecimal.ZERO;
					BigDecimal retHRN = inputs.get(RETHRN);
					BigDecimal retHRNX = inputs.get(RETHRNX);
					BigDecimal runningAFS = inputs.get(RUNNING_AFS);
					if (retHRNX.compareTo(BigDecimal.ZERO) > 0) {
						result = retHRNX;
					} else if (retHRN.compareTo(runningAFS) < 0) {
						result = retHRN;
					} else {
						result = BigDecimal.ZERO;
					}

					return result;

				}, null, null, null, null), Map.of(), null, false);
		engine.addStep(RETHRNA, new Steps.CalculationStep(RETHRNA, List.of(RETHRN, RETHRNX),
				inputs -> inputs.get(RETHRNX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(RETHRNX) : inputs.get(RETHRN),
				null, null, null, null), Map.of(), null, false);

		engine.addStep(HLDHR, new Steps.SkulocFieldStep(HLDHR), Map.of(), null, false);
		engine.addStep(HLDHRX, new Steps.CalculationStep(HLDHRX, List.of(RUNNING_AFS, HLDHR), inputs -> {
			BigDecimal available = inputs.get(RUNNING_AFS);
			BigDecimal requested = inputs.get(HLDHR);
			return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(HLDHRZ,
				new Steps.CalculationStep(HLDHRZ, List.of(HLDHR, HLDHRX, HLDHRA, RUNNING_AFS), inputs -> {
					BigDecimal result = BigDecimal.ZERO;
					BigDecimal heldHR = inputs.get(HLDHR);
					BigDecimal heldHRX = inputs.get(HLDHRX);
					BigDecimal runningAFS = inputs.get(RUNNING_AFS);
					if (heldHRX.compareTo(BigDecimal.ZERO) > 0) {
						result = heldHRX;
					} else if (heldHR.compareTo(runningAFS) < 0) {
						result = heldHR;
					} else {
						result = BigDecimal.ZERO;
					}

					return result;

				}, null, null, null, null), Map.of(), null, false);
		engine.addStep(HLDHRA, new Steps.CalculationStep(HLDHRA, List.of(HLDHR, HLDHRX),
				inputs -> inputs.get(HLDHRX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(HLDHRX) : inputs.get(HLDHR),
				null, null, null, null), Map.of(), null, false);

		// Soft Reserve Fields
		engine.addStep(DOTRSV, new Steps.SkulocFieldStep(DOTRSV), Map.of(), null, false);
		engine.addStep(DOTRSVX, new Steps.CalculationStep(DOTRSVX, List.of(RUNNING_AFS, DOTRSV), inputs -> {
			BigDecimal available = inputs.get(RUNNING_AFS);
			BigDecimal requested = inputs.get(DOTRSV);
			return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(DOTRSVZ, new Steps.CalculationStep(DOTRSVZ, List.of(DOTRSV, DOTRSVX, RUNNING_AFS), inputs -> {
			BigDecimal result = BigDecimal.ZERO;
			BigDecimal dotRSV = inputs.get(DOTRSV);
			BigDecimal dotRSVX = inputs.get(DOTRSVX);
			BigDecimal runningAFS = inputs.get(RUNNING_AFS);
			if (dotRSVX.compareTo(BigDecimal.ZERO) > 0) {
				result = dotRSVX;
			} else if (dotRSV.compareTo(runningAFS) < 0) {
				result = dotRSV;
			} else {
				result = BigDecimal.ZERO;
			}

			return result;

		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(DOTRSVA, new Steps.CalculationStep(DOTRSVA, List.of(DOTRSV, DOTRSVX),
				inputs -> inputs.get(DOTRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(DOTRSVX) : inputs.get(DOTRSV),
				null, null, null, null), Map.of(), null, false);

		engine.addStep(RETRSV, new Steps.SkulocFieldStep(RETRSV), Map.of(), null, false);
		engine.addStep(RETRSVX, new Steps.CalculationStep(RETRSVX, List.of(RUNNING_AFS, RETRSV), inputs -> {
			BigDecimal available = inputs.get(RUNNING_AFS);
			BigDecimal requested = inputs.get(RETRSV);
			return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(RETRSVZ,
				new Steps.CalculationStep(RETRSVZ, List.of(RETRSV, RETRSVX, RETRSVA, RUNNING_AFS), inputs -> {
					BigDecimal result = BigDecimal.ZERO;
					BigDecimal retRSV = inputs.get(RETRSV);
					BigDecimal retRSVX = inputs.get(RETRSVX);
					BigDecimal runningAFS = inputs.get(RUNNING_AFS);
					if (retRSVX.compareTo(BigDecimal.ZERO) > 0) {
						result = retRSVX;
					} else if (retRSV.compareTo(runningAFS) < 0) {
						result = retRSV;
					} else {
						result = BigDecimal.ZERO;
					}

					return result;

				}, null, null, null, null), Map.of(), null, false);
		engine.addStep(RETRSVA, new Steps.CalculationStep(RETRSVA, List.of(RETRSV, RETRSVX),
				inputs -> inputs.get(RETRSVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(RETRSVX) : inputs.get(RETRSV),
				null, null, null, null), Map.of(), null, false);

		// Other Input Fields
		engine.addStep(DOTOUTB, new Steps.SkulocFieldStep(DOTOUTB), Map.of(), null, false);
		engine.addStep(AOUTBV, new Steps.CalculationStep(AOUTBV, List.of(DOTOUTB, DOTATS, BYCL), (inputs, special) -> {
			String buyerClass = (String) special;
			if ("R".equals(buyerClass) || "W".equals(buyerClass)) {
				BigDecimal dotoutb = inputs.get(DOTOUTB);
				BigDecimal dotats = inputs.get(DOTATS);
				return dotoutb.subtract(dotats).max(BigDecimal.ZERO);
			}
			return BigDecimal.ZERO;
		}, (context, flow) -> {
			ReserveCalcStep step = context.getStep(flow, BYCL);
			if (step instanceof Steps.SkulocStringFieldStep) {
				return ((Steps.SkulocStringFieldStep) step).getStringValue();
			}
			return "";
		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(AOUTBVX, new Steps.CalculationStep(AOUTBVX, List.of(RUNNING_AFS, AOUTBV), inputs -> {
			BigDecimal available = inputs.get(RUNNING_AFS);
			BigDecimal requested = inputs.get(AOUTBV);
			return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(AOUTBVZ, new Steps.CalculationStep(AOUTBVZ, List.of(AOUTBV, AOUTBVX, RUNNING_AFS), inputs -> {
			BigDecimal result = BigDecimal.ZERO;
			BigDecimal aOUTBV = inputs.get(AOUTBV);
			BigDecimal aOutBVX = inputs.get(AOUTBVX);
			BigDecimal runningAFS = inputs.get(RUNNING_AFS);
			if (aOutBVX.compareTo(BigDecimal.ZERO) > 0) {
				result = aOutBVX;
			} else if (aOUTBV.compareTo(runningAFS) < 0) {
				result = aOUTBV;
			} else {
				result = BigDecimal.ZERO;
			}

			return result;

		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(AOUTBVA, new Steps.CalculationStep(AOUTBVA, List.of(AOUTBV, AOUTBVX),
				inputs -> inputs.get(AOUTBVX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(AOUTBVX) : inputs.get(AOUTBV),
				null, null, null, null), Map.of(), null, false);

		engine.addStep(NEED, new Steps.SkulocFieldStep(NEED), Map.of(), null, false);
		engine.addStep(ANEED, new Steps.CalculationStep(ANEED, List.of(NEED, RETAILATS), inputs -> {
			BigDecimal need = inputs.get(NEED);
			BigDecimal retailats = inputs.get(RETAILATS);
			return need.subtract(retailats).max(BigDecimal.ZERO);
		}, null, null, null, null), Map.of(), null, false);

		engine.addStep(NEEDX, new Steps.CalculationStep(NEEDX, List.of(RUNNING_AFS, ANEED), inputs -> {
			BigDecimal available = inputs.get(RUNNING_AFS);
			BigDecimal requested = inputs.get(ANEED);
			return available.compareTo(requested) < 0 ? available : BigDecimal.ZERO;
		}, null, null, null, null), Map.of(), null, false);
		engine.addStep(ANEEDZ,
				new Steps.CalculationStep(ANEEDZ, List.of(NEED, ANEED, NEEDX, NEEDA, RUNNING_AFS), inputs -> {
					BigDecimal result = BigDecimal.ZERO;
					BigDecimal aNeed = inputs.get(ANEED);
					BigDecimal aNeedX = inputs.get(NEEDX);
					BigDecimal runningAFS = inputs.get(RUNNING_AFS);
					if (aNeedX.compareTo(BigDecimal.ZERO) > 0) {
						result = aNeedX;
					} else if (aNeed.compareTo(runningAFS) < 0) {
						result = aNeed;
					} else {
						result = BigDecimal.ZERO;
					}

					return result;

				}, null, null, null, null), Map.of(), null, false);
		engine.addStep(NEEDA, new Steps.CalculationStep(NEEDA, List.of(ANEED, NEEDX),
				inputs -> inputs.get(NEEDX).compareTo(BigDecimal.ZERO) > 0 ? inputs.get(NEEDX) : inputs.get(ANEED),
				null, null, null, null), Map.of(), null, false);
		// ===== PHASE 3: RUNNING INVENTORY SETUP =====

		// RUNNING_AFS - Tracks remaining inventory as allocations are made
		engine.addStep(RUNNING_AFS,
				new Steps.RunningWithInitialStep(RUNNING_AFS, INITAFS, List.of(SNBA, DTCOA, ROHPA, DOTHRYA, DOTHRNA,
						RETHRYA, RETHRNA, HLDHRA, DOTRSVA, RETRSVA, AOUTBVA, NEEDA), false, (running, allocated) -> {
							Logger logger = LoggerFactory.getLogger("Running AFS Formula");

							logger.info("Inside Running AFS Formula, incoming values running {} - allocated {}",
									running, allocated);
							BigDecimal result = running.subtract(allocated).max(BigDecimal.ZERO);
							logger.info("Calcualted values new running {}", result);

							return result;
						}),
				Map.of(), null, false);

		// ===== PHASE 2: CORE CALCULATIONS =====

		// UNCOMAFS - Uncommitted after commitments
		engine.addStep(UNCOMAFS, new Steps.CalculationStep(UNCOMAFS, List.of(INITAFS, SNB, DTCO, ROHP), inputs -> {
			BigDecimal result = inputs.get(INITAFS).subtract(inputs.get(SNB)).subtract(inputs.get(DTCO))
					.subtract(inputs.get(ROHP));
			return result.max(BigDecimal.ZERO);
		}, null, null, null, null), Map.of(), null, false);

		// ===== PHASE 4: SEQUENTIAL CONSTRAINT PROCESSING =====

		// Commitment Constraints

		// Hard Reserve Constraints (Sequential Processing)

		// Soft Reserve Constraints

		// Special Calculations
		// AOUTBV with buyer class logic

		// ===== PHASE 6: ACTUAL VALUE FIELDS ("A" Suffix Pattern) =====

		// ===== PHASE 5: ACCUMULATION FIELDS =====

		// DOTATS - Accumulates DOT allocations (starts at ZERO)
		engine.addStep(DOTATS, new Steps.RunningCalculationStep(DOTATS, BigDecimal.ZERO, // Start at 0
				List.of(DOTHRYZ, DOTRSVZ, AOUTBVZ), // Trigger on base fields for now
				false, (running, allocated) -> {

					Logger logger = LoggerFactory.getLogger("Running DOTATS Formula");

					logger.info("Inside Running DOTATS Formula, incoming values running {} - allocated {}", running,
							allocated);

					BigDecimal result = running.add(allocated);
					logger.info("Calcualted values new running DOT ATS {}", result);

					return result;
				}), Map.of(), null, false);
		// RETAILATS - Accumulates RETAIL allocations (starts at ZERO)
		engine.addStep(RETAILATS, new Steps.RunningCalculationStep(RETAILATS, BigDecimal.ZERO, // Start at 0
				List.of(RETHRYZ, RETRSVZ, ANEEDZ), false, (running, allocated) -> {

					Logger logger = LoggerFactory.getLogger("Running RETAILATS Formula");

					logger.info("Inside Running RETAILATS Formula, incoming values running {} - allocated {}", running,
							allocated);

					BigDecimal result = running.add(allocated);
					logger.info("Calcualted values new running Retail ATS {}", result);

					return result;
				}), Map.of(), null, false);

		// ===== PHASE 7: SUMMARY/AGGREGATE FIELDS =====

		engine.addStep(COMMITTED, new Steps.CalculationStep(COMMITTED, List.of(SNB, DTCO, ROHP), inputs -> {
			BigDecimal snb = inputs.get(SNB);
			BigDecimal dtco = inputs.get(DTCO);
			BigDecimal rohp = inputs.get(ROHP);
			BigDecimal commited = snb.add(dtco).add(rohp);
			return commited;
		}, null, null, null, null), Map.of(), null, false); // isDynamic = true

		// Final value ofrunning inventory
		engine.addStep(UNCOMMIT, new Steps.CalculationStep(UNCOMMIT, List.of(RUNNING_AFS),
				inputs -> inputs.get(RUNNING_AFS), null, null, null, null), Map.of(), null, false);

		engine.addStep(UNCOMMHR, new Steps.CalculationStep(UNCOMMHR, List.of(DOTHRNZ, RETHRNZ, HLDHRZ), inputs -> {
			BigDecimal dotNo = inputs.get(DOTHRNZ);
			BigDecimal retNo = inputs.get(RETHRNZ);
			BigDecimal held = inputs.get(HLDHRZ);
			return dotNo.add(retNo).add(held);
		}, null, null, null, null), Map.of(), null, false); // isDynamic = true

		// ===== PHASE 8: OUTPUT FIELDS =====

// TODO : Another mechanism would be to have a step type which also includes base step values        

		// OMSSUP with flow-specific logic
		engine.addStep(OMSSUP, new Steps.CalculationStep(OMSSUP, List.of(INITAFS, DOTATS, DTCOA), inputs -> {
			BigDecimal afs = inputs.get(INITAFS);
			if (afs.compareTo(BigDecimal.ZERO) < 0)
				return BigDecimal.ZERO;

//                            BigDecimal dtcoAct = inputs.get(DTCOX).compareTo(BigDecimal.ZERO) > 0 ? 
//                                    inputs.get(DTCOX) : inputs.get(DTCO);
			BigDecimal dotATS = inputs.get(DOTATS);
			BigDecimal dtcoAct = inputs.get(DTCOA); // Use A field as it already checks for constraints
			return dotATS.add(dtcoAct);
		}, null, null, null, null), Map.of(CalculationFlow.JEI,
				new Steps.CalculationStep(OMSSUP, List.of(INITAFS, DOTATS, DTCOA, SNBA, DOTHRNA, UNCOMAFS), inputs -> {
//                                    BigDecimal afs = inputs.get(INITAFS);
//                                    if (afs.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
//                                    
//                                    BigDecimal dtcoAct = inputs.get(DTCOX).compareTo(BigDecimal.ZERO) > 0 ? 
//                                            inputs.get(DTCOX) : inputs.get(DTCO);
//                                    BigDecimal snbAct = inputs.get(SNBX).compareTo(BigDecimal.ZERO) > 0 ? 
//                                            inputs.get(SNBX) : inputs.get(SNB);
//                                    BigDecimal dhrnAct = inputs.get(DOTHRNX).compareTo(BigDecimal.ZERO) > 0 ? 
//                                            inputs.get(DOTHRNX) : inputs.get(DOTHRN);
//                                    
//                                    BigDecimal result = inputs.get(DOTATS).add(dtcoAct).add(snbAct).add(dhrnAct);
//                                    
//                                    // Special JEI logic when UNCOMAFS <= 0
//                                    if (inputs.get(UNCOMAFS).compareTo(BigDecimal.ZERO) <= 0) {
//                                        result = dtcoAct.add(snbAct);
//                                    }
//                                    
//                                    return result;

					BigDecimal afs = inputs.get(INITAFS);
					if (afs.compareTo(BigDecimal.ZERO) < 0)
						return BigDecimal.ZERO;

					// Use A field as it already checks for constraints
					BigDecimal dotATS = inputs.get(DOTATS);
					BigDecimal dtcoAct = inputs.get(DTCOA);
					BigDecimal snbA = inputs.get(SNBA);
					BigDecimal dotHRNA = inputs.get(DOTHRNA);
					BigDecimal uncomAFS = inputs.get(UNCOMAFS);

					BigDecimal lResult = dotATS.add(dtcoAct).add(snbA).add(dotHRNA).add(uncomAFS);

					if (uncomAFS.compareTo(BigDecimal.ZERO) < 0)
						lResult = dtcoAct.add(snbA);

					return lResult;
				}, null, null, null, null)), null, false);

		// RETFINAL with flow-specific logic
		engine.addStep(RETFINAL, new Steps.CalculationStep(RETFINAL, List.of(INITAFS, RETAILATS), inputs -> {
			BigDecimal afs = inputs.get(INITAFS);
			BigDecimal retATS = inputs.get(RETAILATS);

			if (afs.compareTo(BigDecimal.ZERO) < 0) {
				return BigDecimal.ZERO;
			}
			BigDecimal retFinal = retATS;

			return retFinal;
		}, null, null, null, null), Map.of(CalculationFlow.JEI,
				new Steps.CalculationStep(RETFINAL, List.of(INITAFS, RETAILATS, RETHRNA, ROHPA, HLDHRA), inputs -> {
					BigDecimal afs = inputs.get(INITAFS);
					BigDecimal retFinal = afs;
					;

					BigDecimal rethrnActA = inputs.get(RETHRNA);
					BigDecimal rohpActA = inputs.get(ROHPA);
					BigDecimal heldActA = inputs.get(HLDHRA);

					retFinal.add(rethrnActA).add(rohpActA).add(heldActA);

					return retFinal;
				}, null, null, null, null), CalculationFlow.FRM,
				new Steps.CalculationStep(RETFINAL, List.of(INITAFS, RETAILATS, AOUTBVA), inputs -> {
					BigDecimal afs = inputs.get(INITAFS);
					BigDecimal retATS = inputs.get(RETAILATS);
					BigDecimal aoutBVA = inputs.get(AOUTBVA);

					if (afs.compareTo(BigDecimal.ZERO) < 0) {
						return BigDecimal.ZERO;
					}
					BigDecimal retFinal = retATS.add(aoutBVA);

					return retFinal;
				}, null, null, null, null)), null, false);

		// OMSFINAL with flow-specific logic
		// OMSFINAL with buyer class logic
		// JEI flow: Always returns OMSSUP (ignores buyer class)
		// ALTERNATE STEPS for other flows
		// JEI logic: Simply return OMSSUP value
		// Note: FRM flow will use the main (OMS) step since no alternate provided

		engine.addStep(OMSFINAL, new Steps.CalculationStep(OMSFINAL, List.of(OMSSUP, BYCL), (inputs, special) -> {
			BigDecimal omsSup = inputs.get(OMSSUP);
			if (omsSup.compareTo(BigDecimal.ZERO) == 0) {
				return BigDecimal.ZERO;
			}

			String buyerClass = (String) special;
			if ("R".equals(buyerClass) || "W".equals(buyerClass)) {
				return omsSup;
			}
			return BigDecimal.ZERO;
		}, (context, flow) -> {
			ReserveCalcStep step = context.getStep(flow, BYCL);
			if (step instanceof Steps.SkulocStringFieldStep) {
				return ((Steps.SkulocStringFieldStep) step).getStringValue();
			}
			return "";
		}, null, null, null, null), Map.of(CalculationFlow.JEI, new Steps.CalculationStep(OMSFINAL, List.of(OMSSUP),
				inputs -> inputs.get(OMSSUP), null, null, null, null)), null, false);
	}
}