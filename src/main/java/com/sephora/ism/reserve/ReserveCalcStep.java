package com.sephora.ism.reserve;

import java.util.List;

public class ReserveCalcStep {
    private final String fieldName;
    private final List<String> dependencyFields;

    public ReserveCalcStep(String fieldName, List<String> dependencyFields) {
        this.fieldName = fieldName;
        this.dependencyFields = dependencyFields;
    }

    public String getFieldName() {
        return fieldName;
    }

    public List<String> getDependencyFields() {
        return dependencyFields;
    }

    public void calculateValue(ReserveCalcContext context, ReserveCalcModifierSet modifierSet) {
        // No default implementation; subclasses must implement this method.
    }
}