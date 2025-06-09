// src/main/java/com/hackathon/hr/service/BedrockService.java
package com.hackathon.hr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Service
public class BedrockService {

    private static final Logger logger = LoggerFactory.getLogger(BedrockService.class);
    
    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.bedrock.model-id}")
    private String modelId;

    public BedrockService(BedrockRuntimeClient bedrockRuntimeClient) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
        this.objectMapper = new ObjectMapper();
    }

    public String invokeModel(String prompt) {
        try {
            logger.debug("Invoking model {} with prompt: {}", modelId, prompt);

            // Create the correct JSON payload for Nova Premier
            String jsonBody = String.format("""
                {
                    "messages": [
                        {
                            "role": "user",
                            "content": [
                                {
                                    "text": "%s"
                                }
                            ]
                        }
                    ],
                    "inferenceConfig": {
                        "maxTokens": 2000,
                        "temperature": 0.7,
                        "topP": 0.9
                    }
                }
                """, escapeJsonString(prompt));

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(jsonBody))
                    .build();

            InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request);
            
            // Parse response
            String responseBody = response.body().asUtf8String();
            logger.debug("Raw response: {}", responseBody);
            
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            
            // Extract text from Nova Premier response format
            String responseText = jsonResponse.get("output")
                                            .get("message")
                                            .get("content")
                                            .get(0)
                                            .get("text")
                                            .asText();
            
            logger.debug("Model response: {}", responseText);
            return responseText;

        } catch (Exception e) {
            logger.error("Error invoking Bedrock model", e);
            throw new RuntimeException("Failed to invoke AI model", e);
        }
    }
    
    /**
     * Escape special characters in JSON strings
     */
    private String escapeJsonString(String input) {
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\r", "\\r")
                   .replace("\n", "\\n")
                   .replace("\t", "\\t");
    }
}