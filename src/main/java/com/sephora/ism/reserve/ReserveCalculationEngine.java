package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


public class ReserveCalculationEngine {
    private static final Logger LOGGER = Logger.getLogger(ReserveCalculationEngine.class.getName());

    private final List<ReserveCalcStep> steps = new ArrayList<>();
    private CalculationFlow primaryFlow = CalculationFlow.OMS;

    public void addStep(
        ReserveCalcStep step, 
        java.util.Map<CalculationFlow, ReserveCalcStep> flowSteps
    ) {
        steps.add(step);
        step.setCalculationFlows(flowSteps);
    }

    public void setPrimaryFlow(CalculationFlow flow) {
        this.primaryFlow = flow;
    }

    public void calculate(ReserveCalcContext context, ReserveCalcModifierSet modifierSet) {
        LOGGER.info("Starting reserve calculation");
        
        for (ReserveCalcStep step : steps) {
            step.calculateValue(context, modifierSet);
        }
        
        context.logContextState();
        LOGGER.info("Reserve calculation completed");
    }

    public static ReserveCalculationEngine setupEngine() {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();

        // Existing Skuloc Field Steps (keep as before)
//        engine.addStep(new SkulocFieldStep("onHand", skulocRecord.getSsohu()), Map.of());
//        engine.addStep(new SkulocFieldStep("rohm", skulocRecord.getSsrohm()), Map.of());
//        engine.addStep(new SkulocFieldStep("lost", skulocRecord.getSslost()), Map.of());
//        engine.addStep(new SkulocFieldStep("damaged", skulocRecord.getSsdmg()), Map.of());
//        engine.addStep(new SkulocFieldStep("dotShipNotBill", skulocRecord.getDotShipNotBill()), Map.of());
//        engine.addStep(new SkulocFieldStep("retPickReserve", skulocRecord.getSsrohp()), Map.of());
//        engine.addStep(new SkulocFieldStep("dotOpenCustOrder", skulocRecord.getDotOpenCustOrder()), Map.of());
//
//        // Additional fields from PDF
//        engine.addStep(new SkulocFieldStep("dotHardReserveAtsYes", BigDecimal.ZERO), Map.of());
//        engine.addStep(new SkulocFieldStep("dotHardReserveAtsNo", BigDecimal.ZERO), Map.of());
//        engine.addStep(new SkulocFieldStep("retHardReserveAtsYes", BigDecimal.ZERO), Map.of());
//        engine.addStep(new SkulocFieldStep("retHardReserveAtsNo", BigDecimal.ZERO), Map.of());
//        engine.addStep(new SkulocFieldStep("heldHardReserve", BigDecimal.ZERO), Map.of());
//        engine.addStep(new SkulocFieldStep("dotReserve", BigDecimal.ZERO), Map.of());
//        engine.addStep(new SkulocFieldStep("retReserve", BigDecimal.ZERO), Map.of());
//        engine.addStep(new SkulocFieldStep("dotOutb", BigDecimal.ZERO), Map.of());
//        engine.addStep(new SkulocFieldStep("retNeed", BigDecimal.ZERO), Map.of());

        // Initial AFS Calculation
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


}