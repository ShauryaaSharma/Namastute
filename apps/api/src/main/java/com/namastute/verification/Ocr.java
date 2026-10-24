package com.namastute.verification;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.ImageIOHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Reads the text on an ID photo with Tesseract, English + Hindi (Learning Notes 82). The text is only used to make
// suggestions (IdText) and is never stored. One call at a time: a Tesseract instance isn't safe to share between threads.
@Component
class Ocr {

    private final Tesseract tesseract = new Tesseract();

    Ocr(@Value("${namastute.verification.models-dir}") String modelsDir) {
        tesseract.setDatapath(modelsDir);   // where eng.traineddata and hin.traineddata are
        tesseract.setLanguage("eng+hin");
    }

    synchronized String read(byte[] jpeg) throws IOException, TesseractException {
        BufferedImage photo = ImageIO.read(new ByteArrayInputStream(jpeg));
        // Raw grey pixels, not doOCR(image): that path goes through Leptonica, and Tess4J's Leptonica binding needs a
        // newer version than Ubuntu 24.04 ships (it fails with "undefined symbol: pixDisplayDiff")
        BufferedImage grey = new BufferedImage(photo.getWidth(), photo.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        grey.getGraphics().drawImage(photo, 0, 0, null);
        return tesseract.doOCR(grey.getWidth(), grey.getHeight(), ImageIOHelper.convertImageData(grey), 8, null, null);
    }
}
