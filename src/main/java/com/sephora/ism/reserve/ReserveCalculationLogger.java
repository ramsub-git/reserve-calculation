package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class ReserveCalculationLogger {

    private static final String SEPARATOR = "=".repeat(120);
    private static final String THIN_SEPARATOR = "-".repeat(120);

    // ANSI color codes for better visibility
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String MAGENTA = "\u001B[35m";

    /**
     * Logs the initial state including InitialValueWrapper values
     */
    public static void logInitialState(ReserveCalcContext context) {
        System.out.println("\n" + SEPARATOR);
        System.out.println(BLUE + "INITIAL CONTEXT STATE" + RESET);
        System.out.println(SEPARATOR);

        // Log InitialValueWrapper values if present
        if (context.getInitialValueWrapper() != null) {
            System.out.println(CYAN + "Initial Values from Wrapper:" + RESET);
            Map<ReserveField, BigDecimal> initialValues = context.getInitialValueWrapper().getValues();
            initialValues.forEach((field, value) -> {
                System.out.printf("  %-25s : %10s%n", field, formatValue(value));
            });
            System.out.println();
        }

        // Log current context values
        System.out.println(CYAN + "Current Context Values:" + RESET);
        Map<ReserveField, BigDecimal> allValues = context.getAll();
        allValues.forEach((field, value) -> {
            System.out.printf("  %-25s : %10s%n", field, formatValue(value));
        });

        System.out.println(SEPARATOR + "\n");
    }

    /**
     * Logs calculation step details after context.calculateSteps()
     * Based on the cleaned-up ReserveCalcContext structure
     */
    public static void logStepCalculation(int stepIndex, Map<CalculationFlow, ReserveCalcStep> currentSteps,
                                          ReserveCalcContext context) {

        // Get the main step name from OMS flow
        ReserveField fieldName = currentSteps.get(CalculationFlow.OMS).getFieldName();

        System.out.println("\n" + THIN_SEPARATOR);
        System.out.printf(GREEN + "STEP %d: %s" + RESET + "\n", stepIndex + 1, fieldName);
        System.out.println(THIN_SEPARATOR);

        // Log each flow's calculation details
        for (CalculationFlow flow : CalculationFlow.values()) {
            if (currentSteps.containsKey(flow)) {
                ReserveCalcStep step = currentSteps.get(flow);

                System.out.printf("%s%-5s%s: ", CYAN, flow, RESET);

                // Show dependencies and their values
                if (!step.getDependencyFields().isEmpty()) {
                    String deps = step.getDependencyFields().stream()
                            .map(dep -> String.format("%s=%s", dep.name(), formatValue(context.get(dep))))
                            .collect(Collectors.joining(", "));
                    System.out.printf("Using [%s] ", deps);
                }

                // Get the step snapshot if available
                Map<Integer, Map<CalculationFlow, ReserveCalcStep>> snapshots = context.getStepSnapshots();
                if (snapshots.containsKey(stepIndex)) {
                    Map<CalculationFlow, ReserveCalcStep> stepSnapshot = snapshots.get(stepIndex);
                    if (stepSnapshot.containsKey(flow)) {
                        ReserveCalcStep snapshotStep = stepSnapshot.get(flow);
                        System.out.printf("=> %s%s%s (prev: %s)",
                                YELLOW,
                                formatValue(snapshotStep.getCurrentValue()),
                                RESET,
                                formatValue(snapshotStep.getPreviousValue()));
                    }
                }
                System.out.println();
            }
        }

        // Log the final value stored in context
        BigDecimal finalValue = context.get(fieldName);
        System.out.printf("\n%sFINAL VALUE%s: %s = %s%s%s\n",
                BLUE, RESET, fieldName, YELLOW, formatValue(finalValue), RESET);

        // Log running inventory calculation
        logRunningInventory(context);

        // Log dynamic values if any
        if (!context.getDynamicValues().isEmpty()) {
            System.out.println("\n" + MAGENTA + "Dynamic Values Updated:" + RESET);
            context.getDynamicValues().forEach((field, value) -> {
                System.out.printf("  %-25s : %10s\n", field, formatValue(value));
            });
        }
    }

    /**
     * Logs running inventory similar to Excel
     */
    private static void logRunningInventory(ReserveCalcContext context) {
        System.out.println("\n" + CYAN + "Running Inventory Calculation:" + RESET);

        BigDecimal initAfs = context.get(ReserveField.INITAFS);
        BigDecimal running = initAfs;

        System.out.printf("  Starting with INITAFS    : %s\n", formatValue(initAfs));

        // Subtract committed values
        BigDecimal snb = getActualValue(context, ReserveField.SNB, ReserveField.SNBX);
        BigDecimal dtco = getActualValue(context, ReserveField.DTCO, ReserveField.DTCOX);
        BigDecimal rohp = getActualValue(context, ReserveField.ROHP, ReserveField.ROHPX);

        if (snb.compareTo(BigDecimal.ZERO) > 0) {
            running = running.subtract(snb);
            System.out.printf("  After SNB (-%s)         : %s\n", formatValue(snb), formatValue(running));
        }
        if (dtco.compareTo(BigDecimal.ZERO) > 0) {
            running = running.subtract(dtco);
            System.out.printf("  After DTCO (-%s)        : %s\n", formatValue(dtco), formatValue(running));
        }
        if (rohp.compareTo(BigDecimal.ZERO) > 0) {
            running = running.subtract(rohp);
            System.out.printf("  After ROHP (-%s)        : %s\n", formatValue(rohp), formatValue(running));
        }

        // Should equal UNCOMAFS
        BigDecimal uncomafs = context.get(ReserveField.UNCOMAFS);
        if (!running.equals(uncomafs)) {
            System.out.printf("  %sWARNING: Calculated (%s) != UNCOMAFS (%s)%s\n",
                    RED, formatValue(running), formatValue(uncomafs), RESET);
        }

        // Continue with reserves
        running = uncomafs;

        // Subtract all reserve types
        ReserveField[][] reserves = {
                {ReserveField.DOTHRY, ReserveField.DOTHRYX}, {ReserveField.DOTHRN, ReserveField.DOTHRNX},
                {ReserveField.RETHRY, ReserveField.RETHRYX}, {ReserveField.RETHRN, ReserveField.RETHRNX},
                {ReserveField.HLDHR, ReserveField.HLDHRX}, {ReserveField.DOTRSV, ReserveField.DOTRSVX},
                {ReserveField.RETRSV, ReserveField.RETRSVX}, {ReserveField.AOUTBV, ReserveField.AOUTBVX},
                {ReserveField.ANEED, ReserveField.NEEDX}
        };

        for (ReserveField[] pair : reserves) {
            BigDecimal value = getActualValue(context, pair[0], pair[1]);
            if (value.compareTo(BigDecimal.ZERO) > 0) {
                running = running.subtract(value);
                System.out.printf("  After %s (-%s)%s: %s\n",
                        pair[0], formatValue(value), " ".repeat(Math.max(0, 10 - pair[0].name().length())),
                        formatValue(running));
            }
        }

        System.out.printf("\n  %sFinal Running Inventory  : %s%s%s\n",
                running.compareTo(BigDecimal.ZERO) < 0 ? RED : GREEN,
                formatValue(running),
                RESET,
                running.compareTo(BigDecimal.ZERO) < 0 ? " (NEGATIVE!)" : "");
    }

    /**
     * Get actual value (constraint if > 0, otherwise base)
     */
    private static BigDecimal getActualValue(ReserveCalcContext context, ReserveField base, ReserveField constraint) {
        BigDecimal constraintValue = context.get(constraint);
        BigDecimal baseValue = context.get(base);

        if (constraintValue == null) constraintValue = BigDecimal.ZERO;
        if (baseValue == null) baseValue = BigDecimal.ZERO;

        return constraintValue.compareTo(BigDecimal.ZERO) > 0 ? constraintValue : baseValue;
    }

    /**
     * Log running total history for specific fields
     */
    public static void logRunningTotalHistory(ReserveCalcContext context, ReserveField... fields) {
        Map<ReserveField, List<BigDecimal>> history = context.getRunningTotalHistory();

        System.out.println("\n" + BLUE + "Running Total History:" + RESET);
        for (ReserveField field : fields) {
            if (history.containsKey(field)) {
                List<BigDecimal> values = history.get(field);
                System.out.printf("  %s: %s\n", field,
                        values.stream()
                                .map(ReserveCalculationLogger::formatValue)
                                .collect(Collectors.joining(" -> ")));
            }
        }
    }

    /**
     * Log final summary with key calculations
     */
    public static void logFinalSummary(ReserveCalcContext context) {
        System.out.println("\n" + SEPARATOR);
        System.out.println(GREEN + "FINAL CALCULATION SUMMARY" + RESET);
        System.out.println(SEPARATOR);

        // Key inputs from InitialValueWrapper
        System.out.println("\nKey Inputs:");
        System.out.printf("  ONHAND : %s\n", formatValue(context.get(ReserveField.ONHAND)));
        System.out.printf("  ROHM   : %s\n", formatValue(context.get(ReserveField.ROHM)));
        System.out.printf("  LOST   : %s\n", formatValue(context.get(ReserveField.LOST)));
        System.out.printf("  OOBADJ : %s\n", formatValue(context.get(ReserveField.OOBADJ)));

        // Key calculations
        System.out.println("\nKey Calculations:");
        System.out.printf("  INITAFS   : %s\n", formatValue(context.get(ReserveField.INITAFS)));
        System.out.printf("  UNCOMAFS  : %s\n", formatValue(context.get(ReserveField.UNCOMAFS)));
        System.out.printf("  @DOTATS   : %s\n", formatValue(context.get(ReserveField.DOTATS)));
        System.out.printf("  @RETAILATS: %s\n", formatValue(context.get(ReserveField.RETAILATS)));
        System.out.printf("  @UNCOMMIT : %s\n", formatValue(context.get(ReserveField.UNCOMMIT)));
        System.out.printf("  @COMMITTED: %s\n", formatValue(context.get(ReserveField.COMMITTED)));

        // Final outputs
        System.out.println("\nFinal Outputs:");
        System.out.printf("  @OMSFINAL : %s\n", formatValue(context.get(ReserveField.OMSFINAL)));
        System.out.printf("  @RETFINAL : %s\n", formatValue(context.get(ReserveField.RETFINAL)));

        System.out.println(SEPARATOR);
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
     * Debug specific step calculation
     */
    public static void debugStepCalculation(ReserveField stepName, Map<ReserveField, BigDecimal> inputs,
                                            BigDecimal result, String formula) {
        System.out.println("\n" + YELLOW + "DEBUG: " + stepName + RESET);
        System.out.println("  Formula: " + formula);
        System.out.println("  Inputs:");
        inputs.forEach((field, value) -> {
            System.out.printf("    %s = %s\n", field, formatValue(value));
        });
        System.out.printf("  Result: %s\n", formatValue(result));
    }
}

