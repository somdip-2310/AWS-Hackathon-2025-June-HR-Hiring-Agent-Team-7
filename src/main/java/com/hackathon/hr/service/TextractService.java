// src/main/java/com/hackathon/hr/service/TextractService.java
package com.hackathon.hr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

@Service
public class TextractService {
    
    private static final Logger logger = LoggerFactory.getLogger(TextractService.class);
    
    private final TextractClient textractClient;
    
    // Read from environment variable first, then fall back to property
    @Value("${S3_BUCKET_NAME:${aws.s3.bucket-name:hr-hiring-resumes-js}}")
    private String bucketName;
    
    public TextractService(TextractClient textractClient) {
        this.textractClient = textractClient;
    }
    
    @PostConstruct
    public void init() {
        logger.info("TextractService initialized with bucket: {}", bucketName);
        if (bucketName == null || bucketName.isEmpty()) {
            throw new IllegalStateException("S3 bucket name is not configured for Textract");
        }
    }
    
    public String extractText(String s3Key) {
        try {
            logger.info("Extracting text from s3://{}/{}", bucketName, s3Key);
            
            DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
                    .document(Document.builder()
                            .s3Object(S3Object.builder()
                                    .bucket(bucketName)
                                    .name(s3Key)
                                    .build())
                            .build())
                    .build();
                    
            DetectDocumentTextResponse response = textractClient.detectDocumentText(request);
            
            String extractedText = response.blocks().stream()
                    .filter(block -> block.blockType() == BlockType.LINE)
                    .map(Block::text)
                    .collect(Collectors.joining("\n"));
                    
            logger.info("Text extracted successfully from: {} (extracted {} lines)", 
                s3Key, 
                response.blocks().stream().filter(block -> block.blockType() == BlockType.LINE).count());
                
            return extractedText;
            
        } catch (Exception e) {
            logger.error("Error extracting text from document s3://{}/{}", bucketName, s3Key, e);
            throw new RuntimeException("Failed to extract text from document: " + s3Key, e);
        }
    }
}