// src/main/java/com/hackathon/hr/service/S3Service.java
package com.hackathon.hr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.UUID;

@Service
public class S3Service {
    
    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);
    
    private final S3Client s3Client;
    
    // Read from environment variable first, then fall back to property
    @Value("${S3_BUCKET_NAME:${aws.s3.bucket-name:hr-hiring-resumes-js}}")
    private String bucketName;
    
    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }
    
    @PostConstruct
    public void init() {
        logger.info("S3Service initialized with bucket: {}", bucketName);
        if (bucketName == null || bucketName.isEmpty()) {
            throw new IllegalStateException("S3 bucket name is not configured");
        }
    }
    
    public String uploadFile(MultipartFile file) throws IOException {
        String key = "resumes/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();
                    
            s3Client.putObject(request,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
                    
            logger.info("File uploaded successfully to bucket {} with key: {}", bucketName, key);
            return key;
            
        } catch (Exception e) {
            logger.error("Error uploading file to S3 bucket: {}", bucketName, e);
            throw new RuntimeException("Failed to upload file to bucket: " + bucketName, e);
        }
    }
}