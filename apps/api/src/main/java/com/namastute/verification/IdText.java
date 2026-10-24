package com.namastute.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reads suggestions out of the text OCR found on an ID (Learning Notes 82): the gender printed on it, the last 4
// characters of the document number, and warnings for the reviewer. Pure functions: easy to test with sample text.
final class IdText {

    record Reading(String gender, String last4, List<String> notes) {}

    // FEMALE first: "MALE" is inside "FEMALE". Hindi: महिला / स्त्री (female), पुरुष (male).
    private static final Pattern FEMALE = Pattern.compile("FEMALE|महिला|स्त्री");
    private static final Pattern MALE = Pattern.compile("\\bMALE\\b|पुरुष");
    private static final Pattern OTHER = Pattern.compile("TRANSGENDER|THIRD GENDER|ट्रांसजेंडर");
    // "Sex / लिंग F" on passports: anything but Latin letters and digits may sit between the label and the letter
    private static final Pattern SEX_LETTER = Pattern.compile("\\b(?:SEX|GENDER)\\b[^A-Z0-9]{0,20}?([MFT])\\b");

    // The document number's shape per ID type; group 1 holds its last 4 characters
    // At least 4 masking characters, then a few stray ones, then the 4 digits. OCR often blurs the mask: Tesseract read a
    // clean "XXXX XXXX 4821" as "XXXXK 4821", so the mask's exact shape can't be required.
    private static final Pattern AADHAAR_MASKED = Pattern.compile("(?:[X×*]\\s?){4,}[^\\d\\n]{0,4}(\\d{4})\\b");
    private static final Pattern AADHAAR_FULL = Pattern.compile("\\b\\d{4}\\s\\d{4}\\s\\d{4}\\b");
    private static final Pattern PASSPORT = Pattern.compile("\\b[A-Z]\\d{3}(\\d{4})\\b");                 // A1234567
    private static final Pattern VOTER_ID = Pattern.compile("\\b[A-Z]{3}\\d{3}(\\d{4})\\b");             // ABC1234567
    private static final Pattern LICENCE = Pattern.compile("\\b[A-Z]{2}[- ]?\\d{2}[- ]?\\d{4}[- ]?\\d{3}(\\d{4})\\b"); // DL-14 2011 0012345

    private IdText() {}

    static Reading read(String docType, String ocrText) {
        String text = ocrText.toUpperCase(Locale.ROOT);
        List<String> notes = new ArrayList<>();
        String gender = gender(text);
        if (gender == null) {
            notes.add("Gender not readable: check the ID");
        }
        String last4 = switch (docType) {
            case "masked_aadhaar" -> first(AADHAAR_MASKED, text);
            case "passport" -> first(PASSPORT, text);
            case "voter_id" -> first(VOTER_ID, text);
            case "driving_licence" -> first(LICENCE, text);
            default -> null;
        };
        if (last4 == null) {
            notes.add("Document number not readable: check the ID");
        }
        if (AADHAAR_FULL.matcher(text).find()) {
            notes.add("Possibly a full (unmasked) Aadhaar number: reject if the first 8 digits are visible");
        }
        return new Reading(gender, last4, notes);
    }

    private static String gender(String text) {
        if (OTHER.matcher(text).find()) return "other";
        if (FEMALE.matcher(text).find()) return "female";
        if (MALE.matcher(text).find()) return "male";
        Matcher letter = SEX_LETTER.matcher(text);   // passports print "Sex F"
        if (letter.find()) {
            return switch (letter.group(1)) {
                case "F" -> "female";
                case "M" -> "male";
                default -> "other";
            };
        }
        return null;
    }

    private static String first(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
