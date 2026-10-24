package com.namastute.verification;

import java.nio.file.Path;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.objdetect.FaceRecognizerSF;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Face helper for ID checks (Learning Notes 82): YuNet finds faces, SFace turns a face into 128 numbers, and the cosine
// similarity of two faces says how alike they are. A helper only: a person makes every decision.
// Models load on first use; one call at a time (OpenCV's networks aren't safe to share between threads).
@Component
class FaceMatch {

    // OpenCV's published "same person" threshold for SFace on LFW. Not calibrated on Indian ID photos yet.
    static final double SAME_PERSON = 0.363;
    static final double MAX_SIDE = 640;   // pixels, before looking for faces

    // score: null when either photo has no face
    record Result(boolean idFace, boolean selfieFace, Double score) {}

    private final Path models;
    private FaceDetectorYN detector;
    private FaceRecognizerSF recognizer;

    FaceMatch(@Value("${namastute.verification.models-dir}") String modelsDir) {
        this.models = Path.of(modelsDir);
    }

    synchronized Result compare(byte[] idJpeg, byte[] selfieJpeg) {
        load();
        Mat id = face(idJpeg), selfie = face(selfieJpeg);
        try {
            Double score = id != null && selfie != null ? recognizer.match(id, selfie, FaceRecognizerSF.FR_COSINE) : null;
            return new Result(id != null, selfie != null, score);
        } finally {
            if (id != null) id.release();
            if (selfie != null) selfie.release();
        }
    }

    // The largest face's 128 numbers (an ID can show a second, small "ghost" photo), or null if there's no face
    private Mat face(byte[] jpeg) {
        MatOfByte bytes = new MatOfByte(jpeg);
        Mat image = Imgcodecs.imdecode(bytes, Imgcodecs.IMREAD_COLOR);
        Mat faces = new Mat(), aligned = new Mat(), feature = new Mat();
        try {
            // YuNet is tuned for faces up to a few hundred pixels: in a large close-up (a selfie) it found no face at all
            // until the photo was shrunk. ID photos' small faces stay big enough at this size.
            double scale = MAX_SIDE / Math.max(image.cols(), image.rows());
            if (scale < 1) {
                Imgproc.resize(image, image, new Size(image.cols() * scale, image.rows() * scale), 0, 0, Imgproc.INTER_AREA);
            }
            detector.setInputSize(new Size(image.cols(), image.rows()));
            detector.detect(image, faces);   // one row per face: box x, y, w, h, then 5 points, then a confidence
            int best = -1;
            double bestArea = 0;
            for (int i = 0; i < faces.rows(); i++) {
                double area = faces.get(i, 2)[0] * faces.get(i, 3)[0];
                if (area > bestArea) {
                    bestArea = area;
                    best = i;
                }
            }
            if (best < 0) {
                return null;
            }
            recognizer.alignCrop(image, faces.row(best), aligned);   // eyes level, cropped like the model's training faces
            recognizer.feature(aligned, feature);
            return feature.clone();
        } finally {
            bytes.release();
            image.release();
            faces.release();
            aligned.release();
            feature.release();
        }
    }

    private void load() {
        if (detector != null) {
            return;
        }
        OpenCV.loadLocally();   // unpacks OpenCV's compiled library for this computer from the jar
        detector = FaceDetectorYN.create(models.resolve("face_detection_yunet_2023mar.onnx").toString(), "", new Size(320, 320),
                0.8f, 0.3f, 50);   // 0.8: keep faces it's at least 80% sure of (the model's default is 0.9; ID photos are small)
        recognizer = FaceRecognizerSF.create(models.resolve("face_recognition_sface_2021dec.onnx").toString(), "");
    }
}
