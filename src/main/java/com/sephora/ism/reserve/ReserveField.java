package com.sephora.ism.reserve;

/**
 * Enum for all reserve calculation fields.
 * The @ symbol in field names is represented by different enum categories.
 */
public enum ReserveField {

    // ===== Key Fields =====
    DIV("DIV", FieldCategory.KEY, "Division Number"),
    LOC("LOC", FieldCategory.KEY, "Location Number"),
    SKU("SKU", FieldCategory.KEY, "SKU Number"),
    CALLFOR("CALLFOR", FieldCategory.KEY, "Record Type (OMS/FRM/JEI)"),

    // ===== Base SKULOC Fields (Input) =====
    BYCL("BYCL", FieldCategory.SKULOC, "Buyer Class"),
    SKUSTS("SKUSTS", FieldCategory.SKULOC, "SKULOC Status"),
    ONHAND("ONHAND", FieldCategory.SKULOC, "On-Hand Units"),
    ROHM("ROHM", FieldCategory.SKULOC, "On-Hand Merchandise Reserve"),
    LOST("LOST", FieldCategory.SKULOC, "Lost/Found Units"),
    DMG("DMG", FieldCategory.SKULOC, "Damage Units - TO BE REMOVED"),
    OOBADJ("OOBADJ", FieldCategory.SKULOC, "OOB Adjustment"),

    // ===== Initial Calculations =====
    INITAFS("INITAFS", FieldCategory.CALCULATED, "Initial Available For Sale"),

    // ===== Commitment Fields =====
    SNB("SNB", FieldCategory.SKULOC, "Shipped Not Billed"),
    SNBX("SNBX", FieldCategory.CONSTRAINT, "SNB Constraint"),
    DTCO("DTCO", FieldCategory.SKULOC, "Dotcom Open Customer Orders"),
    DTCOX("DTCOX", FieldCategory.CONSTRAINT, "DTCO Constraint"),
    ROHP("ROHP", FieldCategory.SKULOC, "Retail On-Hand Pick"),
    ROHPX("ROHPX", FieldCategory.CONSTRAINT, "ROHP Constraint"),

    // ===== Uncommitted AFS =====
    UNCOMAFS("UNCOMAFS", FieldCategory.CALCULATED, "Uncommitted Available For Sale"),

    // ===== Hard Reserve Fields =====
    DOTHRY("DOTHRY", FieldCategory.SKULOC, "Dotcom Hard Reserve ATS Yes"),
    DOTHRYX("DOTHRYX", FieldCategory.CONSTRAINT, "DOTHRY Constraint"),
    DOTHRN("DOTHRN", FieldCategory.SKULOC, "Dotcom Hard Reserve ATS No"),
    DOTHRNX("DOTHRNX", FieldCategory.CONSTRAINT, "DOTHRN Constraint"),
    RETHRY("RETHRY", FieldCategory.SKULOC, "Retail Hard Reserve ATS Yes"),
    RETHRYX("RETHRYX", FieldCategory.CONSTRAINT, "RETHRY Constraint"),
    RETHRN("RETHRN", FieldCategory.SKULOC, "Retail Hard Reserve ATS No"),
    RETHRNX("RETHRNX", FieldCategory.CONSTRAINT, "RETHRN Constraint"),
    HLDHR("HLDHR", FieldCategory.SKULOC, "Held Hard Reserve"),
    HLDHRX("HLDHRX", FieldCategory.CONSTRAINT, "HLDHR Constraint"),

    // ===== Soft Reserve Fields =====
    DOTRSV("DOTRSV", FieldCategory.SKULOC, "Dotcom Reserve"),
    DOTRSVX("DOTRSVX", FieldCategory.CONSTRAINT, "DOTRSV Constraint"),
    RETRSV("RETRSV", FieldCategory.SKULOC, "Retail Reserve"),
    RETRSVX("RETRSVX", FieldCategory.CONSTRAINT, "RETRSV Constraint"),

    // ===== Outbound Fields =====
    DOTOUTB("DOTOUTB", FieldCategory.SKULOC, "Dotcom Outbound"),
    AOUTBV("AOUTBV", FieldCategory.CALCULATED, "Adjusted Outbound Variance"),
    AOUTBVX("AOUTBVX", FieldCategory.CONSTRAINT, "AOUTBV Constraint"),

    // ===== Need Fields =====
    NEED("NEED", FieldCategory.SKULOC, "Retail Need"),
    ANEED("ANEED", FieldCategory.CALCULATED, "Adjusted Need"),
    NEEDX("NEEDX", FieldCategory.CONSTRAINT, "ANEED Constraint"),

