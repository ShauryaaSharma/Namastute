package com.namastute.verification;

import com.namastute.shared.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

// Never trust an upload (Learning Notes 78): it must really be an image of a sensible size, and what we store is a fresh
// JPEG we drew ourselves, so nothing hidden in the original (EXIF with a GPS location, or anything else) survives.
final class Photos {

    static final long MAX_BYTES = 2 * 1024 * 1024;
    static final int MIN_SIDE = 480;   // smaller and a reviewer can't read the ID or see the face
    static final int MAX_SIDE = 6000;  // checked BEFORE decoding: a tiny file can claim 50000×50000 and eat the memory

    private Photos() {}

    static byte[] clean(MultipartFile file, String what) {
        if (file == null || file.isEmpty()) {
            throw bad("PHOTO_MISSING", "Add the " + what + ".");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_BIG", "The " + what + " must be under 2 MB.");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(file.getInputStream())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw notAnImage(what);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);   // true, true: read forwards only, skip the metadata
                int width = reader.getWidth(0), height = reader.getHeight(0);   // from the header: nothing decoded yet
                if (Math.max(width, height) > MAX_SIDE) {
                    throw bad("PHOTO_TOO_LARGE", "The " + what + " is too large. Take a normal photo with your camera.");
                }
                if (Math.min(width, height) < MIN_SIDE) {
                    throw bad("PHOTO_TOO_SMALL", "The " + what + " is too small to check. Take a sharper photo.");
                }
                return jpeg(reader.read(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw notAnImage(what);
        }
    }

    // Draw onto a plain white RGB canvas (transparent PNGs become white, not black), then encode a new JPEG
    private static byte[] jpeg(BufferedImage image) throws IOException {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(image, 0, 0, Color.WHITE, null);
        g.dispose();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.9f);   // high: a reviewer must read small print on the ID
            writer.write(null, new IIOImage(rgb, null, null), params);   // null metadata: nothing copied from the original
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static ApiException notAnImage(String what) {
        return bad("NOT_AN_IMAGE", "The " + what + " isn't a photo we can read. Use a JPEG or PNG from your camera.");
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
