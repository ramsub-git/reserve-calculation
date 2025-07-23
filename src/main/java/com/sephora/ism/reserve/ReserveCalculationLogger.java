package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReserveCalculationLogger {

	private static final Logger logger = LoggerFactory.getLogger(ReserveCalculationLogger.class);

	// Private constructor to prevent instantiation
	private ReserveCalculationLogger() {
		// Utility class - should not be instantiated
	}

	private static final String SEPARATOR = "=".repeat(120);
	private static final String THIN_SEPARATOR = "-".repeat(120);
	private static final String DOTS = ".".repeat(60);

	// ANSI color codes for better visibility
	private static final String RESET = "\u001B[0m";
	private static final String GREEN = "\u001B[32m";
	private static final String YELLOW = "\u001B[33m";
	private static final String RED = "\u001B[31m";
	private static final String BLUE = "\u001B[34m";
	private static final String CYAN = "\u001B[36m";
	private static final String MAGENTA = "\u001B[35m";
	private static final String BOLD = "\u001B[1m";

	/**
	 * Logs the initial state including InitialValueWrapper values
	 */
	public static void logInitialState(ReserveCalcContext context) {
		logger.info("\n" + SEPARATOR);
		logger.info(BLUE + BOLD + "INITIAL CONTEXT STATE" + RESET);
		logger.info(SEPARATOR);

		// Log InitialValueWrapper values if present
		if (context.getInitialValueWrapper() != null) {
			logger.info(CYAN + "Initial Values from Wrapper:" + RESET);
			Map<ReserveField, BigDecimal> initialValues = context.getInitialValueWrapper().getValues();

			// Group by category for better readability
			Map<ReserveField.FieldCategory, List<Map.Entry<ReserveField, BigDecimal>>> grouped = initialValues
					.entrySet().stream().collect(Collectors.groupingBy(e -> e.getKey().getCategory()));

			grouped.forEach((category, entries) -> {
				logger.info("  " + YELLOW + category.getDescription() + ":" + RESET);
				entries.forEach(entry -> {
					logger.info("    %-25s : %10s%n", entry.getKey(), formatValue(entry.getValue()));
				});
			});
			logger.info("");
		}

		logger.info(SEPARATOR + "\n");
	}

	/**
	 * Enhanced step calculation logging with constraint tracking
	 */
	public static void logStepCalculation(int stepIndex, Map<CalculationFlow, ReserveCalcStep> currentSteps,
			ReserveCalcContext context) {

		// Get the main step name
		ReserveField fieldName = currentSteps.values().iterator().next().getFieldName();

		// logger.info("\n" + THIN_SEPARATOR);
		// logger.info(GREEN + BOLD + "STEP %d: %s" + RESET, stepIndex + 1, fieldName);

		// Add field description if available
		// logger.info(" (%s)%n", fieldName.getDescription());
		// logger.info(THIN_SEPARATOR);

		// Log calculation details for each flow
		for (CalculationFlow flow : CalculationFlow.values()) {
			if (currentSteps.containsKey(flow)) {
				ReserveCalcStep step = currentSteps.get(flow);
				logStepDetails(flow, step, context);
			}
		}

		// Special handling for constraint fields
		if (fieldName.isConstraint()) {
			logConstraintCalculation(fieldName, context);
		}

		// Log running calculations if affected
		logRunningCalculationStatus(context);

		// Log the final value stored in context (OMS flow as default)
		BigDecimal finalValue = context.getCurrentValue(CalculationFlow.OMS, fieldName);
		// logger.info("\n%sFINAL VALUE%s: %s = %s%s%s%n",
		// BLUE + BOLD, RESET, fieldName, YELLOW, formatValue(finalValue), RESET);

		// Show calculation trace for debugging
		if (fieldName.name().endsWith("X") || fieldName.name().endsWith("A")) {
			showCalculationTrace(fieldName, context);
		}
	}

	/**
	 * Log detailed information about a step execution
	 */
	private static void logStepDetails(CalculationFlow flow, ReserveCalcStep<?> step, ReserveCalcContext context) {
		// logger.info("\n%s[%s]%s ", CYAN, flow, RESET);

		// Show step type
		String stepType = step.getClass().getSimpleName();
		// logger.info("{} ", stepType);

		// Show dependencies and their values
		if (!step.getDependencyFields().isEmpty()) {
			// logger.info("\n Dependencies:");
			for (ReserveField dep : step.getDependencyFields()) {
				BigDecimal depValue = context.getCurrentValue(flow, dep);
				logger.info("    %-20s = %s%n", dep, formatValue(depValue));
			}
		}

		// Show calculation result
		BigDecimal currentValue = context.getCurrentValue(flow, step.getFieldName());
		BigDecimal previousValue = context.getPreviousValue(flow, step.getFieldName());

		// logger.info(" Result: {}", formatValue(currentValue));

		if (!previousValue.equals(currentValue)) {
			// logger.info(" (changed from {})", formatValue(previousValue));
		}

		// logger.info("");
	}

	/**
	 * Special logging for constraint calculations
	 */
	private static void logConstraintCalculation(ReserveField constraintField, ReserveCalcContext context) {
		logger.info("\n" + MAGENTA + "Constraint Calculation Details:" + RESET);

		// Get base field
		ReserveField baseField = constraintField.getBaseField();
		if (baseField != null) {
			BigDecimal baseValue = context.get(baseField);
			BigDecimal constraintValue = context.get(constraintField);

			logger.info("  Base field (%s): %s%n", baseField, formatValue(baseValue));
			logger.info("  Available inventory: %s%n", formatValue(getAvailableInventory(context, constraintField)));
			logger.info("  Constraint result: %s%n", formatValue(constraintValue));

			if (constraintValue.compareTo(BigDecimal.ZERO) > 0) {
				logger.info("  %sINVENTORY CONSTRAINED!%s Cannot fulfill full amount of %s%n", RED, RESET,
						formatValue(baseValue));
			}
		}
	}

	/**
	 * Calculate available inventory at the point of constraint check
	 */
	private static BigDecimal getAvailableInventory(ReserveCalcContext context, ReserveField constraintField) {
		// This would calculate based on the constraint field
		// For now, simplified version
		BigDecimal running = context.get(ReserveField.RUNNING_AFS);
		if (running != null && running.compareTo(BigDecimal.ZERO) > 0) {
			return running;
		}

		// Fallback to manual calculation
		BigDecimal initAfs = context.get(ReserveField.INITAFS);
		// Subtract previously consumed amounts...
		return initAfs;
	}

	/**
	 * Show calculation trace for debugging
	 */
	private static void showCalculationTrace(ReserveField field, ReserveCalcContext context) {
		logger.info("\n" + DOTS);
		logger.info(YELLOW + "Calculation Trace for " + field + ":" + RESET);

		// Show the calculation path
		if (field.name().endsWith("X")) {
			// It's a constraint field
			ReserveField baseField = field.getBaseField();
			logger.info("  Base value (%s): %s%n", baseField, formatValue(context.get(baseField)));
			logger.info("  Constraint value: %s%n", formatValue(context.get(field)));
		} else if (field.name().endsWith("A")) {
			// It's an actual field
			String baseName = field.name().substring(0, field.name().length() - 1);
			String constraintName = baseName + "X";
			try {
				ReserveField baseField = ReserveField.valueOf(baseName);
				ReserveField constraintField = ReserveField.valueOf(constraintName);

				BigDecimal base = context.get(baseField);
				BigDecimal constraint = context.get(constraintField);

				logger.info("  Base value (%s): %s%n", baseField, formatValue(base));
				logger.info("  Constraint (%s): %s%n", constraintField, formatValue(constraint));
				logger.info("  Actual used: %s%n",
						formatValue(constraint.compareTo(BigDecimal.ZERO) > 0 ? constraint : base));
			} catch (IllegalArgumentException e) {
				// Field doesn't follow pattern
			}
		}
		logger.info(DOTS);
	}

	/**
	 * Enhanced running calculation status
	 */
	private static void logRunningCalculationStatus(ReserveCalcContext context) {
		// BigDecimal runningAfs = context.get(ReserveField.RUNNING_AFS);
		// if (runningAfs != null) {
		// logger.info("\n" + YELLOW + "Running Inventory Status:" + RESET);
		// logger.info(" RUNNING_AFS: %s%n", formatValue(runningAfs));

		// // Show if running low
		// if (runningAfs.compareTo(new BigDecimal("100")) < 0) {
		// logger.info(" %sWARNING: Running inventory is low!%s%n", RED, RESET);
		// }
		// }
	}

	/**
	 * Enhanced running inventory calculation logging
	 */
	private static void logRunningInventory(ReserveCalcContext context) {
		logger.info("\n" + CYAN + BOLD + "Running Inventory Breakdown:" + RESET);

		BigDecimal initAfs = context.get(ReserveField.INITAFS);
		BigDecimal running = initAfs;

		logger.info("  %-30s : %10s%n", "Starting (INITAFS)", formatValue(initAfs));
		logger.info("  " + "-".repeat(45));

		// Track each deduction
		List<String[]> deductions = new ArrayList<>();

		// Commitments
		addDeduction(deductions, "SNB", context, ReserveField.SNB, ReserveField.SNBX);
		addDeduction(deductions, "DTCO", context, ReserveField.DTCO, ReserveField.DTCOX);
		addDeduction(deductions, "ROHP", context, ReserveField.ROHP, ReserveField.ROHPX);

		// Process deductions
		for (String[] deduction : deductions) {
			String name = deduction[0];
			BigDecimal amount = new BigDecimal(deduction[1]);
			if (amount.compareTo(BigDecimal.ZERO) > 0) {
				running = running.subtract(amount);
				logger.info("  %-30s : -%9s = %10s%n", "After " + name, formatValue(amount), formatValue(running));
			}
		}

		// Check against UNCOMAFS
		BigDecimal uncomafs = context.get(ReserveField.UNCOMAFS);
		logger.info("  " + "=".repeat(45));
		logger.info("  %-30s : %10s%n", "Calculated", formatValue(running));
		logger.info("  %-30s : %10s%n", "UNCOMAFS", formatValue(uncomafs));

		if (!running.equals(uncomafs)) {
			logger.info("  %sDISCREPANCY: %s%s%n", RED + BOLD, formatValue(running.subtract(uncomafs)), RESET);
		}

		// Continue with reserves...
		logger.info("\n  Reserves Applied:");
		running = uncomafs;

		// All reserve types
		ReserveField[][] reserves = { { ReserveField.DOTHRY, ReserveField.DOTHRYX },
				{ ReserveField.DOTHRN, ReserveField.DOTHRNX }, { ReserveField.RETHRY, ReserveField.RETHRYX },
				{ ReserveField.RETHRN, ReserveField.RETHRNX }, { ReserveField.HLDHR, ReserveField.HLDHRX },
				{ ReserveField.DOTRSV, ReserveField.DOTRSVX }, { ReserveField.RETRSV, ReserveField.RETRSVX },
				{ ReserveField.AOUTBV, ReserveField.AOUTBVX }, { ReserveField.ANEED, ReserveField.NEEDX } };

		for (ReserveField[] pair : reserves) {
			BigDecimal value = getActualValue(context, pair[0], pair[1]);
			if (value.compareTo(BigDecimal.ZERO) > 0) {
				running = running.subtract(value);
				String color = running.compareTo(BigDecimal.ZERO) < 0 ? RED : "";
				logger.info("  %-30s : -%9s = %s%10s%s%n", "After " + pair[0], formatValue(value), color,
						formatValue(running), RESET);
			}
		}

		logger.info("  " + "=".repeat(45));
		logger.info("  %s%-30s : %10s%s%n", running.compareTo(BigDecimal.ZERO) < 0 ? RED + BOLD : GREEN,
				"Final Running Inventory", formatValue(running), RESET);
	}

	/**
	 * Helper to add deduction info
	 */
	private static void addDeduction(List<String[]> deductions, String name, ReserveCalcContext context,
			ReserveField base, ReserveField constraint) {
		BigDecimal value = getActualValue(context, base, constraint);
		deductions.add(new String[] { name, value.toString() });
	}

	/**
	 * Get actual value (constraint if > 0, otherwise base)
	 */
	private static BigDecimal getActualValue(ReserveCalcContext context, ReserveField base, ReserveField constraint) {
		BigDecimal constraintValue = context.get(constraint);
		BigDecimal baseValue = context.get(base);

		if (constraintValue == null)
			constraintValue = BigDecimal.ZERO;
		if (baseValue == null)
			baseValue = BigDecimal.ZERO;

		return constraintValue.compareTo(BigDecimal.ZERO) > 0 ? constraintValue : baseValue;
	}

	/**
	 * Enhanced final summary with validation checks
	 */
	public static void logFinalSummary(ReserveCalcContext context) {
		logger.info("\n" + SEPARATOR);
		logger.info(GREEN + BOLD + "FINAL CALCULATION SUMMARY" + RESET);
		logger.info(SEPARATOR);

		// Key inputs
		logger.info("\n" + CYAN + "Key Inputs:" + RESET);
		logger.info("ONHAND: {}", formatValue(context.get(ReserveField.ONHAND)));
		logger.info("ROHM: {}", formatValue(context.get(ReserveField.ROHM)));
		logger.info("LOST: {}", formatValue(context.get(ReserveField.LOST)));
		logger.info("OOBADJ: {}", formatValue(context.get(ReserveField.OOBADJ)));

		// Key calculations
		logger.info("\n" + CYAN + "Key Calculations:" + RESET);
		logger.info("INITAFS: {}", formatValue(context.get(ReserveField.INITAFS)));
		logger.info("UNCOMAFS: {}", formatValue(context.get(ReserveField.UNCOMAFS)));
		logger.info("@DOTATS: {}", formatValue(context.get(ReserveField.DOTATS)));
		logger.info("@RETAILATS: {}", formatValue(context.get(ReserveField.RETAILATS)));
		logger.info("@UNCOMMIT: {}", formatValue(context.get(ReserveField.UNCOMMIT)));
		logger.info("@COMMITTED: {}", formatValue(context.get(ReserveField.COMMITTED)));

		// Final outputs
		logger.info("\n" + CYAN + "Final Outputs:" + RESET);
		logger.info("@OMSFINAL: {}", formatValue(context.get(ReserveField.OMSFINAL)));
		logger.info("@RETFINAL: {}", formatValue(context.get(ReserveField.RETFINAL)));

		// Validation checks
		logger.info("\n" + CYAN + "Validation Checks:" + RESET);
		performValidationChecks(context);

		logger.info("\n" + SEPARATOR);
	}

	/**
	 * Perform validation checks on final results
	 */
	private static void performValidationChecks(ReserveCalcContext context) {
		boolean allPassed = true;

		// Check 1: Total allocation shouldn't exceed INITAFS
		BigDecimal initAfs = context.get(ReserveField.INITAFS);
		BigDecimal totalAllocated = context.get(ReserveField.COMMITTED).add(context.get(ReserveField.DOTATS))
				.add(context.get(ReserveField.RETAILATS));

		if (totalAllocated.compareTo(initAfs) > 0) {
			logger.info("✗ FAILED: Total allocated ({}) > INITAFS ({})", formatValue(totalAllocated),
					formatValue(initAfs));
			allPassed = false;
		} else {
			logger.info("✓ PASSED: Total allocation within limits");
		}

		// Check 2: No negative values
		if (context.get(ReserveField.UNCOMMIT).compareTo(BigDecimal.ZERO) < 0) {
			logger.info("✗ FAILED: Negative UNCOMMIT value");
			allPassed = false;
		} else {
			logger.info("✓ PASSED: No negative uncommitted inventory");
		}

		if (allPassed) {
			logger.info("\nAll validation checks PASSED");
		} else {
			logger.info("\nSome validation checks FAILED");
		}
	}

	/**
	 * Format BigDecimal for display
	 */
	private static String formatValue(BigDecimal value) {
		if (value == null) {
			return "null";
		}
		return value.stripTrailingZeros().toPlainString();
	}

	/**
	 * Enhanced debug logging for specific calculations
	 */
	public static void debugStepCalculation(ReserveField stepName, Map<ReserveField, BigDecimal> inputs,
			BigDecimal result, String formula) {
		logger.info("\n" + YELLOW + ">>> DEBUG: " + stepName + " <<<" + RESET);
		logger.info("Formula: {}", formula);
		logger.info("Inputs:");
		inputs.forEach((field, value) -> {
			logger.info("{} = {}", field, formatValue(value));
		});
		logger.info("Result: {}", formatValue(result));
		logger.info(DOTS);
	}

	/**
	 * Log running total history for specific fields
	 */
	public static void logRunningTotalHistory(ReserveCalcContext context, ReserveField... fields) {
		Map<ReserveField, List<BigDecimal>> history = context.getRunningTotalHistory();

		if (history.isEmpty()) {
			return;
		}

		logger.info("\n" + BLUE + BOLD + "Running Total History:" + RESET);
		for (ReserveField field : fields) {
			if (history.containsKey(field)) {
				List<BigDecimal> values = history.get(field);
				logger.info("{}:", field);
				for (int i = 0; i < values.size(); i++) {
					logger.info("Step {}: {}", i + 1, formatValue(values.get(i)));
				}
			}
		}
	}
}