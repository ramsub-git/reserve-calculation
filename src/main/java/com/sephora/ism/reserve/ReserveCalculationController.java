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
//        context.setFlow(CalculationFlow.OMS);


        Map<String, BigDecimal> skulocData = Map.ofEntries(
                Map.entry("ONHAND", new BigDecimal("4593")),
                Map.entry("ROHM", new BigDecimal("0")),
                Map.entry("LOST", new BigDecimal("1")),
                Map.entry("OOBADJ", new BigDecimal("535")),

                Map.entry("SNB", new BigDecimal("0")),
                Map.entry("DTCO", new BigDecimal("0")),
                Map.entry("ROHP", new BigDecimal("0")),
                Map.entry("DOTHRY", new BigDecimal("0")),
                Map.entry("DOTHRN", new BigDecimal("5984")),
                Map.entry("RETHRY", new BigDecimal("0")),
                Map.entry("RETHRN", new BigDecimal("1500")),
                Map.entry("HLDHR", new BigDecimal("2992")),
                Map.entry("DOTRSV", new BigDecimal("2992")),
                Map.entry("RETRSV", new BigDecimal("0")),
                Map.entry("DOTOUTB", new BigDecimal("0")),
                Map.entry("NEED", new BigDecimal("0"))
        );

        InitialValueWrapper initialValueWrapper = InitialValueWrapper.fromMap(skulocData);
        context.setInitialValueWrapper(initialValueWrapper);
        engine.calculate(context);

        // Log specific field history
        ReserveCalculationLogger.logRunningTotalHistory(context,
                "INITAFS", "UNCOMAFS", "@DOTATS", "@RETAILATS");


        return context.getAll();
    }

}