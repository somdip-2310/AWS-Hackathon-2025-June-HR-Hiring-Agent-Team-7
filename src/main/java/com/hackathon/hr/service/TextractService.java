// src/main/java/com/hackathon/hr/service/TextractService.java
package com.hackathon.hr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hackathon.hr.exception.DocumentProcessingException;
import com.hackathon.hr.exception.UnsupportedDocumentFormatException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import software.amazon.awssdk.services.textract.model.TextractException;
import software.amazon.awssdk.services.textract.model.UnsupportedDocumentException;

@Service
public class TextractService {
    
    private static final Logger logger = LoggerFactory.getLogger(TextractService.class);
    
    private final TextractClient textractClient;
    private final S3Client s3Client;

    public TextractService(TextractClient textractClient, S3Client s3Client) {
        this.textractClient = textractClient;
        this.s3Client = s3Client;
    }
    
    
    // Read from environment variable first, then fall back to property
    @Value("${S3_BUCKET_NAME:${aws.s3.bucket-name:hr-hiring-resumes-js}}")
    private String bucketName;
    
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
            
            // First, check the file size to determine sync vs async processing
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            
            HeadObjectResponse headResponse = s3Client.headObject(headRequest);
            long fileSize = headResponse.contentLength();
            
            logger.info("File size for {}: {} bytes", s3Key, fileSize);
            
            // Use synchronous processing for files under 1MB, async for larger files
            if (fileSize < 1024 * 1024) { // 1MB threshold
                // Original synchronous processing
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
                
            } else {
                // Asynchronous processing for larger files
                logger.info("Using asynchronous text detection for large file: {}", s3Key);
                
                StartDocumentTextDetectionRequest startRequest = StartDocumentTextDetectionRequest.builder()
                        .documentLocation(DocumentLocation.builder()
                                .s3Object(S3Object.builder()
                                        .bucket(bucketName)
                                        .name(s3Key)
                                        .build())
                                .build())
                        .build();
                
                StartDocumentTextDetectionResponse startResponse = textractClient.startDocumentTextDetection(startRequest);
                String jobId = startResponse.jobId();
                
                logger.info("Started Textract job: {} for document: {}", jobId, s3Key);
                
                // Poll for completion
                GetDocumentTextDetectionRequest getRequest = GetDocumentTextDetectionRequest.builder()
                        .jobId(jobId)
                        .build();
                
                GetDocumentTextDetectionResponse response;
                int attempts = 0;
                int maxAttempts = 60; // Max 60 seconds wait
                
                do {
                    Thread.sleep(1000); // Wait 1 second between polls
                    response = textractClient.getDocumentTextDetection(getRequest);
                    attempts++;
                    
                    if (attempts >= maxAttempts) {
                        throw new RuntimeException("Textract job timed out after " + maxAttempts + " seconds");
                    }
                    
                    logger.debug("Textract job {} status: {}", jobId, response.jobStatus());
                    
                } while (response.jobStatus() == JobStatus.IN_PROGRESS);
                
                if (response.jobStatus() == JobStatus.SUCCEEDED) {
                    StringBuilder extractedText = new StringBuilder();
                    String nextToken = null;
                    int totalLines = 0;
                    
                    // Handle pagination for large documents
                    do {
                        GetDocumentTextDetectionRequest paginatedRequest = GetDocumentTextDetectionRequest.builder()
                                .jobId(jobId)
                                .nextToken(nextToken)
                                .build();
                        
                        GetDocumentTextDetectionResponse paginatedResponse = textractClient.getDocumentTextDetection(paginatedRequest);
                        
                        String pageText = paginatedResponse.blocks().stream()
                                .filter(block -> block.blockType() == BlockType.LINE)
                                .map(Block::text)
                                .collect(Collectors.joining("\n"));
                        
                        if (!pageText.isEmpty()) {
                            if (extractedText.length() > 0) {
                                extractedText.append("\n");
                            }
                            extractedText.append(pageText);
                        }
                        
                        totalLines += paginatedResponse.blocks().stream()
                                .filter(block -> block.blockType() == BlockType.LINE)
                                .count();
                        
                        nextToken = paginatedResponse.nextToken();
                        
                    } while (nextToken != null);
                    
                    logger.info("Text extracted successfully from: {} (extracted {} lines using async processing)", 
                        s3Key, totalLines);
                    
                    return extractedText.toString();
                    
                } else {
                    String errorMessage = "Textract job failed with status: " + response.jobStatus();
                    if (response.statusMessage() != null) {
                        errorMessage += " - " + response.statusMessage();
                    }
                    throw new RuntimeException(errorMessage);
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while waiting for Textract job to complete", e);
            throw new RuntimeException("Textract processing was interrupted", e);
        } catch (UnsupportedDocumentException e) {
            logger.error("Unsupported document format for s3://{}/{}", bucketName, s3Key, e);
            
            // Create a user-friendly error with specific suggestions
            String fileName = s3Key.substring(s3Key.lastIndexOf('/') + 1);
            String userFriendlyError = String.format(
                "The PDF file '%s' has an unsupported format. " +
                "Please try one of these solutions:\n\n" +
                "1. Open the PDF in Adobe Reader and 'Save As' a new PDF\n" +
                "2. Use an online PDF converter (like SmallPDF.com) to re-save the file\n" +
                "3. Ensure the PDF is not password-protected or encrypted\n" +
                "4. If it's a scanned document, try using OCR software first\n" +
                "5. Save as PDF version 1.4 or higher (not PDF/A or PDF/X)",
                fileName
            );
            
            throw new UnsupportedDocumentFormatException(userFriendlyError, e);
            
        } catch (TextractException e) {
            logger.error("Textract service error for s3://{}/{}", bucketName, s3Key, e);
            
            String fileName = s3Key.substring(s3Key.lastIndexOf('/') + 1);
            String errorCode = e.awsErrorDetails().errorCode();
            
            // Provide specific guidance based on error type
            String suggestion = getSuggestionForTextractError(errorCode, fileName);
            throw new DocumentProcessingException(suggestion, e);
            
        } catch (Exception e) {
            logger.error("Unexpected error extracting text from document s3://{}/{}", bucketName, s3Key, e);
            throw new RuntimeException("Failed to extract text from document: " + s3Key + 
                ". Please ensure the file is a valid PDF and try again.", e);
        }
    }
    
