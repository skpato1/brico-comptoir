package tn.bricocomptoir.identity.application.port.out;

public interface ResetDelivery {
    void send(String email, String token, java.time.Instant expiresAt);
    void cancel(String email);
}
