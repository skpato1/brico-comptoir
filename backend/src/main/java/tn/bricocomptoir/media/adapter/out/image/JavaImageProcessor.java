package tn.bricocomptoir.media.adapter.out.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;
import tn.bricocomptoir.media.application.port.out.ImageProcessor;
import tn.bricocomptoir.media.domain.ImageRules;

@Component
public class JavaImageProcessor implements ImageProcessor {
    @Override public Processed process(byte[] bytes, String declaredType) {
        if (bytes == null || bytes.length < 1 || bytes.length > ImageRules.MAX_BYTES)
            throw new IllegalArgumentException("Image size must be at most 6 MiB");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Unrecognized image data");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                String detected = switch (format) { case "jpeg", "jpg" -> "image/jpeg";
                    case "png" -> "image/png"; default -> "unsupported"; };
                int width = reader.getWidth(0), height = reader.getHeight(0);
                ImageRules.source(bytes.length, width, height, declaredType, detected);
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw new IllegalArgumentException("Invalid image data");
                return new Processed(detected, width, height, rendition(decoded, 360, .76f),
                        rendition(decoded, 1200, .84f));
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException invalid) {
            if (invalid instanceof IllegalArgumentException argument) throw argument;
            throw new IllegalArgumentException("Invalid image data", invalid);
        }
    }

    private static byte[] rendition(BufferedImage source, int maxWidth, float quality) throws IOException {
        int width = Math.min(source.getWidth(), maxWidth);
        int height = Math.max(1, (int) Math.round((double) source.getHeight() * width / source.getWidth()));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var buffer = new ByteArrayOutputStream(); var output = new MemoryCacheImageOutputStream(buffer)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            writer.write(null, new javax.imageio.IIOImage(target, null, null), params);
            output.flush();
            return buffer.toByteArray();
        } finally { writer.dispose(); }
    }
}
