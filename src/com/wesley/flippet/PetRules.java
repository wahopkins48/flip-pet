package com.wesley.flippet;

/** Pure time simulation: reading often or after a day produces the same result. */
final class PetRules {
    static final double GRACE_SECONDS = 15 * 60;
    static final double[] AWAKE = {14, 10, 8, 9};
    static final double[] ASLEEP = {7, 3, -13, 5};

    static final class Result {
        final double[] meters;
        final double neglect;
        final boolean dead;
        final int cause;
        final double advanced;
        Result(double[] meters, double neglect, boolean dead, int cause, double advanced) {
            this.meters = meters;
            this.neglect = neglect;
            this.dead = dead;
            this.cause = cause;
            this.advanced = advanced;
        }
    }

    static Result advance(double[] meters, boolean asleep, boolean sick,
                          boolean survival, double neglect, double seconds) {
        double[] rates = (asleep ? ASLEEP : AWAKE).clone();
        double firstEmpty = Double.POSITIVE_INFINITY;
        int cause = -1;
        for (int i = 0; i < rates.length; i++) {
            if (sick && i >= 2 && rates[i] > 0) rates[i] *= 2;
            if (!survival) rates[i] *= 0.25;
            // A zero meter which is recovering (sleeping vitality) is not neglected.
            if (rates[i] > 0) {
                double emptyAt = Math.max(0, meters[i]) / rates[i] * 3600;
                if (emptyAt < firstEmpty) { firstEmpty = emptyAt; cause = i; }
            }
        }
        double carried = firstEmpty == 0 ? Math.max(0, neglect) : 0;
        double deathAt = firstEmpty + Math.max(0, GRACE_SECONDS - carried);
        boolean dead = survival && seconds >= deathAt;
        double advanced = Math.max(0, dead ? deathAt : seconds);
        double[] result = meters.clone();
        for (int i = 0; i < result.length; i++) {
            result[i] = Math.max(0, Math.min(100, result[i] - rates[i] * advanced / 3600));
        }
        double emptySeconds = survival && advanced >= firstEmpty
            ? carried + advanced - firstEmpty : 0;
        return new Result(result, emptySeconds, dead, cause, advanced);
    }

    private PetRules() {}
}
