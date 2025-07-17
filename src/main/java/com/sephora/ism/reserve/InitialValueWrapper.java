// InitialValueWrapper.java - Updated to remove damaged field
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sephora.ism.reserve.Inventory;

public class InitialValueWrapper {

    private final Map<String, BigDecimal> values;

    InitialValueWrapper(Map<String, BigDecimal> values) {
        this.values = values;
    }

    public static InitialValueWrapper fromMap(Map<String, BigDecimal> inputMap) {
        Map<String, BigDecimal> cleaned = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : inputMap.entrySet()) {
            cleaned.put(entry.getKey(), sanitize(entry.getValue()));
        }
        return new InitialValueWrapper(cleaned);
    }

    public static InitialValueWrapper fromInventory(Inventory inventory) {
        Map<String, BigDecimal> temp = new HashMap<>();
        // Example: map fields explicitly
        temp.put("onHand", sanitize(inventory.getOnHand()));
        temp.put("rohm", sanitize(inventory.getRohm()));
        temp.put("lost", sanitize(inventory.getLost()));
        temp.put("oobAdjustment", sanitize(inventory.getOobAdjustment()));
        temp.put("dotHardReserveAtsYes", sanitize(inventory.getDotHardReserveAtsYes()));
        temp.put("retHardReserveAtsYes", sanitize(inventory.getRetHardReserveAtsYes()));
        temp.put("heldHardReserve", sanitize(inventory.getHeldHardReserve()));
        // Add more as required.
        return new InitialValueWrapper(temp);
    }

    public void applyToContext(ReserveCalcContext context) {
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            context.put(entry.getKey(), entry.getValue());
        }
    }

    private static BigDecimal sanitize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Negative value not allowed: " + value);
        }
        return value;
    }

    public Map<String, BigDecimal> getValues() {
        return new HashMap<>(values);
    }

    public BigDecimal get(String fieldName) {
        return values.getOrDefault(fieldName, BigDecimal.ZERO);
    }

}