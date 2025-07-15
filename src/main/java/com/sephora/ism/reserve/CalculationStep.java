package com.sephora.ism.reserve;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.BiFunction;
import java.math.BigDecimal;

public class CalculationStep extends ReserveCalcStep {
    private final BiFunction<Map<String, BigDecimal>, ReserveCalcModifierSet, BigDecimal> logic;
    private final BiFunction<Map<String, BigDecimal>, ReserveCalcModifierSet, BigDecimal> modifierLogic;

    public CalculationStep(String fieldName,
                           List<String> dependencyFields,
                           BiFunction<Map<String, BigDecimal>, ReserveCalcModifierSet, BigDecimal> logic,
                           BiFunction<Map<String, BigDecimal>, ReserveCalcModifierSet, BigDecimal> modifierLogic) {
        super(fieldName, dependencyFields);
        this.logic = logic;
        this.modifierLogic = modifierLogic;
    }

    @Override
    public void calculateValue(ReserveCalcContext context, ReserveCalcModifierSet modifierSet) {
        Map<String, BigDecimal> inputs = getDependencyFields().stream()
            .collect(Collectors.toMap(dep -> dep, dep -> context.get(dep)));
        BigDecimal result;
        if (modifierLogic != null) {
            result = modifierLogic.apply(inputs, modifierSet);
        } else {
            result = logic.apply(inputs, modifierSet);
        }
        context.put(getFieldName(), result);
    }
}