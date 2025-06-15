package com.hackathon.hr.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;

@Controller
public class IframeTestController {
    
    @GetMapping("/iframe-test")
    @ResponseBody
    public String iframeTest(HttpServletResponse response) {
        // Set headers to explicitly allow iframe embedding
        response.setHeader("X-Frame-Options", "ALLOWALL");
        response.setHeader("Content-Security-Policy", "frame-ancestors *;");
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>HR Demo - Iframe Test</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        margin: 0;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    }
                    .container {
                        text-align: center;
                        padding: 40px;
                        background: white;
                        border-radius: 20px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                        max-width: 600px;
                    }
                    h1 {
                        color: #4a5568;
                        margin-bottom: 20px;
                    }
                    .status {
                        background: #48bb78;
                        color: white;
                        padding: 10px 20px;
                        border-radius: 50px;
                        display: inline-block;
                        margin: 20px 0;
                        font-weight: bold;
                    }
                    .info {
                        background: #f7fafc;
                        padding: 20px;
                        border-radius: 10px;
                        margin: 20px 0;
                        border: 1px solid #e2e8f0;
                    }
                    .info p {
                        margin: 5px 0;
                        color: #4a5568;
                    }
                    .info code {
                        background: #edf2f7;
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-family: monospace;
                        color: #5a67d8;
                    }
                    .button {
                        background: #5a67d8;
                        color: white;
                        padding: 12px 30px;
                        border-radius: 8px;
                        text-decoration: none;
                        display: inline-block;
                        margin: 10px;
                        transition: background 0.3s;
                    }
                    .button:hover {
                        background: #4c51bf;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>✅ HR Demo Service - Iframe Test</h1>
                    <div class="status">WORKING</div>
                    <div class="info">
                        <p><strong>Service:</strong> <code>HR Demo Service</code></p>
                        <p><strong>Status:</strong> <code>Online</code></p>
                        <p><strong>URL:</strong> <code>https://demos.somdip.dev</code></p>
                        <p><strong>Time:</strong> <code id="time"></code></p>
                        <p><strong>Iframe Embedding:</strong> <code>Allowed</code></p>
                    </div>
                    <p style="color: #718096;">
                        If you can see this message in the iframe on somdip.dev,<br>
                        then iframe embedding is working correctly!
                    </p>
                    <a href="/" class="button">Go to HR Demo</a>
                    <a href="/actuator/health" class="button">Check Health</a>
                </div>
                <script>
                    document.getElementById('time').textContent = new Date().toLocaleString();
                    
                    // Log parent window info if available
                    try {
                        if (window.parent !== window) {
                            console.log('Running inside iframe from:', document.referrer);
                        }
                    } catch(e) {
                        console.log('Cannot access parent window (cross-origin)');
                    }
                </script>
            </body>
            </html>
            """;
    }
    
    @GetMapping("/iframe-test/json")
    @ResponseBody
    public String iframeTestJson(HttpServletResponse response) {
        response.setHeader("Content-Type", "application/json");
        response.setHeader("X-Frame-Options", "ALLOWALL");
        
        return String.format("""
            {
                "status": "success",
                "message": "HR Demo Service is accessible via iframe",
                "timestamp": "%s",
                "service": "hr-demo-service",
                "embedding": {
                    "allowed": true,
                    "allowedOrigins": ["https://somdip.dev", "https://www.somdip.dev"],
                    "testUrl": "https://demos.somdip.dev/iframe-test"
                }
            }
            """, System.currentTimeMillis());
    }
}