package tn.bricocomptoir.media.application.port.out;

public interface ImageProcessor {
    Processed process(byte[] bytes, String declaredType);
    record Processed(String sourceMime, int width, int height, byte[] card, byte[] detail) { }
}
