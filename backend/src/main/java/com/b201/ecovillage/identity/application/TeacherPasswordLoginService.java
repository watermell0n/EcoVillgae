package com.b201.ecovillage.identity.application;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.identity.domain.TeacherCredential;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.TeacherCredentialRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

@Service
public class TeacherPasswordLoginService {

    private final TeacherCredentialRepository credentialRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TeacherLoginService teacherLoginService;

    public TeacherPasswordLoginService(
        TeacherCredentialRepository credentialRepository,
        UserAccountRepository userAccountRepository,
        PasswordEncoder passwordEncoder,
        TeacherLoginService teacherLoginService
    ) {
        this.credentialRepository = credentialRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.teacherLoginService = teacherLoginService;
    }

    @Transactional(readOnly = true)
    public TeacherLoginResult login(String email, String password) {
        TeacherCredential credential = credentialRepository
            .findByEmail(normalizeEmail(email))
            .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        UserAccount teacher = userAccountRepository
            .findById(credential.getTeacherUserId())
            .orElseThrow(InvalidCredentialsException::new);
        return teacherLoginService.createSession(teacher);
    }

    static String normalizeEmail(String value) {
        if (value == null) {
            throw new InvalidCredentialsException();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new InvalidCredentialsException();
        }
        return normalized;
    }
}
