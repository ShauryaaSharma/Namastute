package com.namastute.verification;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

// ID photos in a private S3 bucket (Learning Notes 78): put, read (for the review, slice 2), delete.
// Development points at SeaweedFS with its dev keys; production at real S3 with no keys (the EC2 IAM role).
@Component
class PhotoStore {

    private final S3Client s3;
    private final String bucket;

    PhotoStore(@Value("${namastute.verification.bucket}") String bucket,
               @Value("${namastute.verification.s3-endpoint}") String endpoint,
               @Value("${namastute.verification.s3-region}") String region,
               @Value("${namastute.verification.s3-access-key}") String accessKey,
               @Value("${namastute.verification.s3-secret-key}") String secretKey) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.create())
                // Checksums only when S3 requires them: S3-compatible stores like SeaweedFS don't all support the newer ones
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);   // http://host:8333/bucket/key
        }
        if (!accessKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        }
        this.s3 = builder.build();
        this.bucket = bucket;
    }

    void put(String key, byte[] jpeg) {
        s3.putObject(b -> b.bucket(bucket).key(key).contentType("image/jpeg"), RequestBody.fromBytes(jpeg));
    }

    byte[] get(String key) {
        return s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
    }

    // Deleting something already gone is fine in S3: this can safely run twice
    void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(key));
    }
}
