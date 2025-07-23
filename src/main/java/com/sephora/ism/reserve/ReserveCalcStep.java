// ReserveCalcStep.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ReserveCalcStep<T> {

    protected static final Logger logger = LoggerFactory.getLogger(ReserveCalcStep.class);

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

    protected ReserveCalcStep(
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

        // Initialize tracking values to prevent NPE
        this.originalValue = BigDecimal.ZERO;
        this.previousValue = BigDecimal.ZERO;
        this.currentValue = BigDecimal.ZERO;
    }

    public BigDecimal calculateValue(ReserveCalcContext context) {
        if (!preCondition.apply(context)) {
//            logger.info("  [" + fieldName + "] PreCondition failed, returning current: " + currentValue);
            return currentValue;
        }

        ReserveCalcContext processedContext = preProcessing.apply(context);

//        logger.info("  [" + fieldName + "] Computing value...");
        BigDecimal result = compute(processedContext);
//        logger.info("  [" + fieldName + "] Computed raw result: " + result);

        BigDecimal processedResult = postProcessing.apply(processedContext, result);
//        logger.info("  [" + fieldName + "] After postProcessing: " + processedResult);

        if (postCondition.apply(processedContext, processedResult)) {
//            logger.info("  [" + fieldName + "] PostCondition passed, returning: " + processedResult);
            return processedResult;
        } else {
//            logger.info("  [" + fieldName + "] PostCondition failed, returning current: " + currentValue);
            return currentValue;
        }
    }

    protected BigDecimal compute(ReserveCalcContext context) {
        // For SkulocFieldStep, this will be overridden
        // For other steps, sum dependencies
        BigDecimal sum = BigDecimal.ZERO;
        for (ReserveField dep : dependencyFields) {
            BigDecimal depValue = context.getCurrentValue(this.flow, dep);
            sum = sum.add(depValue);
        }
        return sum;
    }

    public void updateTracking(BigDecimal newValue) {
        if (originalValue.equals(BigDecimal.ZERO) && !newValue.equals(BigDecimal.ZERO)) {
            originalValue = newValue;
        }
        previousValue = currentValue;
        currentValue = newValue;
    }

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