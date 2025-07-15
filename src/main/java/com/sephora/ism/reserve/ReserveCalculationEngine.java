package com.sephora.ism.reserve;


import java.util.LinkedHashMap;
import java.util.List;

public class ReserveCalculationEngine {

    public static ReserveCalculationEngine setupEngine() {
        ReserveCalculationEngine engine = new ReserveCalculationEngine();

        engine.addStep(new SkulocFieldStep("onHand"));
        engine.addStep(new SkulocFieldStep("rohm"));
        engine.addStep(new SkulocFieldStep("lost"));
        engine.addStep(new SkulocFieldStep("damaged"));

        engine.addStep(new CalculationStep(
            "initialAfs",
            List.of("onHand", "rohm", "lost", "damaged"),
            (inputs, modifierSet) -> inputs.get("onHand")
                .subtract(inputs.get("rohm"))
                .subtract(inputs.get("lost"))
                .subtract(inputs.get("damaged")),
            null
        ));

        engine.addStep(new SkulocFieldStep("retPickReserve"));
        engine.addStep(new SkulocFieldStep("dotPickReserve"));
        engine.addStep(new SkulocFieldStep("retHardReserveAtsYes"));
        engine.addStep(new SkulocFieldStep("dotHardReserveAtsYes"));

        engine.addStep(new CalculationStep(
            "uncommit",
            List.of("initialAfs", "retPickReserve", "dotPickReserve", "retHardReserveAtsYes", "dotHardReserveAtsYes"),
            (inputs, modifierSet) -> inputs.get("initialAfs")
                .subtract(inputs.get("retPickReserve"))
                .subtract(inputs.get("dotPickReserve"))
                .subtract(inputs.get("retHardReserveAtsYes"))
                .subtract(inputs.get("dotHardReserveAtsYes")),
            null
        ));

        engine.addStep(new SkulocFieldStep("dotOpenCustOrder"));
        engine.addStep(new SkulocFieldStep("dotShipNotBill"));
        engine.addStep(new SkulocFieldStep("retOpenCustOrder"));
        engine.addStep(new SkulocFieldStep("retShipNotBill"));

        engine.addStep(new CalculationStep(
            "finalReserve",
            List.of("uncommit", "dotOpenCustOrder", "dotShipNotBill", "retOpenCustOrder", "retShipNotBill"),
            (inputs, modifierSet) -> inputs.get("uncommit")
                .subtract(inputs.get("dotOpenCustOrder"))
                .subtract(inputs.get("dotShipNotBill"))
                .subtract(inputs.get("retOpenCustOrder"))
                .subtract(inputs.get("retShipNotBill")),
            null
        ));

        return engine;
    }
    private final LinkedHashMap<String, ReserveCalcStep> stepMap = new LinkedHashMap<>();

    public void addStep(ReserveCalcStep step) {
        stepMap.put(step.getFieldName(), step);
    }

    public void calculate(ReserveCalcContext context, ReserveCalcModifierSet modifierSet) {
        for (ReserveCalcStep step : stepMap.values()) {
            step.calculateValue(context, modifierSet);
        }
    }
}