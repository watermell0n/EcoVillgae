package com.b201.ecovillage.identity.application;

public record VerifiedTeacherIdentity(
    String externalSubject,
    String providerDisplayName
) {
}
