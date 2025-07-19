package com.sephora.ism.reserve;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sephora.ism.reserve.ReserveField;

@RestController
@RequestMapping("/reserve")
public class ReserveCalculationController {
    @PostMapping("/calculate/pojo")
    public Map<ReserveField, ReserveCalcStep> calculateFromPojo(@RequestBody Inventory skulocRecord) {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();
        ReserveCalculationEngine.setupReserveCalculationSteps(engine);
        ReserveCalcContext context = new ReserveCalcContext();

        InitialValueWrapper initialValueWrapper = InitialValueWrapper.fromInventory(skulocRecord);

        context.setInitialValueWrapper(initialValueWrapper);

        engine.calculate(context);
        return context.getAll(CalculationFlow.OMS);
    }

    @PostMapping("/calculate/map")
    public Map<ReserveField, ReserveCalcStep> calculateFromMap(@RequestBody Map<String, BigDecimal> fieldValues) {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();
        ReserveCalculationEngine.setupReserveCalculationSteps(engine);
        ReserveCalcContext context = new ReserveCalcContext();

        InitialValueWrapper initialValueWrapper = InitialValueWrapper.fromMap(fieldValues);
        context.setInitialValueWrapper(initialValueWrapper);

        engine.calculate(context);
        return context.getAll(CalculationFlow.OMS);
    }

    @GetMapping("/test")
    public Map<String, Object> runTest() {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();
        ReserveCalculationEngine.setupReserveCalculationSteps(engine);

        ReserveCalcContext context = new ReserveCalcContext();

        Map<String, BigDecimal> skulocData = Map.ofEntries(
                Map.entry(ReserveField.ONHAND.name(), new BigDecimal("626")),
                Map.entry(ReserveField.ROHM.name(), new BigDecimal("0")),
                Map.entry(ReserveField.LOST.name(), new BigDecimal("0")),
                Map.entry(ReserveField.OOBADJ.name(), new BigDecimal("0")),

                Map.entry(ReserveField.SNB.name(), new BigDecimal("1")),
                Map.entry(ReserveField.DTCO.name(), new BigDecimal("0")),
                Map.entry(ReserveField.ROHP.name(), new BigDecimal("0")),
                Map.entry(ReserveField.DOTHRY.name(), new BigDecimal("0")),
                Map.entry(ReserveField.DOTHRN.name(), new BigDecimal("0")),
                Map.entry(ReserveField.RETHRY.name(), new BigDecimal("0")),
                Map.entry(ReserveField.RETHRN.name(), new BigDecimal("0")),
                Map.entry(ReserveField.HLDHR.name(), new BigDecimal("0")),
                Map.entry(ReserveField.DOTRSV.name(), new BigDecimal("255")),
                Map.entry(ReserveField.RETRSV.name(), new BigDecimal("84")),
                Map.entry(ReserveField.DOTOUTB.name(), new BigDecimal("255")),
                Map.entry(ReserveField.NEED.name(), new BigDecimal("84"))
        );

        InitialValueWrapper initialValueWrapper = InitialValueWrapper.fromMap(skulocData);
        context.setInitialValueWrapper(initialValueWrapper);
        engine.calculate(context);

        // Log specific field history
        // ReserveCalculationLogger.logRunningTotalHistory(context, ReserveField.INITAFS, ReserveField.UNCOMAFS, ReserveField.DOTATS, ReserveField.RETAILATS);

        // Build comprehensive result map
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. Add all calculated values from OMS flow
        Map<ReserveField, BigDecimal> allValues = context.getAll();
        result.put("calculatedValues", allValues);

        // 2. Add key metrics separately for easy viewing
        Map<String, BigDecimal> keyMetrics = new LinkedHashMap<>();
        keyMetrics.put("INITAFS", context.get(ReserveField.INITAFS));
        keyMetrics.put("UNCOMAFS", context.get(ReserveField.UNCOMAFS));
        keyMetrics.put("@DOTATS", context.get(ReserveField.DOTATS));
        keyMetrics.put("@RETAILATS", context.get(ReserveField.RETAILATS));
        keyMetrics.put("@UNCOMMIT", context.get(ReserveField.UNCOMMIT));
        keyMetrics.put("@COMMITTED", context.get(ReserveField.COMMITTED));
        keyMetrics.put("@OMSFINAL", context.get(ReserveField.OMSFINAL));
        keyMetrics.put("@RETFINAL", context.get(ReserveField.RETFINAL));
        result.put("keyMetrics", keyMetrics);

        // 3. Add constraint analysis
        Map<String, Map<String, BigDecimal>> constraints = new LinkedHashMap<>();
        addConstraintInfo(constraints, "SNB", context);
        addConstraintInfo(constraints, "DTCO", context);
        addConstraintInfo(constraints, "ROHP", context);
        addConstraintInfo(constraints, "DOTHRY", context);
        addConstraintInfo(constraints, "DOTHRN", context);
        addConstraintInfo(constraints, "RETHRY", context);
        addConstraintInfo(constraints, "RETHRN", context);
        addConstraintInfo(constraints, "HLDHR", context);
        addConstraintInfo(constraints, "DOTRSV", context);
        addConstraintInfo(constraints, "RETRSV", context);
        addConstraintInfo(constraints, "AOUTBV", context);
        addConstraintInfo(constraints, "ANEED", context);
        result.put("constraintAnalysis", constraints);

        // 4. Add running inventory if available
        BigDecimal runningAfs = context.get(ReserveField.RUNNING_AFS);
        if (runningAfs != null) {
            result.put("runningInventory", runningAfs);
        }

        // 5. Add dynamic values
        Map<ReserveField, BigDecimal> dynamicValues = context.getDynamicValues();
        if (!dynamicValues.isEmpty()) {
            result.put("dynamicValues", dynamicValues);
        }

        return result;
    }

