// ReserveCalcStep.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ReserveCalcStep {

    private final String fieldName;
    private final List<String> dependencyFields;

    private BigDecimal originalValue;
    private BigDecimal previousValue;
    private BigDecimal currentValue;

    private final Function<ReserveCalcContext, Boolean> preCondition;
    private final BiFunction<ReserveCalcContext, BigDecimal, Boolean> postCondition;
    private final Function<ReserveCalcContext, ReserveCalcContext> preProcessing;
    private final BiFunction<ReserveCalcContext, BigDecimal, BigDecimal> postProcessing;

    public ReserveCalcStep(
            String fieldName,
            List<String> dependencyFields,
            Function<ReserveCalcContext, Boolean> preCondition,
            BiFunction<ReserveCalcContext, BigDecimal, Boolean> postCondition,
            Function<ReserveCalcContext, ReserveCalcContext> preProcessing,
            BiFunction<ReserveCalcContext, BigDecimal, BigDecimal> postProcessing
    ) {
        this.fieldName = fieldName;
        this.dependencyFields = dependencyFields;
        this.preCondition = preCondition != null ? preCondition : ctx -> true;
        this.postCondition = postCondition != null ? postCondition : (ctx, result) -> true;
        this.preProcessing = preProcessing != null ? preProcessing : ctx -> ctx;
        this.postProcessing = postProcessing != null ? postProcessing : (ctx, result) -> result;

        this.originalValue = BigDecimal.ZERO;
        this.previousValue = BigDecimal.ZERO;
        this.currentValue = BigDecimal.ZERO;
    }

    public BigDecimal calculateValue(ReserveCalcContext context) {
        if (!preCondition.apply(context)) {
            return currentValue;
        }

        ReserveCalcContext processedContext = preProcessing.apply(context);

        BigDecimal result = compute(processedContext);
        BigDecimal processedResult = postProcessing.apply(processedContext, result);

        if (postCondition.apply(processedContext, processedResult)) {
            updateTracking(processedResult);
            return processedResult;
        } else {
            return currentValue;
        }
    }

    protected BigDecimal compute(ReserveCalcContext context) {
        BigDecimal sum = BigDecimal.ZERO;
        for (String dep : dependencyFields) {
            sum = sum.add(context.get(dep));
        }
        return sum;
    }

    private void updateTracking(BigDecimal newValue) {
        if (originalValue.equals(BigDecimal.ZERO)) {
            originalValue = newValue;
        }
        previousValue = currentValue;
        currentValue = newValue;
    }

    public ReserveCalcStep copy() {
        ReserveCalcStep copy = new ReserveCalcStep(fieldName, dependencyFields, preCondition, postCondition, preProcessing, postProcessing);
        copy.originalValue = this.originalValue;
        copy.previousValue = this.previousValue;
        copy.currentValue = this.currentValue;
        return copy;
    }

    public String getFieldName() {
        return fieldName;
    }

    public List<String> getDependencyFields() {
        return dependencyFields;
    }

    public BigDecimal getOriginalValue() {
        return originalValue;
    }

    public BigDecimal getPreviousValue() {
        return previousValue;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }
}
