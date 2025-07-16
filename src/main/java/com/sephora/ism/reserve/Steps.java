// Steps.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class SkulocFieldStep extends ReserveCalcStep {
    public SkulocFieldStep(String fieldName) {
        super(fieldName, List.of());
    }

    @Override
    public void calculateValue(ReserveCalcContext context) {
        // No action needed - field populated by InitialValueWrapper
    }
}

class CalculationStep extends ReserveCalcStep {
    private static final Logger LOGGER = Logger.getLogger(CalculationStep.class.getName());
    private final Function<Map<String, BigDecimal>, BigDecimal> calculationLogic;

    public CalculationStep(
            String fieldName,
            List<String> dependencyFields,
            Function<Map<String, BigDecimal>, BigDecimal> calculationLogic
    ) {
        super(fieldName, dependencyFields);
        this.calculationLogic = calculationLogic;
    }

    @Override
    public void calculateValue(ReserveCalcContext context) {
        Map<String, BigDecimal> inputs = getDependencyFields().stream()
                .collect(Collectors.toMap(
                        dep -> dep,
                        dep -> context.get(dep)
                ));

        BigDecimal result = calculationLogic.apply(inputs);
        context.put(getFieldName(), result);
        LOGGER.info(String.format("Calculated %s = %s with inputs: %s",
                getFieldName(), result, inputs));
    }
}

// Constraint step to ensure we don't subtract more than available
class ConstraintStep extends ReserveCalcStep {
    private static final Logger LOGGER = Logger.getLogger(ConstraintStep.class.getName());

    public ConstraintStep(String fieldName, String baseField, String availableField) {
        super(fieldName, List.of(baseField, availableField));
    }

    @Override
    public void calculateValue(ReserveCalcContext context) {
        String baseField = getDependencyFields().get(0);
        String availableField = getDependencyFields().get(1);

        BigDecimal base = context.get(baseField);
        BigDecimal available = context.get(availableField);

        // Constraint: can't consume more than available
        BigDecimal result = base.min(available);
        context.put(getFieldName(), result);

        LOGGER.info(String.format("Constraint %s: min(%s=%s, %s=%s) = %s",
                getFieldName(), baseField, base, availableField, available, result));
    }
}

class ContextConditionStep extends ReserveCalcStep {
    private static final Logger LOGGER = Logger.getLogger(ContextConditionStep.class.getName());
    private final Function<ReserveCalcContext, BigDecimal> conditionLogic;

    public ContextConditionStep(
            String fieldName,
            List<String> dependencyFields,
            Function<ReserveCalcContext, BigDecimal> conditionLogic
    ) {
        super(fieldName, dependencyFields);
        this.conditionLogic = conditionLogic;
    }

    @Override
    public void calculateValue(ReserveCalcContext context) {
        BigDecimal selectedValue = conditionLogic.apply(context);
        context.put(getFieldName(), selectedValue);
        LOGGER.info(String.format("Context condition %s selected value: %s",
                getFieldName(), selectedValue));
    }
}