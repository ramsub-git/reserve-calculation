package com.sephora.ism.reserve;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

import com.sephora.ism.reserve.ReserveField;

@RestController
@RequestMapping("/reserve")
public class ReserveCalculationController {
    @PostMapping("/calculate/pojo")
    public Map<ReserveField, BigDecimal> calculateFromPojo(@RequestBody Inventory skulocRecord) {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();
        ReserveCalculationEngine.setupReserveCalculationSteps(engine);
        ReserveCalcContext context = new ReserveCalcContext();

        InitialValueWrapper initialValueWrapper = InitialValueWrapper.fromInventory(skulocRecord);

        context.setInitialValueWrapper(initialValueWrapper);

        engine.calculate(context);
        return context.getAll();
    }

    @PostMapping("/calculate/map")
    public Map<ReserveField, BigDecimal> calculateFromMap(@RequestBody Map<String, BigDecimal> fieldValues) {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();
        ReserveCalculationEngine.setupReserveCalculationSteps(engine);
        ReserveCalcContext context = new ReserveCalcContext();

        InitialValueWrapper initialValueWrapper = InitialValueWrapper.fromMap(fieldValues);
        context.setInitialValueWrapper(initialValueWrapper);

        engine.calculate(context);
        return context.getAll();
    }

    @GetMapping("/test")
    public Map<ReserveField, BigDecimal> runTest() {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();
        ReserveCalculationEngine.setupReserveCalculationSteps(engine);

        ReserveCalcContext context = new ReserveCalcContext();
//        context.setFlow(CalculationFlow.OMS);


        Map<String, BigDecimal> skulocData = Map.ofEntries(
                Map.entry(ReserveField.ONHAND.name(), new BigDecimal("4593")),
                Map.entry(ReserveField.ROHM.name(), new BigDecimal("0")),
                Map.entry(ReserveField.LOST.name(), new BigDecimal("1")),
                Map.entry(ReserveField.OOBADJ.name(), new BigDecimal("535")),

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

        // Log specific field history
        ReserveCalculationLogger.logRunningTotalHistory(context,
                ReserveField.INITAFS, ReserveField.UNCOMAFS, ReserveField.DOTATS, ReserveField.RETAILATS);


        return context.getAll();
    }

}