// Steps.java
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Steps {


    // 1. SkulocFieldStep: Direct passthrough from InitialValueWrapper
    public static class SkulocFieldStep extends ReserveCalcStep {
        protected static final Logger logger = LoggerFactory.getLogger(SkulocFieldStep.class);

        public SkulocFieldStep(ReserveField fieldName) {
            super(fieldName, List.of(), null, null, null, null);
        }

        @Override
        public BigDecimal calculateValue(ReserveCalcContext context) {
            if (context.getInitialValueWrapper() == null) {
                logger.info("  [" + fieldName + "] No InitialValueWrapper, returning current: " + getCurrentValue());
                return getCurrentValue();
            }
            BigDecimal value = context.getInitialValueWrapper().get(fieldName);
//            logger.info("  [" + fieldName + "] Got value from wrapper: " + value);
            return value;
        }


        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            // Override compute to get value from InitialValueWrapper
            if (context.getInitialValueWrapper() != null) {
                return context.getInitialValueWrapper().get(fieldName);
            }
            return BigDecimal.ZERO;
        }

        @Override
        public ReserveCalcStep copy() {
            SkulocFieldStep copy = new SkulocFieldStep(fieldName);
            copy.flow = this.flow;
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            return copy;
        }
    }

    // 2. CalculationStep: Formula-based calculation
    public static class CalculationStep extends ReserveCalcStep {
        private final Function<Map<ReserveField, BigDecimal>, BigDecimal> formula;

        public CalculationStep(ReserveField fieldName, List<ReserveField> dependencyFields,
                               Function<Map<ReserveField, BigDecimal>, BigDecimal> formula,
                               Function<ReserveCalcContext, Boolean> preCondition,
                               BiFunction<ReserveCalcContext, BigDecimal, Boolean> postCondition,
                               Function<ReserveCalcContext, ReserveCalcContext> preProcessing,
                               BiFunction<ReserveCalcContext, BigDecimal, BigDecimal> postProcessing) {
            super(fieldName, dependencyFields, preCondition, postCondition, preProcessing, postProcessing);
            this.formula = formula;
        }

        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
//            logger.info("    CalculationStep.compute() for " + getFieldName());
            Map<ReserveField, BigDecimal> inputs = new LinkedHashMap<>();

            for (ReserveField dependency : dependencyFields) {
                BigDecimal depValue = context.getCurrentValue(this.flow, dependency);
                inputs.put(dependency, depValue);
//                logger.info("      Dependency " + dependency + " = " + depValue + " (flow: " + this.flow + ")");
            }

//            logger.info("    Calling formula with inputs: " + inputs);
            BigDecimal result = formula.apply(inputs);
//            logger.info("    Formula returned: " + result);

            // Debug log for complex calculations
            if (getFieldName().name().contains("X") || getFieldName().name().startsWith("@")) {
                // ReserveCalculationLogger.debugStepCalculation(
                //         getFieldName(),
                //         inputs,
                //         result,
                //         "Custom formula"
                // );
            }
            return result;
        }

        @Override
        public ReserveCalcStep copy() {
            CalculationStep copy = new CalculationStep(fieldName, dependencyFields, formula,
                    preCondition, postCondition, preProcessing, postProcessing);
            copy.flow = this.flow;
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            return copy;
        }
    }


    
    // x. SkulocFieldStep: Direct passthrough from InitialValueWrapper
    public static class SkulocStringFieldStep extends ReserveCalcStep {
        protected static final Logger logger = LoggerFactory.getLogger(SkulocFieldStep.class);

        public SkulocStringFieldStep(ReserveField fieldName) {
            super(fieldName, List.of(), null, null, null, null);
        }

        @Override
        public BigDecimal calculateValue(ReserveCalcContext context) {
            if (context.getInitialValueWrapper() == null) {
                logger.info("  [" + fieldName + "] No InitialValueWrapper, returning current: " + getCurrentValue());
                return getCurrentValue();
            }
            BigDecimal value = context.getInitialValueWrapper().get(fieldName);
//            logger.info("  [" + fieldName + "] Got value from wrapper: " + value);
            return value;
        }


        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            // Override compute to get value from InitialValueWrapper
            if (context.getInitialValueWrapper() != null) {
                return context.getInitialValueWrapper().get(fieldName);
            }
            return BigDecimal.ZERO;
        }

        @Override
        public ReserveCalcStep copy() {
            SkulocStringFieldStep copy = new SkulocStringFieldStep(fieldName);
            copy.flow = this.flow;
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            return copy;
        }
    }

    
    // 3. RunningCalculationStep - Fixed version
    public static class RunningCalculationStep extends ReserveCalcStep {
        // private final ReserveField initialValueField;
        protected final List<ReserveField> triggerFields;
        protected final boolean selfDriven;
        protected final BiFunction<BigDecimal, BigDecimal, BigDecimal> formula;

        public RunningCalculationStep(ReserveField outputField,
        							  BigDecimal startingValue,
//                                    // ReserveField initialValueField,
                                      List<ReserveField> triggerFields,
                                      boolean selfDriven,
                                      BiFunction<BigDecimal, BigDecimal, BigDecimal> formula) {
            // Pass trigger fields as dependencies for getDependencyFields() to work
            super(outputField, triggerFields, null, null, null, null);
            this.originalValue = this.currentValue = startingValue;
            // this.initialValueField = initialValueField;
            this.triggerFields = new ArrayList<>(triggerFields);
            this.selfDriven = selfDriven;
            this.formula = formula;
        }

        public boolean shouldTrigger(ReserveField triggeredField, boolean afterInitStep) {
//            if (selfDriven) {
//                // For self-driven, trigger only once after initial value field is set
//                return afterInitStep && triggeredField.equals(initialValueField);
//            } else {
                // For dependency-driven, trigger when one of the trigger fields changes
                return triggerFields.contains(triggeredField);
//            }
        }

        public BigDecimal calculateValue(ReserveCalcContext context, ReserveField triggeredField) {
            // Get the current running value for this field in the current flow
            BigDecimal runningValue = context.getCurrentValue(flow, fieldName);

            // If no running value exists yet, initialize from the initial value field
//            if (runningValue == null || runningValue.equals(BigDecimal.ZERO)) {
//                if (initialValueField != null) {
//                    runningValue = context.getCurrentValue(flow, initialValueField);
//                } else {
//                    runningValue = BigDecimal.ZERO;
//                }
//            }

            // Get the value that triggered this calculation
            BigDecimal triggeredValue = context.getCurrentValue(flow, triggeredField);

            // Apply the formula (e.g., subtract from running total)
            BigDecimal result = formula.apply(runningValue, triggeredValue);

            // Update the tracking for this step
            updateTracking(result);

            // Log the calculation for debugging
            logger.info(String.format("RunningCalc[%s.%s]: %s - %s = %s (triggered by %s)",
                    flow, fieldName, runningValue, triggeredValue, result, triggeredField));

            return result;
        }

        @Override
        public BigDecimal calculateValue(ReserveCalcContext context) {
            // This method should not be called directly for RunningCalculationStep
            // Return current value as fallback
            return getCurrentValue();
        }

        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            // Return current value - actual calculation happens in calculateValue(context, triggeredField)
            return getCurrentValue();
        }

        @Override
        public ReserveCalcStep copy() {
            RunningCalculationStep copy = new RunningCalculationStep(
                    this.fieldName,
                    this.originalValue,
                    // this.initialValueField,
                    new ArrayList<>(this.triggerFields),
                    this.selfDriven,
                    this.formula
            );
            copy.flow = this.flow;
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            return copy;
        }
    }

    
 // RunningWithInitialStep - Full Implementation
    public static class RunningWithInitialStep extends RunningCalculationStep {
        private final ReserveField initialField;
        private boolean initialized = false;

        public RunningWithInitialStep(ReserveField outputField,
                                      ReserveField initialField,
                                      List<ReserveField> triggerFields,
                                      boolean selfDriven,
                                      BiFunction<BigDecimal, BigDecimal, BigDecimal> formula) {
            // Call parent constructor with BigDecimal.ZERO as starting value
            super(outputField, BigDecimal.ZERO, triggerFields, selfDriven, formula);
            this.initialField = initialField;
        }

        @Override
        public boolean shouldTrigger(ReserveField triggeredField, boolean afterInitStep) {
            // Trigger on the initial field OR any of the regular trigger fields
            if (triggeredField.equals(initialField)) {
                return true; // Always trigger for initial field
            }
            return super.shouldTrigger(triggeredField, afterInitStep);
        }

        @Override
        public BigDecimal calculateValue(ReserveCalcContext context, ReserveField triggeredField) {
            if (!initialized && triggeredField.equals(initialField)) {
                // First time initialization - copy the initial field value
                BigDecimal initialValue = context.getCurrentValue(flow, initialField);
                updateTracking(initialValue);
                initialized = true;
                
                logger.info("RunningWithInitial[{}.{}]: Initialized from {} = {} ", 
                        flow, fieldName, initialField, initialValue);
                return initialValue;
                
            } else if (initialized && !triggeredField.equals(initialField)) {
                // Subsequent updates - apply the formula (e.g., subtract allocations)
                BigDecimal runningValue = getCurrentValue();
                BigDecimal triggeredValue = context.getCurrentValue(flow, triggeredField);
                
                // Apply the formula from parent class
                BigDecimal result = formula.apply(runningValue, triggeredValue);
                updateTracking(result);
                
                logger.info("RunningWithInitial[{}.{}]: {} operation {} = {} (triggered by {})",
                        flow, fieldName, runningValue, triggeredValue, result, triggeredField);
                return result;
                
            } else {
                // Either: initial field triggered but already initialized, 
                // OR: some other field that shouldn't trigger this
                logger.debug("RunningWithInitial[{}.{}]: No action for trigger {} (initialized={})", 
                        flow, fieldName, triggeredField, initialized);
                return getCurrentValue();
            }
        }

        @Override
        public ReserveCalcStep copy() {
            RunningWithInitialStep copy = new RunningWithInitialStep(
                    this.fieldName,
                    this.initialField,
                    new ArrayList<>(this.triggerFields),
                    this.selfDriven,
                    this.formula
            );
            copy.flow = this.flow;
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            copy.initialized = this.initialized; // Copy the initialization state
            return copy;
        }

        // Helper method to check if this step has been initialized
        public boolean isInitialized() {
            return initialized;
        }

        // Helper method to get the initial field
        public ReserveField getInitialField() {
            return initialField;
        }
    }
    
    
    // 4. StatefulCalculationStep - For calculations that need previous state
    public static class StatefulCalculationStep extends ReserveCalcStep {
        private final Function<Map<ReserveField, BigDecimal>, BigDecimal> formula;

        public StatefulCalculationStep(ReserveField outputField,
                                       List<ReserveField> dependencyFields,
                                       Function<Map<ReserveField, BigDecimal>, BigDecimal> formula) {
            super(outputField, dependencyFields, null, null, null, null);
            this.formula = formula;
        }

        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            Map<ReserveField, BigDecimal> inputs = new HashMap<>();

            // Collect previous values from dependencies
            for (ReserveField field : dependencyFields) {
                inputs.put(field, context.getPreviousValue(flow, field));
            }

            return formula.apply(inputs);
        }

        @Override
        public ReserveCalcStep copy() {
            StatefulCalculationStep copy = new StatefulCalculationStep(
                    this.fieldName,
                    new ArrayList<>(this.dependencyFields),
                    this.formula
            );
            copy.flow = this.flow;
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            return copy;
        }
    }

    // 5. ConstantStep: Static value
    public static class ConstantStep extends ReserveCalcStep {
        private final BigDecimal constantValue;

        public ConstantStep(ReserveField fieldName, BigDecimal constantValue) {
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
            copy.flow = this.flow;
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            return copy;
        }
    }

    // 6. ConstraintStep: Min/Max clamping logic
    public static class ConstraintStep extends ReserveCalcStep {
        private final ReserveField baseField;
        private final ReserveField constraintField;

        public ConstraintStep(ReserveField fieldName, ReserveField baseField, ReserveField constraintField) {
            super(fieldName, List.of(baseField, constraintField), null, null, null, null);
            this.baseField = baseField;
            this.constraintField = constraintField;
        }

        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            BigDecimal base = context.getCurrentValue(this.flow, baseField);
            BigDecimal constraint = context.getCurrentValue(this.flow, constraintField);
            return base.min(constraint).max(BigDecimal.ZERO);
        }

        @Override
        public ReserveCalcStep copy() {
            ConstraintStep copy = new ConstraintStep(fieldName, baseField, constraintField);
            copy.flow = this.flow;
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            return copy;
        }
    }

    // 7. CopyStep: Copy from one field to another
    public static class CopyStep extends ReserveCalcStep {
        private final ReserveField sourceField;

        public CopyStep(ReserveField fieldName, ReserveField sourceField) {
            super(fieldName, List.of(sourceField), null, null, null, null);
            this.sourceField = sourceField;
        }

        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            return context.getCurrentValue(this.flow, sourceField);
        }

        @Override
        public ReserveCalcStep copy() {
            CopyStep copy = new CopyStep(fieldName, sourceField);
            copy.flow = this.flow;
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            return copy;
        }
    }

    // 8. ContextConditionStep: For selecting between flow results
    public static class ContextConditionStep extends ReserveCalcStep {
        private final Function<Map<String, BigDecimal>, BigDecimal> conditionLogic;

        public ContextConditionStep(ReserveField fieldName, List<ReserveField> dependencyFields,
                                    Function<Map<String, BigDecimal>, BigDecimal> conditionLogic) {
            super(fieldName, dependencyFields, null, null, null, null);
            this.conditionLogic = conditionLogic;
        }

        @Override
        protected BigDecimal compute(ReserveCalcContext context) {
            // This would typically be used to select between different flow values
            Map<String, BigDecimal> flowValues = new HashMap<>();

            // Collect values from different flows
            for (CalculationFlow flow : CalculationFlow.values()) {
                BigDecimal value = context.getCurrentValue(flow, fieldName);
                flowValues.put(flow.name() + "_" + fieldName.name(), value);
            }

            return conditionLogic.apply(flowValues);
        }

        @Override
        public ReserveCalcStep copy() {
            ContextConditionStep copy = new ContextConditionStep(fieldName, dependencyFields, conditionLogic);
            copy.flow = this.flow;
            copy.originalValue = this.originalValue;
            copy.previousValue = this.previousValue;
            copy.currentValue = this.currentValue;
            return copy;
        }
    }
}