    private String getSuggestionForTextractError(String errorCode, String fileName) {
        switch (errorCode) {
            case "InvalidParameterException":
                return String.format(
                    "The PDF '%s' contains invalid parameters. Please:\n" +
                    "• Ensure the file size is under 5MB\n" +
                    "• Check that the PDF is not corrupted\n" +
                    "• Try opening and re-saving the PDF", 
                    fileName
                );
                
            case "DocumentTooLargeException":
                return String.format(
                    "The PDF '%s' is too large for processing. Please:\n" +
                    "• Reduce file size to under 5MB\n" +
                    "• Compress the PDF using online tools\n" +
                    "• Remove unnecessary images or pages", 
                    fileName
                );
                
            case "BadDocumentException":
                return String.format(
                    "The PDF '%s' appears to be corrupted. Please:\n" +
                    "• Try opening the file to verify it's not corrupted\n" +
                    "• Re-create or re-download the PDF\n" +
                    "• Use a PDF repair tool if needed", 
                    fileName
                );
                
            default:
                return String.format(
                    "Unable to process '%s'. Please:\n" +
                    "• Ensure it's a standard PDF file\n" +
                    "• Check that it's not password-protected\n" +
                    "• Try converting to PDF using a different tool\n" +
                    "• Contact support if the issue persists\n" +
                    "Error: %s", 
                    fileName, errorCode
                );
        }
    }
    
}