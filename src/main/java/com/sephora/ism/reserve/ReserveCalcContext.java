// ReserveCalcContext.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;

public class ReserveCalcContext {
    private static final Logger LOGGER = Logger.getLogger(ReserveCalcContext.class.getName());

    final Map<String, BigDecimal> fieldValues = new LinkedHashMap<>();
    private final Map<String, List<BigDecimal>> fieldHistory = new LinkedHashMap<>();
    private final Map<CalculationFlow, Map<String, BigDecimal>> flowContexts = new LinkedHashMap<>();

    public ReserveCalcContext() {
        // Initialize flow contexts
        for (CalculationFlow flow : CalculationFlow.values()) {
            flowContexts.put(flow, new LinkedHashMap<>());
        }
    }

    public void put(String field, BigDecimal value) {
        // Track history for traceability
        fieldHistory.computeIfAbsent(field, k -> new ArrayList<>()).add(value);
        fieldValues.put(field, value);
        LOGGER.fine(String.format("Put %s = %s", field, value));
    }

    public BigDecimal get(String field) {
        return fieldValues.getOrDefault(field, BigDecimal.ZERO);
    }

    public void putFlowValue(CalculationFlow flow, String field, BigDecimal value) {
        flowContexts.get(flow).put(field, value);
        LOGGER.fine(String.format("Put flow %s: %s = %s", flow, field, value));
    }

    public BigDecimal getFlowValue(CalculationFlow flow, String field) {
        return flowContexts.get(flow).getOrDefault(field, BigDecimal.ZERO);
    }

    public Map<String, BigDecimal> getFlowContext(CalculationFlow flow) {
        return new LinkedHashMap<>(flowContexts.get(flow));
    }

    public Map<String, BigDecimal> getAll() {
        return new LinkedHashMap<>(fieldValues);
    }

    public List<BigDecimal> getHistory(String field) {
        return new ArrayList<>(fieldHistory.getOrDefault(field, List.of()));
    }

    // Copy current state to a flow context
    public void copyToFlow(CalculationFlow flow) {
        flowContexts.get(flow).putAll(fieldValues);
    }

    // Ensure non-negative result with clamping
    public void putNonNegative(String field, BigDecimal value) {
        put(field, value.max(BigDecimal.ZERO));
    }
}