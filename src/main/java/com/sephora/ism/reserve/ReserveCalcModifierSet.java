package com.sephora.ism.reserve;

import java.util.HashMap;
import java.util.Map;

public class ReserveCalcModifierSet {
    private final String modifierSetName;
    private final Map<String, Object> modifiers;

    public ReserveCalcModifierSet(String modifierSetName) {
        this.modifierSetName = modifierSetName;
        this.modifiers = new HashMap<>();
    }

    public void addModifier(String key, Object value) {
        modifiers.put(key, value);
    }

    public Object getModifier(String key) {
        return modifiers.get(key);
    }

    public String getModifierSetName() {
        return modifierSetName;
    }
}