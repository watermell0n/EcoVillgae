package com.b201.ecovillage.identity.application;

public interface TeacherIdentityVerifier {

    VerifiedTeacherIdentity verify(String idToken);
}
