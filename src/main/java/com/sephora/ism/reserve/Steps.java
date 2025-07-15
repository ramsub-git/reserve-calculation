package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class SkulocFieldStep extends ReserveCalcStep {
    public SkulocFieldStep(String fieldName) {
        super(fieldName, List.of());
    }

    @Override
    public void calculateValue(ReserveCalcContext context) {
        // No action needed in engine setup
        // The actual population will happen when InitialValueWrapper is used
    }
}

class CalculationStep extends ReserveCalcStep {
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
                        context::get
                ));

        BigDecimal result = calculationLogic.apply(inputs);
        context.put(getFieldName(), result);
    }
}

class ContextConditionStep extends ReserveCalcStep {
    private final Function<Map<String, BigDecimal>, BigDecimal> conditionLogic;

    public ContextConditionStep(
            String fieldName,
            List<String> dependencyFields,
            Function<Map<String, BigDecimal>, BigDecimal> conditionLogic
    ) {
        super(fieldName, dependencyFields);
        this.conditionLogic = conditionLogic;
    }

    @Override
    public void calculateValue(ReserveCalcContext context) {
        Map<String, BigDecimal> inputs = getDependencyFields().stream()
                .collect(Collectors.toMap(
                        dep -> dep,
                        context::get
                ));

        BigDecimal selectedValue = conditionLogic.apply(inputs);
        context.put("selectedValue", selectedValue);
    }
}