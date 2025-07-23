package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
	public Map<String, BigDecimal> calculateFromMap(@RequestBody Map<String, Object> fieldValues) {
		ReserveCalculationEngine engine = new ReserveCalculationEngine();
		ReserveCalculationEngine.setupReserveCalculationSteps(engine);
		ReserveCalcContext context = new ReserveCalcContext();

		InitialValueWrapper initialValueWrapper = InitialValueWrapper.fromMap(fieldValues);
		context.setInitialValueWrapper(initialValueWrapper);

		engine.calculate(context);
		Map<String, BigDecimal> result = new LinkedHashMap<>();

		// Map the requested field names to their enum values
		result.put("DOTCOMATS", context.get(ReserveField.DOTATS));
		result.put("RETAILATS", context.get(ReserveField.RETAILATS));
		result.put("UNCOMMIT", context.get(ReserveField.UNCOMMIT));
		result.put("COMMITTED", context.get(ReserveField.COMMITTED));
		result.put("UNCOMMHR", context.get(ReserveField.UNCOMMHR));
		result.put("OMSSUP", context.get(ReserveField.OMSSUP));
		result.put("RETFINAL", context.get(ReserveField.RETFINAL));
		result.put("OMSFINAL", context.get(ReserveField.OMSFINAL));

		return result;
	}

	@GetMapping("/test")
	public Map<String, Object> runTest() {
		ReserveCalculationEngine engine = new ReserveCalculationEngine();
		ReserveCalculationEngine.setupReserveCalculationSteps(engine);

		ReserveCalcContext context = new ReserveCalcContext();

		Map<String, Object> skulocData = Map.ofEntries(Map.entry(ReserveField.ONHAND.name(), new BigDecimal("626")),
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
				Map.entry(ReserveField.NEED.name(), new BigDecimal("84")));

//		Map<String, Object> skulocData = Map.ofEntries(Map.entry(ReserveField.ONHAND.name(), new BigDecimal("4593")),
//				Map.entry(ReserveField.ROHM.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.LOST.name(), new BigDecimal("1")),
//				Map.entry(ReserveField.OOBADJ.name(), new BigDecimal("0")), // This is key
//				Map.entry(ReserveField.BYCL.name(), "R"), // This is a buyer class
//				Map.entry(ReserveField.SNB.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.DTCO.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.ROHP.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.DOTHRY.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.DOTHRN.name(), new BigDecimal("5984")),
//				Map.entry(ReserveField.RETHRY.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.RETHRN.name(), new BigDecimal("1500")),
//				Map.entry(ReserveField.HLDHR.name(), new BigDecimal("2992")),
//				Map.entry(ReserveField.DOTRSV.name(), new BigDecimal("2992")),
//				Map.entry(ReserveField.RETRSV.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.DOTOUTB.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.NEED.name(), new BigDecimal("84")));

		InitialValueWrapper initialValueWrapper = InitialValueWrapper.fromMap(skulocData);
		context.setInitialValueWrapper(initialValueWrapper);
		engine.calculate(context);

		// Log specific field history
		// ReserveCalculationLogger.logRunningTotalHistory(context,
		// ReserveField.INITAFS, ReserveField.UNCOMAFS, ReserveField.DOTATS,
		// ReserveField.RETAILATS);

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
	private void addConstraintInfo(Map<String, Map<String, BigDecimal>> constraints, String fieldName,
								   ReserveCalcContext context) {
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

//		Map<String, Object> skulocData = Map.ofEntries(Map.entry(ReserveField.ONHAND.name(), new BigDecimal("4593")),
//				Map.entry(ReserveField.ROHM.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.LOST.name(), new BigDecimal("1")),
//				Map.entry(ReserveField.OOBADJ.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.SNB.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.DTCO.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.ROHP.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.DOTHRY.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.DOTHRN.name(), new BigDecimal("5984")),
//				Map.entry(ReserveField.RETHRY.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.RETHRN.name(), new BigDecimal("1500")),
//				Map.entry(ReserveField.HLDHR.name(), new BigDecimal("2992")),
//				Map.entry(ReserveField.DOTRSV.name(), new BigDecimal("2992")),
//				Map.entry(ReserveField.RETRSV.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.DOTOUTB.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.NEED.name(), new BigDecimal("0")));

//		// ===== TEST 1: Buyer Class R - Should Enable OMS Final =====
//		Map<String, Object> testBuyerClassR = Map.ofEntries(
//				Map.entry(ReserveField.ONHAND.name(), new BigDecimal("1000")),
//				Map.entry(ReserveField.ROHM.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.LOST.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.OOBADJ.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.SNB.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.DTCO.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.ROHP.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.DOTHRY.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.DOTHRN.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.RETHRY.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.RETHRN.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.HLDHR.name(), new BigDecimal("0")),
//				Map.entry(ReserveField.DOTRSV.name(), new BigDecimal("300")), // DOT reserve
//				Map.entry(ReserveField.RETRSV.name(), new BigDecimal("200")), // Retail reserve
//				Map.entry(ReserveField.DOTOUTB.name(), new BigDecimal("400")), // DOT outbound > DOTATS
//				Map.entry(ReserveField.NEED.name(), new BigDecimal("150")), // Retail need
//				Map.entry(ReserveField.BYCL.name(), "R") // Buyer class R
//		);
//		// Expected: OMSFINAL should equal OMSSUP, AOUTBV should be calculated

		// ===== TEST 2: Buyer Class W - Should Enable OMS Final =====
		// ===== TEST 3: Buyer Class D - Should Disable OMS Final =====
		Map<String, Object> testBuyerClassR = Map.ofEntries(
				Map.entry(ReserveField.ONHAND.name(), new BigDecimal("1000")),
				Map.entry(ReserveField.ROHM.name(), new BigDecimal("0")),
				Map.entry(ReserveField.LOST.name(), new BigDecimal("0")),
				Map.entry(ReserveField.OOBADJ.name(), new BigDecimal("0")),
				Map.entry(ReserveField.SNB.name(), new BigDecimal("0")),
				Map.entry(ReserveField.DTCO.name(), new BigDecimal("0")),
				Map.entry(ReserveField.ROHP.name(), new BigDecimal("0")),
				Map.entry(ReserveField.DOTHRY.name(), new BigDecimal("0")),
				Map.entry(ReserveField.DOTHRN.name(), new BigDecimal("0")),
				Map.entry(ReserveField.RETHRY.name(), new BigDecimal("0")),
				Map.entry(ReserveField.RETHRN.name(), new BigDecimal("0")),
				Map.entry(ReserveField.HLDHR.name(), new BigDecimal("0")),
				Map.entry(ReserveField.DOTRSV.name(), new BigDecimal("300")), // DOT reserve
				Map.entry(ReserveField.RETRSV.name(), new BigDecimal("200")), // Retail reserve
				Map.entry(ReserveField.DOTOUTB.name(), new BigDecimal("400")), // DOT outbound > DOTATS
				Map.entry(ReserveField.NEED.name(), new BigDecimal("150")), // Retail need
				Map.entry(ReserveField.BYCL.name(), "R") // Buyer class R
		);
		// Expected: OMSFINAL should equal OMSSUP, AOUTBV should be positive

		InitialValueWrapper initialValueWrapper = InitialValueWrapper.fromMap(testBuyerClassR);
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