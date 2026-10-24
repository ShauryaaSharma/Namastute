package com.namastute.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

// The real models on this machine (Learning Notes 82): OpenCV's native library loads, Tesseract reads printed text,
// and a photo with no face is recognised as such. No real person's face or ID is used anywhere in the tests.
class MachineChecksTest {

    static final String MODELS = "target/models";   // downloaded by the build (pom.xml), checksums verified

    @Test
    void tesseractReadsAnIdLikeCard() throws Exception {
        byte[] card = card("GOVERNMENT OF INDIA", "Name: Test Person", "Gender: FEMALE", "XXXX XXXX 4821");
        String text = new Ocr(MODELS).read(card);
        IdText.Reading r = IdText.read("masked_aadhaar", text);
        assertThat(r.gender()).as("read from: " + text).isEqualTo("female");
        assertThat(r.last4()).as("read from: " + text).isEqualTo("4821");
    }

    @Test
    void noFaceIsFoundOnACardWithOnlyText() throws Exception {
        byte[] card = card("GOVERNMENT OF INDIA", "Gender: FEMALE");
        FaceMatch.Result r = new FaceMatch(MODELS).compare(card, card);
        assertThat(r.idFace()).isFalse();
        assertThat(r.selfieFace()).isFalse();
        assertThat(r.score()).isNull();
    }

    // A white card with black printed lines, like a clean photo of an ID's text
    static byte[] card(String... lines) throws Exception {
        // At least 600 px tall: uploads under 480 px are refused (Photos.MIN_SIDE)
        BufferedImage image = new BufferedImage(1200, Math.max(600, 120 + 90 * lines.length), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 48));
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], 60, 110 + 90 * i);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return out.toByteArray();
    }
}
