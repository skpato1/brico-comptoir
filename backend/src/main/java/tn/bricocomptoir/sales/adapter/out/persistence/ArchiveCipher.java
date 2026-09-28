package tn.bricocomptoir.sales.adapter.out.persistence;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ArchiveCipher {
    private final SecretKeySpec key;
    public ArchiveCipher(@Value("${brico.privacy.archive-key}") String encoded) {
        byte[] raw=Base64.getDecoder().decode(encoded);
        if(raw.length!=32)throw new IllegalArgumentException("DATA_ARCHIVE_KEY must contain 32 bytes");
        key=new SecretKeySpec(raw,"AES");
    }
    public byte[] encrypt(String id,String value) {
        try {
            byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
            cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            var raw=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length+raw.length).put(iv).put(raw).array();
        } catch(Exception failure) { throw new IllegalStateException("ARCHIVE_ENCRYPTION_FAILED"); }
    }
    public String decrypt(String id,byte[] payload) {
        try {
            var buffer=ByteBuffer.wrap(payload);byte[] iv=new byte[12];buffer.get(iv);
            byte[] encrypted=new byte[buffer.remaining()];buffer.get(encrypted);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));
            cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        } catch(Exception failure) { throw new IllegalStateException("ARCHIVE_UNREADABLE"); }
    }
}
