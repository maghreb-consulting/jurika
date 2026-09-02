package fr.maghreb.gje.exceptions;

import lombok.Getter;

@Getter
public class QuotaExceededException extends RuntimeException {
    private final String type;
    private final Number used;
    private final Number limit;

    public QuotaExceededException(String type, Number used, Number limit) {
        super("Quota dépassé pour : " + type);
        this.type = type;
        this.used = used;
        this.limit = limit;
    }
}
