package fr.hardcoding.model;

public enum JepState {
    DRAFTED("Dra"),
    SUBMITTED("Sub"),
    CANDIDATE("Can"),
    PROPOSED_TO_TARGET("Pro"),
    TARGETED("Tar"),
    INTEGRATED("Int"),
    CLOSED_DELIVERED("Clo"),
    COMPLETED("Com"),
    ACTIVE("Act");
    // TODO There might be a PROPOSED_TO_DROP missing status

    private final String shortName;

    JepState(String shortName) {
        this.shortName = shortName;
    }

    public static JepState fromShortName(String shortName) {
        for (JepState status : JepState.values()) {
            if (status.shortName.equals(shortName)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown JepStatus short name: " + shortName);
    }
}
