package com.sephora.ism.reserve;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/reserve")
public class ReserveCalculationController {
    @PostMapping("/calculate/pojo")
    public Map<String, BigDecimal> calculateFromPojo(@RequestBody Inventory skulocRecord) {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();
        ReserveCalculationEngine.setupReserveCalculationSteps(engine);
        ReserveCalcContext context = new ReserveCalcContext();

        InitialValueWrapper.fromInventory(skulocRecord).applyToContext(context);

        engine.calculate(context);
        return context.getAll();
    }

    @PostMapping("/calculate/map")
    public Map<String, BigDecimal> calculateFromMap(@RequestBody Map<String, BigDecimal> fieldValues) {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();
        ReserveCalculationEngine.setupReserveCalculationSteps(engine);
        ReserveCalcContext context = new ReserveCalcContext();

        new InitialValueWrapper(fieldValues).applyToContext(context);

        engine.calculate(context);
        return context.getAll();
    }

    @GetMapping("/test")
    public Map<String, BigDecimal> runTest() {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();
        ReserveCalculationEngine.setupReserveCalculationSteps(engine);

        ReserveCalcContext context = new ReserveCalcContext();
        // context.setFlow(CalculationFlow.OMS); // Ensure default flow is explicitly set

        // Mandatory fields
        context.put("onHand", new BigDecimal("100"));
        context.put("rohm", new BigDecimal("10"));
        context.put("lost", new BigDecimal("5"));

        // Add required additional fields with sample values
        context.put("dotShipNotBill", BigDecimal.ZERO);
        context.put("dotOpenCustOrder", BigDecimal.ZERO);
        context.put("retPickReserve", BigDecimal.ZERO);
        context.put("dotHardReserveAtsYes", BigDecimal.ZERO);
        context.put("dotHardReserveAtsNo", BigDecimal.ZERO);
        context.put("retHardReserveAtsYes", BigDecimal.ZERO);
        context.put("retHardReserveAtsNo", BigDecimal.ZERO);
        context.put("heldHardReserve", BigDecimal.ZERO);
        context.put("dotReserve", BigDecimal.ZERO);
        context.put("retReserve", BigDecimal.ZERO);
        context.put("dotOutb", BigDecimal.ZERO);
        context.put("retNeed", BigDecimal.ZERO);
        context.put("oobAdjustment", BigDecimal.ZERO);

        engine.calculate(context);

        return context.getAll();
    }

}