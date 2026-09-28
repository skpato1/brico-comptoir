package tn.bricocomptoir.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import tn.bricocomptoir.media.adapter.out.image.JavaImageProcessor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaImageProcessorTest {
    @Test void detectsActualFormatAndProducesBoundedJpegRenditions() throws Exception {
        var source = new BufferedImage(1600, 800, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        var processor = new JavaImageProcessor();
        var output = processor.process(bytes.toByteArray(), "image/png");
        assertThat(output.sourceMime()).isEqualTo("image/png");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(output.card())).getWidth()).isEqualTo(360);
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(output.detail())).getWidth()).isEqualTo(1200);
        assertThat(output.card().length).isPositive();
        assertThatThrownBy(() -> processor.process(bytes.toByteArray(), "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> processor.process("<svg></svg>".getBytes(), "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
