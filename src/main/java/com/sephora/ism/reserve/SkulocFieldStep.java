package com.sephora.ism.reserve;

import java.util.List;

public class SkulocFieldStep extends ReserveCalcStep {
    public SkulocFieldStep(String fieldName) {
        super(fieldName, List.of());
    }

    @Override
    public void calculateValue(ReserveCalcContext context, ReserveCalcModifierSet modifierSet) {
        // Value already present in context; no action needed.
    }
}