package com.sephora.ism.reserve;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.logging.Logger;


@RestController
@RequestMapping("/reserve")
public class ReserveCalculationController {
    private static final Logger LOGGER = Logger.getLogger(ReserveCalculationController.class.getName());


    @GetMapping("/test")
    public Map<String, BigDecimal> runTest() {
        // Setup Calculation Engine
        ReserveCalculationEngine engine = ReserveCalculationEngine.setupEngine();

        // Create context and populate with sample data
        ReserveCalcContext context = new ReserveCalcContext();
        context.put("onHand", new BigDecimal("626"));
        context.put("rohm", BigDecimal.ZERO);
        context.put("lost", BigDecimal.ZERO);
        context.put("damaged", BigDecimal.ZERO);
        context.put("dotShipNotBill", new BigDecimal("1"));
        context.put("retPickReserve", BigDecimal.ZERO);
        context.put("dotOpenCustOrder", BigDecimal.ZERO);

        // Additional fields from PDF
        context.put("dotHardReserveAtsYes", BigDecimal.ZERO);
        context.put("dotHardReserveAtsNo", BigDecimal.ZERO);
        context.put("retHardReserveAtsYes", BigDecimal.ZERO);
        context.put("retHardReserveAtsNo", BigDecimal.ZERO);
        context.put("heldHardReserve", BigDecimal.ZERO);
        context.put("dotReserve", new BigDecimal("255"));
        context.put("retReserve", new BigDecimal("84"));
        context.put("dotOutb", new BigDecimal("255"));
        context.put("retNeed", new BigDecimal("84"));

        // Create modifier set
        ReserveCalcModifierSet modifierSet = new ReserveCalcModifierSet();

        // Run calculation
        engine.calculate(context, modifierSet);

        // Return results
        return context.getAll();
    }






