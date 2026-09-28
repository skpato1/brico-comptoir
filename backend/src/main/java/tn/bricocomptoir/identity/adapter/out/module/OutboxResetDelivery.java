package tn.bricocomptoir.identity.adapter.out.module;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.identity.application.port.out.ResetDelivery;
import tn.bricocomptoir.notifications.application.port.in.NotificationOperations;

@Component
public class OutboxResetDelivery implements ResetDelivery {
    private final NotificationOperations notifications;
    private final String publicUrl;
    public OutboxResetDelivery(NotificationOperations notifications,@Value("${brico.public-url}") String publicUrl) {
        this.notifications=notifications;this.publicUrl=publicUrl.replaceAll("/+$","");
    }
    @Override public void send(String email,String token,Instant expiry) {
        notifications.enqueue(new NotificationOperations.Email("RESET:"+hash(token),email,"BricoComptoir — réinitialisation du mot de passe",
            "Ouvrez ce lien avant "+expiry+" : "+publicUrl+"/compte#reset="+token
                +"\nSi vous n'avez rien demandé, ignorez ce message.\n",hash(email),expiry));
    }
    @Override public void cancel(String email) { notifications.cancelReset(hash(email)); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
