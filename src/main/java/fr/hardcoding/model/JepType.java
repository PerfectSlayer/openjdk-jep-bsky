package fr.hardcoding.model;

public enum JepType {
    PROCESS("P"),
    INFORMATIONAL("I"),
    FEATURE("F"),
    INFRASTRUCTURE("S");

    private final String shortName;

    JepType(String shortName) {
        this.shortName = shortName;
    }

    public static JepType fromShortName(String shortName) {
        for (JepType jepType : JepType.values()) {
            if (jepType.shortName.equals(shortName)) {
                return jepType;
            }
        }
        throw new IllegalArgumentException("Unknown JepType short name: " + shortName);
    }

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
