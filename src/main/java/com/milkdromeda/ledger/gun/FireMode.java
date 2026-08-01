package com.milkdromeda.ledger.gun;

public enum FireMode {

    /** One shot per click. */
    SEMI("Semi-Auto"),
    /** Held trigger, continuous fire. */
    AUTO("Full-Auto"),
    /** A short burst of rounds per click. */
    BURST("Burst"),
    /** Hold to charge, release to fire one big round. */
    CHARGE("Charge"),
    /** Held trigger, but it has to spin up first. */
    SPINUP("Spin-Up");

    private final String displayName;

    FireMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** True when the gun keeps firing while the trigger is held down. */
    public boolean isSustained() {
        return this == AUTO || this == SPINUP;
    }
}
