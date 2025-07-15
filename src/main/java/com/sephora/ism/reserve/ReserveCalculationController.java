package com.sephora.ism.reserve;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/reserve")
public class ReserveCalculationController {
    @PostMapping("/calculate/pojo")
    public Map<String, BigDecimal> calculateFromPojo(@RequestBody Inventory skulocRecord) {
        ReserveCalculationEngine engine = ReserveCalculationEngine.setupEngine();
        ReserveCalcContext context = new ReserveCalcContext();

        new InitialValueWrapper(skulocRecord).validate().populateContext(context);

        engine.calculate(context);
        return context.getAll();
    }

    @PostMapping("/calculate/map")
    public Map<String, BigDecimal> calculateFromMap(@RequestBody Map<String, BigDecimal> fieldValues) {
        ReserveCalculationEngine engine = ReserveCalculationEngine.setupEngine();
        ReserveCalcContext context = new ReserveCalcContext();

        new InitialValueWrapper(fieldValues).validate().populateContext(context);

        engine.calculate(context);
        return context.getAll();
    }

    @GetMapping("/test")
    public Map<String, BigDecimal> runTest() {
        ReserveCalculationEngine engine = ReserveCalculationEngine.setupEngine();
        ReserveCalcContext context = new ReserveCalcContext();

        // Sample test data
        context.put("onHand", new BigDecimal("100"));
        context.put("rohm", new BigDecimal("10"));
        context.put("lost", new BigDecimal("5"));
        context.put("damaged", new BigDecimal("2"));

        engine.calculate(context);
        return context.getAll();
    }
}