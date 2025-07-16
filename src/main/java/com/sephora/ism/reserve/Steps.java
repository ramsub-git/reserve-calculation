// Steps.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Steps {

    // 1. SkulocFieldStep: Direct passthrough or no-op
    public static class SkulocFieldStep extends ReserveCalcStep {
        public SkulocFieldStep(String fieldName) {
            super(fieldName, List.of(), null, null, null, null);
        }

        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            return context.get(getFieldName());
        }
    }

    // 2. CalculationStep: Formula-based calculation
    public static class CalculationStep extends ReserveCalcStep {
        private final Function<List<BigDecimal>, BigDecimal> formula;

        public CalculationStep(String fieldName, List<String> dependencyFields,
                               Function<List<BigDecimal>, BigDecimal> formula,
                               Function<ReserveCalcContext, Boolean> preCondition,
                               BiFunction<ReserveCalcContext, BigDecimal, Boolean> postCondition,
                               Function<ReserveCalcContext, ReserveCalcContext> preProcessing,
                               BiFunction<ReserveCalcContext, BigDecimal, BigDecimal> postProcessing) {
            super(fieldName, dependencyFields, preCondition, postCondition, preProcessing, postProcessing);
            this.formula = formula;
        }

        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            List<BigDecimal> values = getDependencyFields().stream()
                    .map(context::get)
                    .toList();
            return formula.apply(values);
        }
    }

    // 3. ConstraintStep: Min/Max clamping logic
    public static class ConstraintStep extends ReserveCalcStep {
        private final String baseField;
        private final String constraintField;

        public ConstraintStep(String fieldName, String baseField, String constraintField) {
            super(fieldName, List.of(baseField, constraintField), null, null, null, null);
            this.baseField = baseField;
            this.constraintField = constraintField;
        }

        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            BigDecimal base = context.get(baseField);
            BigDecimal constraint = context.get(constraintField);
            return base.min(constraint).max(BigDecimal.ZERO);
        }
    }

    // 4. ContextConditionStep: Select between context values based on logic
    public static class ContextConditionStep extends ReserveCalcStep {
        private final Function<ReserveCalcContext, BigDecimal> conditionLogic;

        public ContextConditionStep(String fieldName, List<String> dependencyFields,
                                    Function<ReserveCalcContext, BigDecimal> conditionLogic) {
            super(fieldName, dependencyFields, null, null, null, null);
            this.conditionLogic = conditionLogic;
        }

        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            return conditionLogic.apply(context);
        }
    }

    // 5. ConstantStep: Static value
    public static class ConstantStep extends ReserveCalcStep {
        private final BigDecimal constantValue;

        public ConstantStep(String fieldName, BigDecimal constantValue) {
            super(fieldName, List.of(), null, null, null, null);
            this.constantValue = constantValue;
        }

        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            return constantValue;
        }
    }

    // 6. CopyStep: Copy from one field to another
    public static class CopyStep extends ReserveCalcStep {
        private final String sourceField;

        public CopyStep(String fieldName, String sourceField) {
            super(fieldName, List.of(sourceField), null, null, null, null);
            this.sourceField = sourceField;
        }

        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            return context.get(sourceField);
        }
    }
}