    // ===== Summary/Aggregate Fields =====
    RETAILATS("@RETAILATS", FieldCategory.AGGREGATE, "Retail Available To Sell"),
    DOTATS("@DOTATS", FieldCategory.AGGREGATE, "Dotcom Available To Sell"),
    UNCOMMIT("@UNCOMMIT", FieldCategory.AGGREGATE, "Uncommitted Inventory"),
    COMMITTED("@COMMITTED", FieldCategory.AGGREGATE, "Committed Inventory"),
    UNCOMMHR("@UNCOMMHR", FieldCategory.AGGREGATE, "Uncommitted Hard Reserve"),

    // ===== Final Output Fields =====
    OMSSUP("@OMSSUP", FieldCategory.OUTPUT, "OMS Supply"),
    RETFINAL("@RETFINAL", FieldCategory.OUTPUT, "Retail Final"),
    OMSFINAL("@OMSFINAL", FieldCategory.OUTPUT, "OMS Final"),

    // ===== Actual Value Fields =====
    SNBA("@SNBA", FieldCategory.ACTUAL, "SNB Actual"),
    DTCOA("@DTCOA", FieldCategory.ACTUAL, "DTCO Actual"),
    ROHPA("@ROHPA", FieldCategory.ACTUAL, "ROHP Actual"),
    DOTHRYA("@DOTHRYA", FieldCategory.ACTUAL, "DOTHRY Actual"),
    DOTHRNA("@DOTHRNA", FieldCategory.ACTUAL, "DOTHRN Actual"),
    RETHRYA("@RETHRYA", FieldCategory.ACTUAL, "RETHRY Actual"),
    RETHRNA("@RETHRNA", FieldCategory.ACTUAL, "RETHRN Actual"),
    HLDHRA("@HLDHRA", FieldCategory.ACTUAL, "HLDHR Actual"),
    DOTRSVA("@DOTRSVA", FieldCategory.ACTUAL, "DOTRSV Actual"),
    RETRSVA("@RETRSVA", FieldCategory.ACTUAL, "RETRSV Actual"),
    AOUTBVA("@AOUTBVA", FieldCategory.ACTUAL, "AOUTBV Actual"),
    NEEDA("@NEEDA", FieldCategory.ACTUAL, "NEED Actual"),

    // ===== System Fields =====
    LMTS("LMTS", FieldCategory.SYSTEM, "Last Modified Timestamp"),
    USER("USER", FieldCategory.SYSTEM, "User"),


    RUNNING_AFS("RUNNING_AFS", FieldCategory.SYSTEM, "Running UnCommited AFS");


    private final String fieldName;
    private final FieldCategory category;
    private final String description;

    ReserveField(String fieldName, FieldCategory category, String description) {
        this.fieldName = fieldName;
        this.category = category;
        this.description = description;
    }

    public String getFieldName() {
        return fieldName;
    }

    public FieldCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this is an aggregate field (starts with @)
     */
    public boolean isAggregate() {
        return fieldName.startsWith("@");
    }

    /**
     * Check if this is a constraint field (ends with X)
     */
    public boolean isConstraint() {
        return category == FieldCategory.CONSTRAINT;
    }

    /**
     * Get the base field for a constraint field
     * e.g., SNBX -> SNB
     */
    public ReserveField getBaseField() {
        if (!isConstraint()) {
            return this;
        }
        String baseName = name().substring(0, name().length() - 1);
        try {
            return ReserveField.valueOf(baseName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get the constraint field for a base field
     * e.g., SNB -> SNBX
     */
    public ReserveField getConstraintField() {
        if (isConstraint()) {
            return this;
        }
        try {
            return ReserveField.valueOf(name() + "X");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Lookup by field name (handles @ prefix)
     */
    public static ReserveField fromFieldName(String fieldName) {
        for (ReserveField field : values()) {
            if (field.fieldName.equals(fieldName)) {
                return field;
            }
        }
        throw new IllegalArgumentException("Unknown field: " + fieldName);
    }

    /**
     * Category of fields for better organization
     */
    public enum FieldCategory {
        KEY("Key Fields"),
        SKULOC("SKULOC Input Fields"),
        CALCULATED("Calculated Fields"),
        CONSTRAINT("Constraint Fields"),
        AGGREGATE("Aggregate Fields"),
        OUTPUT("Output Fields"),
        ACTUAL("Actual Value Fields"),
        SYSTEM("System Fields");

        private final String description;

        FieldCategory(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static String flowResultKey(CalculationFlow flow, ReserveField field) {
        return "_flowResult_" + flow.name() + "_" + field.name();
    }
}