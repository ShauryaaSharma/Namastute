package com.namastute.verification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// The suggestions read from an ID's text (Learning Notes 82), on sample OCR output. Made-up numbers only.
class IdTextTest {

    @Test
    void maskedAadhaarInHindiAndEnglish() {
        IdText.Reading r = IdText.read("masked_aadhaar", """
                भारत सरकार  Government of India
                नेहा शर्मा  Neha Sharma
                जन्म तिथि/DOB: 01/01/1999
                महिला / FEMALE
                XXXX XXXX 4821
                """);
        assertThat(r.gender()).isEqualTo("female");
        assertThat(r.last4()).isEqualTo("4821");
        assertThat(r.notes()).isEmpty();
    }

    @Test
    void aBlurredMaskStillCounts() {
        assertThat(IdText.read("masked_aadhaar", "FEMALE >>» XXXXK 4821").last4()).as("a real Tesseract misread").isEqualTo("4821");
        assertThat(IdText.read("masked_aadhaar", "FEMALE Mobile 98100 4821").last4()).as("no mask, no number").isNull();
    }

    @Test
    void femaleIsNotReadAsMale() {
        assertThat(IdText.read("voter_id", "Sex: FEMALE  ABC1234567").gender()).isEqualTo("female");
        assertThat(IdText.read("voter_id", "Sex: MALE  ABC1234567").gender()).isEqualTo("male");
        assertThat(IdText.read("voter_id", "पुरुष ABC1234567").gender()).isEqualTo("male");
        assertThat(IdText.read("voter_id", "TRANSGENDER ABC1234567").gender()).isEqualTo("other");
    }

    @Test
    void passportPrintsJustALetter() {
        IdText.Reading r = IdText.read("passport", """
                REPUBLIC OF INDIA  Passport No. Z1234567
                Sex / लिंग  F   Date of Birth 01/01/1999
                """);
        assertThat(r.gender()).isEqualTo("female");
        assertThat(r.last4()).isEqualTo("4567");
    }

    @Test
    void documentNumbersPerType() {
        assertThat(IdText.read("voter_id", "EPIC No. ABC1234567 MALE").last4()).isEqualTo("4567");
        assertThat(IdText.read("driving_licence", "DL No: DL-14 2011 0012345 MALE").last4()).isEqualTo("2345");
        assertThat(IdText.read("driving_licence", "MH1220110012345 FEMALE").last4()).isEqualTo("2345");
        // A voter ID number on a passport form isn't a passport number
        assertThat(IdText.read("passport", "ABC1234567 FEMALE").last4()).isNull();
    }

    @Test
    void anUnmaskedAadhaarIsFlaggedNotTrusted() {
        IdText.Reading r = IdText.read("masked_aadhaar", "FEMALE 2345 6789 4821");
        assertThat(r.last4()).as("only a masked number counts").isNull();
        assertThat(r.notes()).anyMatch(n -> n.startsWith("Possibly a full (unmasked) Aadhaar number"));
    }

    @Test
    void nothingReadableBecomesNotesNotGuesses() {
        IdText.Reading r = IdText.read("passport", "~~ glare ~~");
        assertThat(r.gender()).isNull();
        assertThat(r.last4()).isNull();
        assertThat(r.notes()).containsExactly("Gender not readable: check the ID", "Document number not readable: check the ID");
    }
}
