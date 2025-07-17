// Steps.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Steps {

    // 1. SkulocFieldStep: Direct passthrough or no-op
    public static class SkulocFieldStep extends ReserveCalcStep {
        public SkulocFieldStep(String fieldName) {
            super(fieldName, List.of(), null, null, null, null);
        }

        @Override
        public BigDecimal calculateValue(ReserveCalcContext context) {
            if (context.getInitialValueWrapper() == null) {
                throw new IllegalStateException("InitialValueWrapper not set in context.");
            }
            return context.getInitialValueWrapper().get(fieldName);
        }

        @Override
        public ReserveCalcStep copy() {
            SkulocFieldStep copy = new SkulocFieldStep(fieldName);
            copy.flow = this.flow;
            return copy;
        }


    }

    // 2. CalculationStep: Formula-based calculation
    public static class CalculationStep extends ReserveCalcStep {
        private final Function<Map<String, BigDecimal>, BigDecimal> formula;

        public CalculationStep(String fieldName, List<String> dependencyFields,
                               Function<Map<String, BigDecimal>, BigDecimal> formula,
                               Function<ReserveCalcContext, Boolean> preCondition,
                               BiFunction<ReserveCalcContext, BigDecimal, Boolean> postCondition,
                               Function<ReserveCalcContext, ReserveCalcContext> preProcessing,
                               BiFunction<ReserveCalcContext, BigDecimal, BigDecimal> postProcessing) {
            super(fieldName, dependencyFields, preCondition, postCondition, preProcessing, postProcessing);
            this.formula = formula;
        }

        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            Map<String, BigDecimal> inputs = new LinkedHashMap<>();
            for (String dependency : dependencyFields) {
                inputs.put(dependency, context.get(dependency));
            }
            BigDecimal result = formula.apply(inputs);

    // Debug log for complex calculations
    if (getFieldName().contains("X") || getFieldName().startsWith("@")) {
        ReserveCalculationLogger.debugStepCalculation(
            getFieldName(), 
            inputs, 
            result, 
            "Custom formula"
        );

    }
        return result;
        }

        @Override
        public ReserveCalcStep copy() {
            CalculationStep copy = new CalculationStep(fieldName, dependencyFields, formula, preCondition, postCondition, preProcessing, postProcessing);
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            copy.flow = this.flow;
            return copy;
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

        @Override
        public ReserveCalcStep copy() {
            ConstraintStep copy = new ConstraintStep(fieldName, baseField, constraintField);
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            copy.flow = this.flow;
            return copy;
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


        @Override
        public ReserveCalcStep copy() {
            ContextConditionStep copy = new ContextConditionStep(fieldName, dependencyFields, conditionLogic);
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            copy.flow = this.flow;
            return copy;
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

        @Override
        public ReserveCalcStep copy() {
            ConstantStep copy = new ConstantStep(fieldName, constantValue);
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            copy.flow = this.flow;
            return copy;
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


        @Override
        public ReserveCalcStep copy() {
            CopyStep copy = new CopyStep(fieldName, sourceField);
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            copy.flow = this.flow;
            return copy;
        }

    }
}
