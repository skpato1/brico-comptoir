package tn.bricocomptoir.notifications.adapter.out.persistence;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PayloadCipher {
    private final SecretKeySpec key;
    private final SecureRandom random=new SecureRandom();
    public PayloadCipher(@Value("${brico.mail.outbox-key}") String encoded) {
        byte[] bytes=Base64.getDecoder().decode(encoded);
        if (bytes.length!=32) throw new IllegalArgumentException("MAIL_OUTBOX_KEY must contain 32 bytes");
        key=new SecretKeySpec(bytes,"AES");
    }
    public byte[] encrypt(String id, String json) {
        try {
            byte[] iv=new byte[12]; random.nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
            cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(12+encrypted.length).put(iv).put(encrypted).array();
        } catch (Exception e) { throw new IllegalStateException("OUTBOX_ENCRYPTION_FAILED",e); }
    }
    public String decrypt(String id, byte[] bytes) {
        try {
            ByteBuffer buffer=ByteBuffer.wrap(bytes); byte[] iv=new byte[12];buffer.get(iv);
            byte[] encrypted=new byte[buffer.remaining()];buffer.get(encrypted);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));
            cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("OUTBOX_DECRYPTION_FAILED",e); }
    }
}
