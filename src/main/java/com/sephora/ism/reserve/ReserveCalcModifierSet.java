
package com.sephora.ism.reserve;

import java.util.HashMap;
import java.util.Map;

public class ReserveCalcModifierSet {
    private final Map<String, Object> modifiers;

    public ReserveCalcModifierSet() {
        this.modifiers = new HashMap<>();
    }

    public void addModifier(String key, Object value) {
        modifiers.put(key, value);
    }

    public Object getModifier(String key) {
        return modifiers.get(key);
    }

    public boolean hasModifier(String key) {
        return modifiers.containsKey(key);
    }
}