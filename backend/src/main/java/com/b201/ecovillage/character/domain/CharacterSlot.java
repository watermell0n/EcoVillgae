package com.b201.ecovillage.character.domain;

import java.util.Locale;

public enum CharacterSlot {
    BACKGROUND,
    EFFECT,
    BACK,
    BOTTOM,
    SHOES,
    TOP,
    NECK,
    FACE,
    HAND,
    HAT;

    public static CharacterSlot fromCharacterCode(String characterCode) {
        if (characterCode == null || characterCode.isBlank()) {
            throw new IllegalArgumentException("characterCode must not be blank");
        }
        int separator = characterCode.indexOf('_');
        String prefix = separator < 0 ? characterCode : characterCode.substring(0, separator);
        if ("BG".equalsIgnoreCase(prefix)) {
            return BACKGROUND;
        }
        try {
            return valueOf(prefix.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "characterCode must start with a supported slot: " + characterCode,
                exception
            );
        }
    }
}
