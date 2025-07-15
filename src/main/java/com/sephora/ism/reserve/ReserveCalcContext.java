package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;

public class ReserveCalcContext {
    private static final Logger LOGGER = Logger.getLogger(ReserveCalcContext.class.getName());

    private final Map<String, BigDecimal> fieldValues = new LinkedHashMap<>();
    private final Map<String, Map<CalculationFlow, BigDecimal>> modifierValues = new LinkedHashMap<>();
    private final Map<String, List<BigDecimal>> runningHistory = new LinkedHashMap<>();
    private final Map<String, List<String>> calculationSteps = new LinkedHashMap<>();

    public void put(String field, BigDecimal value) {
        fieldValues.put(field, value);
        
        // Update running history
        runningHistory.computeIfAbsent(field, k -> new ArrayList<>()).add(value);
        
        // Track calculation steps
        calculationSteps.computeIfAbsent(field, k -> new ArrayList<>()).add("Main Calculation");
        
        // Automatically populate modifier values if not exists
        modifierValues.computeIfAbsent(field, k -> new LinkedHashMap<>())
                      .putIfAbsent(CalculationFlow.OMS, value);
    }

    public void putModifierValue(String field, CalculationFlow flow, BigDecimal value, String calculationStep) {
        modifierValues.computeIfAbsent(field, k -> new LinkedHashMap<>())
                      .put(flow, value);
        
        // Track modifier calculation steps
        calculationSteps.computeIfAbsent(field, k -> new ArrayList<>())
                        .add(flow + " - " + calculationStep);
    }

    public BigDecimal get(String field) {
        return fieldValues.getOrDefault(field, BigDecimal.ZERO);
    }

    public BigDecimal getModifierValue(String field, CalculationFlow flow) {
        return modifierValues.getOrDefault(field, Collections.emptyMap())
                             .getOrDefault(flow, get(field));
    }

    public List<BigDecimal> getRunningHistory(String field) {
        return runningHistory.getOrDefault(field, Collections.emptyList());
    }

    public List<String> getCalculationSteps(String field) {
        return calculationSteps.getOrDefault(field, Collections.emptyList());
    }

    public Map<String, BigDecimal> getAll() {
        return new LinkedHashMap<>(fieldValues);
    }

    public Map<String, Map<CalculationFlow, BigDecimal>> getAllModifierValues() {
        return new LinkedHashMap<>(modifierValues);
    }

    public void logContextState() {
        LOGGER.info("Context State:");
        fieldValues.forEach((field, value) -> 
            LOGGER.info(field + ": " + value + " (Steps: " + 
                String.join(" -> ", getCalculationSteps(field)) + ")")
        );
    }
}