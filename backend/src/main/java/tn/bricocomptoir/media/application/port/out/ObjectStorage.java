package tn.bricocomptoir.media.application.port.out;

public interface ObjectStorage {
    void put(String key, byte[] bytes, String contentType);
    byte[] get(String key);
    void delete(String key);
}
