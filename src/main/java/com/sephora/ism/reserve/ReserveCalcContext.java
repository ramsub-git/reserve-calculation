package com.sephora.ism.reserve;

import java.util.Map;
import java.util.LinkedHashMap;
import java.math.BigDecimal;

public class ReserveCalcContext {
    private final Map<String, BigDecimal> fieldValues = new LinkedHashMap<>();

    public void put(String field, BigDecimal value) {
        fieldValues.put(field, value);
    }

    public BigDecimal get(String field) {
        return fieldValues.getOrDefault(field, BigDecimal.ZERO);
    }

    public Map<String, BigDecimal> getAll() {
        return fieldValues;
    }
}