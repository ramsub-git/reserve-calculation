package com.sephora.ism.reserve;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;
import java.math.BigDecimal;

@RestController
@RequestMapping("/reserve")
public class ReserveCalculationController {
    @GetMapping("/test")
    public Map<String, BigDecimal> runTest() {
        // This is a test method to run the reserve calculation engine
        ReserveCalculationEngine engine = ReserveCalculationEngine.setupEngine();

        ReserveCalcContext context = new ReserveCalcContext();
        context.put("onHand", new BigDecimal("100"));
        context.put("rohm", new BigDecimal("10"));
        context.put("lost", new BigDecimal("5"));
        context.put("damaged", new BigDecimal("2"));
        context.put("retPickReserve", new BigDecimal("3"));
        context.put("dotPickReserve", new BigDecimal("4"));
        context.put("retHardReserveAtsYes", new BigDecimal("5"));
        context.put("dotHardReserveAtsYes", new BigDecimal("6"));
        context.put("dotOpenCustOrder", new BigDecimal("7"));
        context.put("dotShipNotBill", new BigDecimal("8"));
        context.put("retOpenCustOrder", new BigDecimal("9"));
        context.put("retShipNotBill", new BigDecimal("10"));

        ReserveCalcModifierSet modifierSet = new ReserveCalcModifierSet("JEI");
        engine.calculate(context, modifierSet);

        return context.getAll();
    }
}