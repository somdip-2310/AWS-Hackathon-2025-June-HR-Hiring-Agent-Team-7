// src/main/java/com/hackathon/hr/config/AwsConfig.java
package com.hackathon.hr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.textract.TextractClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.PostConstruct;

@Configuration
public class AwsConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(AwsConfig.class);
    
    // Read from environment variable first, then fall back to property
    @Value("${AWS_REGION:${aws.region:us-east-1}}")
    private String region;
    
    @Value("${AWS_ACCESS_KEY_ID:${aws.accessKeyId:}}")
    private String accessKeyId;
    
    @Value("${AWS_SECRET_ACCESS_KEY:${aws.secretAccessKey:}}")
    private String secretAccessKey;
    
    @PostConstruct
    public void logConfiguration() {
        logger.info("AWS Configuration initialized:");
        logger.info("  Region: {}", region);
        logger.info("  Using credentials: {}", (!accessKeyId.isEmpty() ? "Explicit" : "Default chain"));
    }
    
    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        // If credentials are provided in properties, use them
        if (!accessKeyId.isEmpty() && !secretAccessKey.isEmpty()) {
            logger.info("Using provided AWS credentials");
            AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
            return StaticCredentialsProvider.create(awsCredentials);
        } else {
            // Fallback to default credential provider chain
            logger.info("Using default AWS credentials provider chain (IAM role, etc.)");
            return DefaultCredentialsProvider.create();
        }
    }
    
    @Bean
    public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
        logger.info("Creating S3 client for region: {}", region);
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
    
    @Bean
    public TextractClient textractClient(AwsCredentialsProvider credentialsProvider) {
        logger.info("Creating Textract client for region: {}", region);
        return TextractClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
    
    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(AwsCredentialsProvider credentialsProvider) {
        logger.info("Creating Bedrock Runtime client for region: {}", region);
        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}