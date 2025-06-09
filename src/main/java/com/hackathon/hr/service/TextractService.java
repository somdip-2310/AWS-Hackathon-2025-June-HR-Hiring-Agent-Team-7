// src/main/java/com/hackathon/hr/service/TextractService.java
package com.hackathon.hr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.util.stream.Collectors;

@Service
public class TextractService {

    private static final Logger logger = LoggerFactory.getLogger(TextractService.class);

    private final TextractClient textractClient;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public TextractService(TextractClient textractClient) {
        this.textractClient = textractClient;
    }

    public String extractText(String s3Key) {
        try {
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

            logger.info("Text extracted successfully from: {}", s3Key);
            return extractedText;

        } catch (Exception e) {
            logger.error("Error extracting text from document", e);
            throw new RuntimeException("Failed to extract text", e);
        }
    }
}