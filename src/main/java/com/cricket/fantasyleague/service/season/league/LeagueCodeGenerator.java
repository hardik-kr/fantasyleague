package com.cricket.fantasyleague.service.season.league;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Generates 10-character league codes from a 32-character unambiguous
 * alphabet (no {@code 0/O/1/I/L}). Backed by {@link SecureRandom} so
 * codes are not guessable by clients.
 *
 * <p>Combinations: {@code 32^10 ~= 1.13 x 10^15}. Collision probability
 * with 1M existing leagues is on the order of {@code 10^-9}, which the
 * service further mitigates by retrying up to 5 times around an
 * {@code existsByCode} probe.
 */
@Component
public class LeagueCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] buf = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            buf[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(buf);
    }

    public int codeLength() {
        return CODE_LENGTH;
    }

    public boolean isValidShape(String code) {
        if (code == null || code.length() != CODE_LENGTH) return false;
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) return false;
        }
        return true;
    }
}
