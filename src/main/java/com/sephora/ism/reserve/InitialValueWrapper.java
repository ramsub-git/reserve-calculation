// InitialValueWrapper.java - Updated to remove damaged field
package com.sephora.ism.reserve;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sephora.ism.reserve.Inventory;

public class InitialValueWrapper {

    private final Map<ReserveField, BigDecimal> values;

    InitialValueWrapper(Map<ReserveField, BigDecimal> values) {
        this.values = values;
    }

    public static InitialValueWrapper fromMap(Map<String, BigDecimal> inputMap) {
        Map<ReserveField, BigDecimal> cleaned = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : inputMap.entrySet()) {
            try {
                ReserveField field = ReserveField.valueOf(entry.getKey());
                cleaned.put(field, sanitize(entry.getValue()));
            } catch (IllegalArgumentException e) {
                // Ignore or log unknown fields
            }
        }
        return new InitialValueWrapper(cleaned);
    }

    public static InitialValueWrapper fromInventory(Inventory inventory) {
        Map<ReserveField, BigDecimal> temp = new HashMap<>();
        // Basic fields
        temp.put(ReserveField.ONHAND, sanitize(inventory.getOnHand()));
        temp.put(ReserveField.ROHM, sanitize(inventory.getRohm()));
        temp.put(ReserveField.LOST, sanitize(inventory.getLost()));
        temp.put(ReserveField.OOBADJ, sanitize(inventory.getOobAdjustment()));

        // Hard reserves
        temp.put(ReserveField.DOTHRY, sanitize(inventory.getDotHardReserveAtsYes()));
        temp.put(ReserveField.DOTHRN, sanitize(inventory.getDotHardReserveAtsNo()));
        temp.put(ReserveField.RETHRY, sanitize(inventory.getRetHardReserveAtsYes()));
        temp.put(ReserveField.RETHRN, sanitize(inventory.getRetHardReserveAtsNo()));
        temp.put(ReserveField.HLDHR, sanitize(inventory.getHeldHardReserve()));

        // Add these default zeros for fields not in Inventory POJO
        temp.put(ReserveField.SNB, BigDecimal.ZERO);
        temp.put(ReserveField.DTCO, BigDecimal.ZERO);
        temp.put(ReserveField.ROHP, BigDecimal.ZERO);
        temp.put(ReserveField.DOTRSV, BigDecimal.ZERO);
        temp.put(ReserveField.RETRSV, BigDecimal.ZERO);
        temp.put(ReserveField.DOTOUTB, BigDecimal.ZERO);
        temp.put(ReserveField.NEED, BigDecimal.ZERO);

        return new InitialValueWrapper(temp);
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

    public Map<ReserveField, BigDecimal> getValues() {
        return new HashMap<>(values);
    }

    public BigDecimal get(ReserveField fieldName) {
        return values.getOrDefault(fieldName, BigDecimal.ZERO);
    }

}