    // Helper method to add constraint information
    private void addConstraintInfo(Map<String, Map<String, BigDecimal>> constraints,
                                   String fieldName, ReserveCalcContext context) {
        try {
            ReserveField baseField = ReserveField.valueOf(fieldName);
            ReserveField constraintField = ReserveField.valueOf(fieldName + "X");
            ReserveField actualField = ReserveField.valueOf(fieldName + "A");

            Map<String, BigDecimal> info = new LinkedHashMap<>();
            info.put("base", context.get(baseField));
            info.put("constraint", context.get(constraintField));
            info.put("actual", context.get(actualField));

            constraints.put(fieldName, info);
        } catch (IllegalArgumentException e) {
            // Field doesn't exist, skip
        }
    }

    // Alternative: Simple version that returns all fields as a flat map
    @GetMapping("/test/simple")
    public Map<String, BigDecimal> runTestSimple() {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();
        ReserveCalculationEngine.setupReserveCalculationSteps(engine);

        ReserveCalcContext context = new ReserveCalcContext();

        Map<String, BigDecimal> skulocData = Map.ofEntries(
                Map.entry(ReserveField.ONHAND.name(), new BigDecimal("4593")),
                Map.entry(ReserveField.ROHM.name(), new BigDecimal("0")),
                Map.entry(ReserveField.LOST.name(), new BigDecimal("1")),
                Map.entry(ReserveField.OOBADJ.name(), new BigDecimal("0")),
                Map.entry(ReserveField.SNB.name(), new BigDecimal("0")),
                Map.entry(ReserveField.DTCO.name(), new BigDecimal("0")),
                Map.entry(ReserveField.ROHP.name(), new BigDecimal("0")),
                Map.entry(ReserveField.DOTHRY.name(), new BigDecimal("0")),
                Map.entry(ReserveField.DOTHRN.name(), new BigDecimal("5984")),
                Map.entry(ReserveField.RETHRY.name(), new BigDecimal("0")),
                Map.entry(ReserveField.RETHRN.name(), new BigDecimal("1500")),
                Map.entry(ReserveField.HLDHR.name(), new BigDecimal("2992")),
                Map.entry(ReserveField.DOTRSV.name(), new BigDecimal("2992")),
                Map.entry(ReserveField.RETRSV.name(), new BigDecimal("0")),
                Map.entry(ReserveField.DOTOUTB.name(), new BigDecimal("0")),
                Map.entry(ReserveField.NEED.name(), new BigDecimal("0"))
        );

        InitialValueWrapper initialValueWrapper = InitialValueWrapper.fromMap(skulocData);
        context.setInitialValueWrapper(initialValueWrapper);
        engine.calculate(context);

        // Convert to simple string->BigDecimal map for JSON serialization
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        Map<ReserveField, BigDecimal> allValues = context.getAll();

        // Add all fields with their names as strings
        for (Map.Entry<ReserveField, BigDecimal> entry : allValues.entrySet()) {
            result.put(entry.getKey().name(), entry.getValue());
        }

        return result;
    }


}