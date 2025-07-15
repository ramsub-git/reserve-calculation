package com.sephora.ism.reserve;

import java.util.List;
import java.math.BigDecimal;

public class ConstantStep extends ReserveCalcStep {
    private final BigDecimal constantValue;

    public ConstantStep(String fieldName, BigDecimal constantValue) {
        super(fieldName, List.of());
        this.constantValue = constantValue;
    }

    @Override
    public void calculateValue(ReserveCalcContext context, ReserveCalcModifierSet modifierSet) {
        context.put(getFieldName(), constantValue);
    }
}