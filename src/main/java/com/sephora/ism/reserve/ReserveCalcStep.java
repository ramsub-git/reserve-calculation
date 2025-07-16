// ReserveCalcStep.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class ReserveCalcStep {
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

    public abstract void calculateValue(ReserveCalcContext context);
}