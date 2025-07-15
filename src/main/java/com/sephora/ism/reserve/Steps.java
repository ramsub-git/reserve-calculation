package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.function.BiFunction;

class SkulocFieldStep extends ReserveCalcStep {
    private final BigDecimal value;

    public SkulocFieldStep(String fieldName, BigDecimal value) {
        super(fieldName, List.of());
        this.value = value;
    }

    @Override
    public void calculateValue(ReserveCalcContext context, ReserveCalcModifierSet modifierSet) {
        context.put(fieldName, value);
    }
}

class CalculationStep extends ReserveCalcStep {
    private static final Logger LOGGER = Logger.getLogger(CalculationStep.class.getName());
    private final BiFunction<Map<String, BigDecimal>, ReserveCalcModifierSet, BigDecimal> calculationLogic;

    public CalculationStep(
        String fieldName, 
        List<String> dependencyFields,
        BiFunction<Map<String, BigDecimal>, ReserveCalcModifierSet, BigDecimal> calculationLogic
    ) {
        super(fieldName, dependencyFields);
        this.calculationLogic = calculationLogic;
    }

    @Override
    public void calculateValue(ReserveCalcContext context, ReserveCalcModifierSet modifierSet) {
        Map<String, BigDecimal> inputs = dependencyFields.stream()
            .collect(java.util.stream.Collectors.toMap(
                dep -> dep, 
                context::get
            ));

        try {
            BigDecimal calculatedValue = calculationLogic.apply(inputs, modifierSet);
            context.put(fieldName, calculatedValue);

            calculationFlows.forEach((flow, step) -> {
                if (step != null) {
                    step.calculateValue(context, modifierSet);
                }
            });

        } catch (Exception e) {
            LOGGER.severe("Calculation error in step " + fieldName + ": " + e.getMessage());
            throw new RuntimeException("Calculation failed", e);
        }
    }
}

class OverrideCalculationStep extends CalculationStep {
    public OverrideCalculationStep(
        String fieldName, 
        List<String> dependencyFields,
        BiFunction<Map<String, BigDecimal>, ReserveCalcModifierSet, BigDecimal> calculationLogic
    ) {
        super(fieldName, dependencyFields, calculationLogic);
    }
}