    public ReserveCalcContext runReserveCalculation(Inventory skulocRecord) {
        ReserveCalculationEngine engine = setupReserveCalculationEngine(skulocRecord);
        ReserveCalcContext context = new ReserveCalcContext();
        ReserveCalcModifierSet modifierSet = new ReserveCalcModifierSet();

        engine.calculate(context, modifierSet);
        return context;
    }

private ReserveCalculationEngine setupReserveCalculationEngine(Inventory skulocRecord) {
    ReserveCalculationEngine engine = new ReserveCalculationEngine();

    // Existing Skuloc Field Steps (keep as before)
    engine.addStep(new SkulocFieldStep("onHand", skulocRecord.getSsohu()), Map.of());
    engine.addStep(new SkulocFieldStep("rohm", skulocRecord.getSsrohm()), Map.of());
    engine.addStep(new SkulocFieldStep("lost", skulocRecord.getSslost()), Map.of());
    engine.addStep(new SkulocFieldStep("damaged", skulocRecord.getSsdmg()), Map.of());
    engine.addStep(new SkulocFieldStep("dotShipNotBill", skulocRecord.getDotShipNotBill()), Map.of());
    engine.addStep(new SkulocFieldStep("retPickReserve", skulocRecord.getSsrohp()), Map.of());
    engine.addStep(new SkulocFieldStep("dotOpenCustOrder", skulocRecord.getDotOpenCustOrder()), Map.of());
    
    // Additional fields from PDF
    engine.addStep(new SkulocFieldStep("dotHardReserveAtsYes", BigDecimal.ZERO), Map.of());
    engine.addStep(new SkulocFieldStep("dotHardReserveAtsNo", BigDecimal.ZERO), Map.of());
    engine.addStep(new SkulocFieldStep("retHardReserveAtsYes", BigDecimal.ZERO), Map.of());
    engine.addStep(new SkulocFieldStep("retHardReserveAtsNo", BigDecimal.ZERO), Map.of());
    engine.addStep(new SkulocFieldStep("heldHardReserve", BigDecimal.ZERO), Map.of());
    engine.addStep(new SkulocFieldStep("dotReserve", BigDecimal.ZERO), Map.of());
    engine.addStep(new SkulocFieldStep("retReserve", BigDecimal.ZERO), Map.of());
    engine.addStep(new SkulocFieldStep("dotOutb", BigDecimal.ZERO), Map.of());
    engine.addStep(new SkulocFieldStep("retNeed", BigDecimal.ZERO), Map.of());

    // 1. Initial AFS Calculation
    engine.addStep(
        new CalculationStep(
            "initialAfs", 
            List.of("onHand", "rohm", "lost", "damaged"),
            (inputs, modifierSet) -> inputs.get("onHand")
                .subtract(inputs.get("rohm"))
                .subtract(inputs.get("lost"))
                .subtract(inputs.get("damaged"))
        ),
        Map.of(
            CalculationFlow.JEI, new CalculationStep(
                "initialAfsJei", 
                List.of("onHand", "lost"),
                (inputs, modifierSet) -> inputs.get("onHand").subtract(inputs.get("lost"))
            )
        )
    );

    // 2. Uncommitted Inventory Calculation
    engine.addStep(
        new CalculationStep(
            "uncommittedInventory", 
            List.of("initialAfs", "dotShipNotBill", "retPickReserve", "dotOpenCustOrder"),
            (inputs, modifierSet) -> inputs.get("initialAfs")
                .subtract(inputs.get("dotShipNotBill"))
                .subtract(inputs.get("retPickReserve"))
                .subtract(inputs.get("dotOpenCustOrder"))
        ),
        Map.of(
            CalculationFlow.JEI, new CalculationStep(
                "uncommittedInventoryJei", 
                List.of("initialAfs"),
                (inputs, modifierSet) -> inputs.get("initialAfs")
            )
        )
    );

    // 3. Retail ATS Calculation
    engine.addStep(
        new CalculationStep(
            "retailAts", 
            List.of("retHardReserveAtsYes", "retReserve", "retNeed"),
            (inputs, modifierSet) -> inputs.get("retHardReserveAtsYes")
                .add(inputs.get("retReserve"))
                .add(inputs.get("retNeed"))
        ),
        Map.of(
            CalculationFlow.JEI, new CalculationStep(
                "retailAtsJei", 
                List.of("retHardReserveAtsYes"),
                (inputs, modifierSet) -> inputs.get("retHardReserveAtsYes")
            )
        )
    );

    // 4. Dotcom ATS Calculation
    engine.addStep(
        new CalculationStep(
            "dotAts", 
            List.of("dotHardReserveAtsYes", "dotReserve", "dotOutb"),
            (inputs, modifierSet) -> inputs.get("dotHardReserveAtsYes")
                .add(inputs.get("dotReserve"))
                .add(inputs.get("dotOutb"))
        ),
        Map.of(
            CalculationFlow.JEI, new CalculationStep(
                "dotAtsJei", 
                List.of("dotHardReserveAtsYes"),
                (inputs, modifierSet) -> inputs.get("dotHardReserveAtsYes")
            )
        )
    );

    // 5. Committed Inventory Calculation
    engine.addStep(
        new CalculationStep(
            "committedInventory", 
            List.of("dotShipNotBill", "retPickReserve", "dotOpenCustOrder"),
            (inputs, modifierSet) -> inputs.get("dotShipNotBill")
                .add(inputs.get("retPickReserve"))
                .add(inputs.get("dotOpenCustOrder"))
        ),
        Map.of()
    );

    // 6. Uncommitted Hard Reserve Calculation
    engine.addStep(
        new CalculationStep(
            "uncommittedHardReserve", 
            List.of("dotHardReserveAtsNo", "retHardReserveAtsNo", "heldHardReserve"),
            (inputs, modifierSet) -> inputs.get("dotHardReserveAtsNo")
                .add(inputs.get("retHardReserveAtsNo"))
                .add(inputs.get("heldHardReserve"))
        ),
        Map.of()
    );

    // 7. OMS Supply Calculation
    engine.addStep(
        new CalculationStep(
            "omsSupply", 
            List.of("dotAts", "dotOpenCustOrder"),
            (inputs, modifierSet) -> inputs.get("dotAts")
                .add(inputs.get("dotOpenCustOrder"))
        ),
        Map.of()
    );

    // 8. Retail Final Supply Calculation
    engine.addStep(
        new CalculationStep(
            "retailFinal", 
            List.of("retailAts", "retPickReserve", "retHardReserveAtsNo", "heldHardReserve"),
            (inputs, modifierSet) -> inputs.get("retailAts")
                .add(inputs.get("retPickReserve"))
                .add(inputs.get("retHardReserveAtsNo"))
                .add(inputs.get("heldHardReserve"))
        ),
        Map.of() 
    );

    // 9. OMS Final Supply Calculation
    engine.addStep(
        new CalculationStep(
            "omsFinal", 
            List.of("omsSupply", "dotOpenCustOrder"),
            (inputs, modifierSet) -> {
                // Implement buyer class logic if applicable
                return inputs.get("omsSupply");
            }
        ),
        Map.of()
    );

    return engine;
}

    // Test method to demonstrate calculation
    public void demonstrateReserveCalculation() {
        // Create a sample Inventory record with values from Excel PDF
        Inventory sampleInventory = createSampleInventoryFromPdf();

        // Run calculation
        ReserveCalcContext context = runReserveCalculation(sampleInventory);

        // Log results
        LOGGER.info("Calculation Results:");
        context.getAll().forEach((field, value) ->
                LOGGER.info(field + ": " + value)
        );

        // Optional: Log modifier values
        context.getAllModifierValues().forEach((field, modifiers) -> {
            LOGGER.info("Modifier values for " + field + ":");
            modifiers.forEach((flow, value) ->
                    LOGGER.info(flow + ": " + value)
            );
        });
    }


    // Create a sample Inventory record with values matching Excel PDF
    private Inventory createSampleInventoryFromPdf() {
        return new Inventory(
                new BigDecimal("626"),   // onHand
                new BigDecimal("0"),     // rohm
                new BigDecimal("0"),     // lost
                new BigDecimal("0"),     // damaged
                new BigDecimal("0"),     // retPickReserve
                new BigDecimal("1"),     // dotShipNotBill
                new BigDecimal("0"),     // dotOpenCustOrder

                // Additional fields from PDF
                new BigDecimal("0"),     // dotHardReserveAtsYes
                new BigDecimal("0"),     // dotHardReserveAtsNo
                new BigDecimal("0"),     // retHardReserveAtsYes
                new BigDecimal("0"),     // retHardReserveAtsNo
                new BigDecimal("0"),     // heldHardReserve
                new BigDecimal("255"),   // dotReserve
                new BigDecimal("84"),    // retReserve
                new BigDecimal("255"),   // dotOutb
                new BigDecimal("84")     // retNeed
        );
    }




}