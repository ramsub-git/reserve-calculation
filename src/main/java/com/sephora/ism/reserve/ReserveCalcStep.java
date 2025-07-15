package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

public class ReserveCalcStep {
    private static final Logger LOGGER = Logger.getLogger(ReserveCalcStep.class.getName());

    protected final String fieldName;
    protected final List<String> dependencyFields;
    protected Map<CalculationFlow, ReserveCalcStep> calculationFlows = new LinkedHashMap<>();

    public ReserveCalcStep(String fieldName, List<String> dependencyFields) {
        this.fieldName = fieldName;
        this.dependencyFields = dependencyFields;
    }

    public void setCalculationFlows(Map<CalculationFlow, ReserveCalcStep> flows) {
        this.calculationFlows.putAll(flows);
    }

    public void calculateValue(ReserveCalcContext context, ReserveCalcModifierSet modifierSet) {
        LOGGER.info("Calculating value for field: " + fieldName);
    }

    public String getFieldName() {
        return fieldName;
    }
}