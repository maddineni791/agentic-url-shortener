package com.assessment.agentic.urlshortener;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate(String regionPrefix, int randomLength) {
        String prefix = regionPrefix == null || regionPrefix.isBlank() ? "" : regionPrefix + "-";
        StringBuilder code = new StringBuilder(prefix);
        for (int i = 0; i < randomLength; i++) {
            code.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    public String generate(int length) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
