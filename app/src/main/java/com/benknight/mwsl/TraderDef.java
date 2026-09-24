package com.benknight.mwsl;

/* compiled from: Models.java */
class TraderDef {
    static final String ACTIVE = "ACTIVE";
    static final String RESEARCH = "RESEARCH";
    static final String VALIDATION = "VALIDATION";
    final String id;
    final String name;
    final boolean paperEnabled;
    final String purpose;
    final String role;
    final String wallet;

    TraderDef(String id, String name, String wallet, String purpose, String role, boolean paperEnabled) {
        this.id = id;
        this.name = name;
        this.wallet = wallet;
        this.purpose = purpose;
        this.role = role;
        this.paperEnabled = paperEnabled;
    }

    boolean activeSlot() {
        return ACTIVE.equals(this.role);
    }

    boolean researchOnly() {
        return RESEARCH.equals(this.role);
    }
}
