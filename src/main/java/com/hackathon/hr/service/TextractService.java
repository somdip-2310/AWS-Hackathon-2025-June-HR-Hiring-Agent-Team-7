// src/main/java/com/hackathon/hr/service/TextractService.java
package com.hackathon.hr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hackathon.hr.exception.DocumentProcessingException;
import com.hackathon.hr.exception.UnsupportedDocumentFormatException;

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
            
            // Use synchronous processing only (no async to avoid permission issues)
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
            
        } catch (AccessDeniedException e) {
            logger.error("Access denied for Textract operation on s3://{}/{}", bucketName, s3Key, e);
            throw new DocumentProcessingException("PROCESSING_ERROR: Access denied - please contact system administrator. The application doesn't have required AWS permissions.", e);
            
        } catch (InvalidS3ObjectException e) {
            logger.error("Invalid S3 object for s3://{}/{}", bucketName, s3Key, e);
            
            String fileName = s3Key.substring(s3Key.lastIndexOf('/') + 1);
            throw new DocumentProcessingException(String.format(
                "PROCESSING_ERROR: Unable to access '%s' from S3. Please ensure the file was uploaded correctly and try again.",
                fileName
            ), e);
            
        } catch (TextractException e) {
            logger.error("Textract service error for s3://{}/{}", bucketName, s3Key, e);
            
            String fileName = s3Key.substring(s3Key.lastIndexOf('/') + 1);
            String errorCode = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "Unknown";
            
            // Provide specific guidance based on error type
            String suggestion = getSuggestionForTextractError(errorCode, fileName);
            throw new DocumentProcessingException(suggestion, e);
            
        } catch (Exception e) {
            // For any other unexpected errors, log and re-throw without wrapping
            logger.error("Unexpected error extracting text from document s3://{}/{}", bucketName, s3Key, e);
            throw new RuntimeException("Failed to extract text from document: " + s3Key + 
                ". Please ensure the file is a valid PDF and try again.", e);
        }
    }
    
    private String getSuggestionForTextractError(String errorCode, String fileName) {
        switch (errorCode) {
            case "InvalidParameterException":
                return String.format(
                    "PROCESSING_ERROR: The PDF '%s' contains invalid parameters. Please:\n" +
                    "• Ensure the file size is under 5MB\n" +
                    "• Check that the PDF is not corrupted\n" +
                    "• Try opening and re-saving the PDF", 
                    fileName
                );
                
            case "DocumentTooLargeException":
                return String.format(
                    "PROCESSING_ERROR: The PDF '%s' is too large for processing. Please:\n" +
                    "• Reduce file size to under 5MB\n" +
                    "• Compress the PDF using online tools\n" +
                    "• Remove unnecessary images or pages", 
                    fileName
                );
                
            case "BadDocumentException":
                return String.format(
                    "PROCESSING_ERROR: The PDF '%s' appears to be corrupted. Please:\n" +
                    "• Try opening the file to verify it's not corrupted\n" +
                    "• Re-create or re-download the PDF\n" +
                    "• Use a PDF repair tool if needed", 
                    fileName
                );
                
            case "ProvisionedThroughputExceededException":
                return String.format(
                    "PROCESSING_ERROR: Service is currently busy processing '%s'. Please:\n" +
                    "• Wait a few moments and try again\n" +
                    "• Upload files one at a time if uploading multiple files", 
                    fileName
                );
                
            case "ThrottlingException":
                return String.format(
                    "PROCESSING_ERROR: Too many requests for '%s'. Please:\n" +
                    "• Wait a few seconds and try again\n" +
                    "• Reduce the number of simultaneous uploads", 
                    fileName
                );
                
            case "AccessDeniedException":
                return "PROCESSING_ERROR: System configuration error. Please contact the administrator.";
                
            default:
                return String.format(
                    "PROCESSING_ERROR: Unable to process '%s'. Please:\n" +
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