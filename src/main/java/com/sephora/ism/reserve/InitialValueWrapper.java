// InitialValueWrapper.java - Updated to remove damaged field
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
        this.fieldValues.put("oobAdjustment", skulocPojo.getOobAdjustment());
        this.fieldValues.put("retPickReserve", skulocPojo.getSsrohp());
        this.fieldValues.put("dotShipNotBill", skulocPojo.getDotShipNotBill());
        this.fieldValues.put("dotOpenCustOrder", skulocPojo.getDotOpenCustOrder());
        this.fieldValues.put("retHardReserveAtsYes", skulocPojo.getRetHardReserveAtsYes());
        this.fieldValues.put("retHardReserveAtsNo", skulocPojo.getRetHardReserveAtsNo());
        this.fieldValues.put("dotHardReserveAtsYes", skulocPojo.getDotHardReserveAtsYes());
        this.fieldValues.put("dotHardReserveAtsNo", skulocPojo.getDotHardReserveAtsNo());
        this.fieldValues.put("heldHardReserve", skulocPojo.getHeldHardReserve());
        this.fieldValues.put("dotReserve", skulocPojo.getDotReserve());
        this.fieldValues.put("retReserve", skulocPojo.getRetReserve());
        this.fieldValues.put("dotOutb", skulocPojo.getDotOutb());
        this.fieldValues.put("retNeed", skulocPojo.getRetNeed());
    }

    public InitialValueWrapper(Map<String, BigDecimal> fieldValues) {
        this.fieldValues.putAll(fieldValues);
    }

    public void populateContext(ReserveCalcContext context) {
        fieldValues.forEach(context::put);
    }

    public InitialValueWrapper validate() {
        // Ensure all values are non-null
        fieldValues.replaceAll((k, v) -> v != null ? v : BigDecimal.ZERO);

        // Validate no negative inputs for quantity fields
        fieldValues.forEach((key, value) -> {
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        String.format("Field %s cannot be negative: %s", key, value));
            }
        });

        return this;
    }

    public BigDecimal getValue(String fieldName) {
        return fieldValues.getOrDefault(fieldName, BigDecimal.ZERO);
    }
}