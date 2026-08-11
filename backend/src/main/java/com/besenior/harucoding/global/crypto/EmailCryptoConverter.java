package com.besenior.harucoding.global.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Converter
@RequiredArgsConstructor
public class EmailCryptoConverter implements AttributeConverter<String, String> {

    private final AesGcmEncryptor encryptor;

    @Override
    public String convertToDatabaseColumn(String plainEmail) {
        if (plainEmail == null) return null;
        return encryptor.encrypt(plainEmail);
    }

    @Override
    public String convertToEntityAttribute(String encryptedEmail) {
        if (encryptedEmail == null) return null;
        return encryptor.decrypt(encryptedEmail);
    }
}
