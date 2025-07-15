package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class InitialValueWrapper {
    private final Map<String, BigDecimal> fieldValues = new LinkedHashMap<>();

    public InitialValueWrapper(Inventory skulocPojo) {
        this.fieldValues.put("onHand", skulocPojo.getSsohu());
        this.fieldValues.put("rohm", skulocPojo.getSsrohm());
        this.fieldValues.put("lost", skulocPojo.getSslost());
        this.fieldValues.put("damaged", skulocPojo.getSsdmg());
        this.fieldValues.put("retPickReserve", skulocPojo.getSsrohp());
        this.fieldValues.put("dotShipNotBill", skulocPojo.getDotShipNotBill());
        this.fieldValues.put("dotOpenCustOrder", skulocPojo.getDotOpenCustOrder());
    }

    public InitialValueWrapper(Map<String, BigDecimal> fieldValues) {
        this.fieldValues.putAll(fieldValues);
    }

    public void populateContext(ReserveCalcContext context) {
        fieldValues.forEach(context::put);
    }

    public InitialValueWrapper validate() {
        fieldValues.replaceAll((k, v) -> v != null ? v : BigDecimal.ZERO);
        return this;
    }

    public BigDecimal getValue(String fieldName) {
        return fieldValues.getOrDefault(fieldName, BigDecimal.ZERO);
    }
}