// ReserveCalcStep.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.sephora.ism.reserve.ReserveField.*;

public abstract class ReserveCalcStep {

    protected final ReserveField fieldName;
    protected final List<ReserveField> dependencyFields;
    protected CalculationFlow flow;

    protected BigDecimal originalValue;
    protected BigDecimal previousValue;
    protected BigDecimal currentValue;

    protected final Function<ReserveCalcContext, Boolean> preCondition;
    protected final BiFunction<ReserveCalcContext, BigDecimal, Boolean> postCondition;
    protected final Function<ReserveCalcContext, ReserveCalcContext> preProcessing;
    protected final BiFunction<ReserveCalcContext, BigDecimal, BigDecimal> postProcessing;

    public ReserveCalcStep(
            ReserveField fieldName,
            List<ReserveField> dependencyFields,
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
        for (ReserveField dep : dependencyFields) {
            sum = sum.add(context.get(dep));
        }
        return sum;
    }

    protected void updateTracking(BigDecimal newValue) {
        if (originalValue.equals(BigDecimal.ZERO)) {
            originalValue = newValue;
        }
        previousValue = currentValue;
        currentValue = newValue;
    }

//    public ReserveCalcStep copy() {
//        ReserveCalcStep copy = new ReserveCalcStep(fieldName, dependencyFields, preCondition, postCondition, preProcessing, postProcessing);
//        copy.originalValue = this.originalValue;
//        copy.previousValue = this.previousValue;
//        copy.currentValue = this.currentValue;
//        return copy;
//    }

    public ReserveField getFieldName() {
        return fieldName;
    }

    public List<ReserveField> getDependencyFields() {
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

    public void setFlow(CalculationFlow flow) {
        this.flow = flow;
    }

    public CalculationFlow getFlow() {
        return this.flow;
    }

    public abstract ReserveCalcStep copy();
}
