// src/main/java/com/hackathon/hr/service/SessionManagementService.java
package com.hackathon.hr.service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class SessionManagementService {
    
	@Autowired
    public SessionManagementService(EmailService emailService) {
        this.emailService = emailService;
    }
	
    private static final Logger logger = LoggerFactory.getLogger(SessionManagementService.class);
    
    private EmailService emailService;
    
    @Value("${application.url:https://demos.somdip.dev/hr-agent}")
    private String applicationUrl;
    // Session duration in minutes
    @Value("${hr.demo.session.duration:7}")
    private int sessionDurationMinutes;
    
    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
 // Queue management
    private final Queue<QueuedUser> waitingQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, QueuedUser> queuedUsers = new ConcurrentHashMap<>();
    
    // In-memory session storage (cost-effective for demo)
    private final Map<String, DemoSession> activeSessions = new ConcurrentHashMap<>();
    private volatile DemoSession currentActiveSession = null;
    
    // Email verification codes (temporary storage)
    private final Map<String, EmailVerification> emailVerifications = new ConcurrentHashMap<>();
    
    // EMAIL STORAGE - Cost-effective approach for tracking emails
    private final Map<String, EmailRecord> emailHistory = new ConcurrentHashMap<>();
 // Add access token storage
    private final Map<String, AccessToken> accessTokens = new ConcurrentHashMap<>();
    /**
     * Check if a new session can be started
     */
    public SessionStatus checkSessionAvailability() {
        cleanupExpiredSessions();
        
        if (currentActiveSession == null || isSessionExpired(currentActiveSession)) {
            return new SessionStatus(true, null, null);
        }
        
        long remainingMinutes = getRemainingSessionTime(currentActiveSession);
        return new SessionStatus(false, currentActiveSession.getEmail(), remainingMinutes);
    }
    
    /**
     * Start a new demo session for verified email
     */
    public SessionStartResult startSession(String email) {
        try {
            // Validate email format
            if (!isValidEmail(email)) {
                return new SessionStartResult(false, "Invalid email format", null);
            }
            
            // Check if session can be started
            SessionStatus status = checkSessionAvailability();
            if (!status.isAvailable()) {
                // Session is not available, check if user should be added to queue
                
                // Check if user is already in queue
                String existingQueueId = null;
                int queuePosition = -1;
                for (QueuedUser user : waitingQueue) {
                    if (user.getEmail().equalsIgnoreCase(email)) {
                        existingQueueId = user.getQueueId();
                        queuePosition = getQueuePosition(existingQueueId);
                        break;
                    }
                }
                
                if (existingQueueId != null) {
                    // User is already in queue
                    if (queuePosition == 1) {
                        // They're next in line but session still active
                        return new SessionStartResult(false, 
                            "You are next in queue. The current session will end in " + 
                            status.getRemainingMinutes() + " minutes.", null);
                    } else {
                        // They're further back in queue
                        long estimatedWait = calculateEstimatedWaitTime();
                        return new SessionStartResult(false, 
                            "You are #" + queuePosition + " in queue. Estimated wait: " + 
                            estimatedWait + " minutes.", null);
                    }
                } else {
                    // Not in queue yet - add them
                    String queueId = addToQueue(email);
                    int newPosition = getQueuePosition(queueId);
                    long estimatedWait = calculateEstimatedWaitTime();
                    
                    return new SessionStartResult(false, 
                        "Session unavailable. You've been added to queue at position #" + 
                        newPosition + ". Estimated wait: " + estimatedWait + " minutes.", null);
                }
            }
            
            // Session is available - check if user was in queue
            String queueId = null;
            for (QueuedUser user : waitingQueue) {
                if (user.getEmail().equalsIgnoreCase(email)) {
                    queueId = user.getQueueId();
                    break;
                }
            }
            
            // If user was in queue, verify they're next
            if (queueId != null) {
                QueuedUser nextUser = waitingQueue.peek();
                if (nextUser != null && !nextUser.getQueueId().equals(queueId)) {
                    // They're not next in line
                    int position = getQueuePosition(queueId);
                    long estimatedWait = calculateEstimatedWaitTime();
                    return new SessionStartResult(false, 
                        "You are #" + position + " in queue. Please wait your turn. " +
                        "Estimated wait: " + estimatedWait + " minutes.", null);
                }
                
                // They are next - remove from queue
                removeFromQueue(queueId);
                logger.info("User {} removed from queue to start session", maskEmail(email));
            }
            
            // Create new session
            String sessionId = generateSessionId();
            DemoSession session = new DemoSession(sessionId, email, LocalDateTime.now());
            
            activeSessions.put(sessionId, session);
            currentActiveSession = session;
            
            // Store email for tracking
            storeEmailRecord(email, sessionId);
            
            logger.info("Demo session started for email: {} with session ID: {}", 
                       maskEmail(email), sessionId);
         // Send welcome email (async, don't wait)
            emailService.sendWelcomeEmail(email);
            
            // Notify next user in queue if any
            QueuedUser nextInQueue = waitingQueue.peek();
            if (nextInQueue != null) {
                logger.info("Next user in queue: {} (Position #1)", 
                           maskEmail(nextInQueue.getEmail()));
            }
            
            return new SessionStartResult(true, "Session started successfully", sessionId);
            
        } catch (Exception e) {
            logger.error("Error starting session for email: {}", maskEmail(email), e);
            return new SessionStartResult(false, "Failed to start session", null);
        }
    }
    
    /**
     * Validate session for upload operations
     */
    public boolean validateSessionForUpload(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            logger.debug("Session validation failed: sessionId is null or empty");
            return false;
        }
        
        cleanupExpiredSessions();
        
        DemoSession session = activeSessions.get(sessionId);
        if (session == null) {
            logger.debug("Session validation failed: session not found for ID: {}", sessionId);
            return false;
        }
        
        if (isSessionExpired(session)) {
            logger.debug("Session validation failed: session expired for ID: {}", sessionId);
            return false;
        }
        
        // Check if this is the current active session
        boolean isCurrentActive = currentActiveSession != null && 
                                 currentActiveSession.getSessionId().equals(sessionId);
        
        if (!isCurrentActive) {
            logger.debug("Session validation failed: not the current active session for ID: {}", sessionId);
        } else {
            logger.debug("Session validation successful for ID: {}", sessionId);
        }
        
        return isCurrentActive;
    }
    
    /**
     * Get session information
     */
    public DemoSession getSession(String sessionId) {
        if (sessionId == null) return null;
        return activeSessions.get(sessionId);
    }
    
    /**
     * Get current active session info for frontend
     */
    public Map<String, Object> getCurrentSessionInfo() {
        cleanupExpiredSessions();
        
        Map<String, Object> info = new ConcurrentHashMap<>();
        
        if (currentActiveSession == null || isSessionExpired(currentActiveSession)) {
            info.put("hasActiveSession", false);
            info.put("available", true);
        } else {
            info.put("hasActiveSession", true);
            info.put("available", false);
            info.put("userEmail", maskEmail(currentActiveSession.getEmail()));
            info.put("remainingMinutes", getRemainingSessionTime(currentActiveSession));
            info.put("remainingSeconds", getRemainingSessionTimeInSeconds(currentActiveSession));
            info.put("startTime", currentActiveSession.getStartTime().toString());
        }
        
        // Add queue information
        Map<String, Object> queueStatus = getQueueStatus();
        info.put("queueLength", queueStatus.get("queueLength"));
        info.put("waitingUsers", queueStatus.get("waitingUsers"));
        info.put("estimatedWaitTime", queueStatus.get("estimatedWaitTime"));
        
        info.put("sessionDuration", sessionDurationMinutes);
        return info;
    }
    
    public SessionCleanupResult cleanupExpiredSessions() {
        boolean sessionExpired = false;
        String expiredUserEmail = null;
        
        try {
            // Remove expired sessions
            activeSessions.entrySet().removeIf(entry -> isSessionExpired(entry.getValue()));
            
            // Reset current active session if expired
            if (currentActiveSession != null && isSessionExpired(currentActiveSession)) {
                expiredUserEmail = currentActiveSession.getEmail();
                logger.info("Current session expired for user: {}", 
                           maskEmail(currentActiveSession.getEmail()));
                currentActiveSession = null;
                
                // Send session expired notification
                try {
                    if (expiredUserEmail != null) {
                        emailService.sendSessionExpiredEmail(expiredUserEmail);
                    }
                } catch (Exception e) {
                    logger.error("Failed to send session expired email", e);
                }
                
                sessionExpired = true;
                
                // IMPORTANT: Process queue when session expires
                processNextInQueue();
            }
            
            // Cleanup old email verifications (older than 10 minutes)
            emailVerifications.entrySet().removeIf(entry -> 
                ChronoUnit.MINUTES.between(entry.getValue().getCreatedAt(), LocalDateTime.now()) > 10);
            
            // Clean up stale queue entries (older than 30 minutes)
            waitingQueue.removeIf(user -> 
                ChronoUnit.MINUTES.between(user.getJoinedAt(), LocalDateTime.now()) > 30);
            
            // Update queued users map
            queuedUsers.values().removeIf(user -> !waitingQueue.contains(user));
            accessTokens.entrySet().removeIf(entry -> entry.getValue().isExpired());
                
        } catch (Exception e) {
            logger.error("Error during session cleanup", e);
        }
        
        return new SessionCleanupResult(sessionExpired, expiredUserEmail);
    }
    
    /**
     * Process next user in queue
     */
    private void processNextInQueue() {
        try {
            QueuedUser nextUser = waitingQueue.peek();
            if (nextUser != null) {
                logger.info("Processing next user in queue: {}", maskEmail(nextUser.getEmail()));
                
                // Generate access token
                String accessToken = generateAccessToken(nextUser.getEmail());
                
                // Generate access link
                String accessLink = applicationUrl + "/start-demo?token=" + accessToken;
                
                // Send email notification with link
                emailService.sendQueueTurnEmail(nextUser.getEmail(), accessLink, 2)
                    .thenAccept(sent -> {
                        if (sent) {
                            logger.info("Queue turn notification sent to: {}", maskEmail(nextUser.getEmail()));
                        } else {
                            logger.error("Failed to send queue turn notification to: {}", maskEmail(nextUser.getEmail()));
                        }
                    });
                
                // Log for monitoring
                logger.info("User {} is now first in queue and has been sent an access link", 
                           maskEmail(nextUser.getEmail()));
            }
        } catch (Exception e) {
            logger.error("Error processing queue", e);
        }
    }
    
    private String generateAccessToken(String email) {
        // Generate a secure token for the user
        String token = UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
        // Store token with expiration (2 minutes as per requirement)
        AccessToken accessToken = new AccessToken(email, token, LocalDateTime.now().plusMinutes(2));
        accessTokens.put(token, accessToken);
        return token;
    }
    
    /**
     * Enhanced end session method
     */
    public boolean endSession(String sessionId) {
        try {
            DemoSession session = activeSessions.remove(sessionId);
            if (session != null) {
                if (currentActiveSession != null && 
                    currentActiveSession.getSessionId().equals(sessionId)) {
                    currentActiveSession = null;
                    
                    // Process queue when session ends manually
                    processNextInQueue();
                }
                
                logger.info("Session ended manually: {} for user: {}", 
                           sessionId, maskEmail(session.getEmail()));
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error ending session: {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * Send verification code to email (simulated - cost-effective approach)
     */
    /**
     * Updated sendVerificationCode to handle queue better
     */
    public EmailVerificationResult sendVerificationCode(String email) {
        try {
            if (!isValidEmail(email)) {
                return new EmailVerificationResult(false, "Invalid email format");
            }
            
            // Normalize email to lowercase for consistent storage
            String normalizedEmail = email.toLowerCase().trim();
            
            // Check if user is in queue
            QueuedUser queuedUser = null;
            for (QueuedUser user : waitingQueue) {
                if (user.getEmail().equalsIgnoreCase(normalizedEmail)) {
                    queuedUser = user;
                    break;
                }
            }
            
            if (queuedUser != null) {
                // Check if they're first in queue and session is available
                QueuedUser firstInQueue = waitingQueue.peek();
                if (firstInQueue != null && firstInQueue.getQueueId().equals(queuedUser.getQueueId())) {
                    // They're first in queue - check if session is available
                    SessionStatus status = checkSessionAvailability();
                    if (status.isAvailable()) {
                        // Remove from queue and allow verification
                        removeFromQueue(queuedUser.getQueueId());
                        logger.info("User {} removed from queue to request verification", maskEmail(email));
                    } else {
                        // Still need to wait
                        int position = getQueuePosition(queuedUser.getQueueId());
                        return new EmailVerificationResult(false, 
                            "You are #" + position + " in queue. Current session will end in " + 
                            status.getRemainingMinutes() + " minutes.");
                    }
                } else {
                    // Not first in queue
                    int position = getQueuePosition(queuedUser.getQueueId());
                    long estimatedWait = calculateEstimatedWaitTime();
                    return new EmailVerificationResult(false, 
                        "You are #" + position + " in queue. Estimated wait: " + estimatedWait + " minutes.");
                }
            }
            
            // Check for active session (not in queue)
            if (activeSessions.values().stream().anyMatch(session -> session.getEmail().equalsIgnoreCase(normalizedEmail))) {
                return new EmailVerificationResult(false, "You already have an active session");
            }
            
            // Check if session is available
            SessionStatus sessionStatus = checkSessionAvailability();
            if (!sessionStatus.isAvailable()) {
                // Add to queue instead of just rejecting
                String queueId = addToQueue(normalizedEmail);
                int position = getQueuePosition(queueId);
                long estimatedWait = calculateEstimatedWaitTime();
                
                return new EmailVerificationResult(false, 
                    "Session unavailable. You've been added to queue at position #" + position + 
                    ". Estimated wait: " + estimatedWait + " minutes. We'll notify you when it's your turn.");
            }
            
            // Rest of the original code for sending verification...
            // Check rate limiting (use normalized email)
            EmailVerification existingVerification = emailVerifications.get(normalizedEmail);
            if (existingVerification != null) {
                long minutesSinceLastRequest = ChronoUnit.MINUTES.between(existingVerification.getCreatedAt(), LocalDateTime.now());
                if (minutesSinceLastRequest < 2) {
                    return new EmailVerificationResult(false, "Please wait 2 minutes before requesting another code");
                }
            }
            
            // Generate 6-digit verification code
            String verificationCode = String.format("%06d", (int)(Math.random() * 900000) + 100000);
            
            // Store verification with normalized email as key
            EmailVerification verification = new EmailVerification(email, verificationCode, LocalDateTime.now());
            emailVerifications.put(normalizedEmail, verification);
            
            logger.info("Storing verification code for normalized email: {} (original: {})", 
                       maskEmail(normalizedEmail), maskEmail(email));
            
            // Send actual email via SendGrid (async)
            CompletableFuture<Boolean> emailFuture = emailService.sendVerificationEmail(email, verificationCode);
            
            // Store email record for tracking
            EmailRecord record = emailHistory.computeIfAbsent(normalizedEmail, 
                k -> new EmailRecord(email, null, LocalDateTime.now()));
            record.incrementUsageCount();
            
            // Wait for email result with timeout
            try {
                boolean emailSent = emailFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
                if (!emailSent) {
                    emailVerifications.remove(normalizedEmail);
                    return new EmailVerificationResult(false, "Failed to send verification email. Please try again.");
                }
            } catch (Exception e) {
                logger.error("Error waiting for email send result", e);
                // Continue anyway - email might still be sent
            }
            
            return new EmailVerificationResult(true, 
                "Verification code sent to your email! Please check your inbox.");
                
        } catch (Exception e) {
            logger.error("Error sending verification code to: {}", maskEmail(email), e);
            return new EmailVerificationResult(false, "Failed to send verification code");
        }
    }
    
    /**
     * Verify email with code
     */
    public EmailVerificationResult verifyEmail(String email, String code) {
        try {
            // Normalize email to lowercase for lookup
            String normalizedEmail = email.toLowerCase().trim();
            
            logger.info("Attempting to verify email: {} (normalized: {})", 
                       maskEmail(email), maskEmail(normalizedEmail));
            
            EmailVerification verification = emailVerifications.get(normalizedEmail);
            
            if (verification == null) {
                logger.warn("No verification code found for email: {} (normalized: {})", 
                           maskEmail(email), maskEmail(normalizedEmail));
                
                // Debug: log all stored verification keys
                logger.debug("Currently stored verification emails: {}", 
                            emailVerifications.keySet().stream()
                                .map(this::maskEmail)
                                .collect(Collectors.joining(", ")));
                
                return new EmailVerificationResult(false, "No verification code found for this email");
            }
            
            // Check if code is expired (10 minutes)
            long minutesSinceCreation = ChronoUnit.MINUTES.between(verification.getCreatedAt(), LocalDateTime.now());
            if (minutesSinceCreation > 10) {
                emailVerifications.remove(normalizedEmail);
                logger.info("Verification code expired for email: {} (created {} minutes ago)", 
                           maskEmail(email), minutesSinceCreation);
                return new EmailVerificationResult(false, "Verification code expired. Please request a new one.");
            }
            
            // Verify code
            String trimmedCode = code.trim();
            if (!verification.getCode().equals(trimmedCode)) {
                logger.warn("Invalid verification code attempt for email: {} (expected: {}, received: {})", 
                           maskEmail(email), verification.getCode(), trimmedCode);
                return new EmailVerificationResult(false, "Invalid verification code");
            }
            
            // Mark as verified and remove from temporary storage
            emailVerifications.remove(normalizedEmail);
            
            logger.info("Email verified successfully: {} (normalized: {})", 
                       maskEmail(email), maskEmail(normalizedEmail));
            
            return new EmailVerificationResult(true, "Email verified successfully");
            
        } catch (Exception e) {
            logger.error("Error verifying email: {}", maskEmail(email), e);
            return new EmailVerificationResult(false, "Verification failed");
        }
    }
    
    //Quese Management method
    /**
     * Add user to waiting queue after email verification
     */
    public String addToQueue(String email) {
        // Check if already in queue
        for (QueuedUser user : waitingQueue) {
            if (user.getEmail().equalsIgnoreCase(email)) {
                return user.getQueueId();
            }
        }
        
        QueuedUser queuedUser = new QueuedUser(email, LocalDateTime.now());
        waitingQueue.offer(queuedUser);
        queuedUsers.put(queuedUser.getQueueId(), queuedUser);
        
        logger.info("User added to queue: {} at position {}", 
                   maskEmail(email), getQueuePosition(queuedUser.getQueueId()));
        
        return queuedUser.getQueueId();
    }

    /**
     * Get queue position for a user
     */
    public int getQueuePosition(String queueId) {
        int position = 1;
        for (QueuedUser user : waitingQueue) {
            if (user.getQueueId().equals(queueId)) {
                return position;
            }
            position++;
        }
        return -1; // Not in queue
    }

    /**
     * Remove user from queue
     */
    public void removeFromQueue(String queueId) {
        QueuedUser user = queuedUsers.remove(queueId);
        if (user != null) {
            waitingQueue.remove(user);
            logger.info("User removed from queue: {}", maskEmail(user.getEmail()));
        }
    }

    /**
     * Get next user in queue when session ends
     */
    public QueuedUser getNextInQueue() {
        // Clean up old queue entries (older than 30 minutes)
        waitingQueue.removeIf(user -> 
            ChronoUnit.MINUTES.between(user.getJoinedAt(), LocalDateTime.now()) > 30);
        
        return waitingQueue.poll();
    }

    /**
     * Get queue status including wait list
     */
    public Map<String, Object> getQueueStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // Clean up old entries
        waitingQueue.removeIf(user -> 
            ChronoUnit.MINUTES.between(user.getJoinedAt(), LocalDateTime.now()) > 30);
        
        status.put("queueLength", waitingQueue.size());
        
        // Get masked emails of waiting users (show first 3)
        List<Map<String, Object>> waitingList = new ArrayList<>();
        int count = 0;
        for (QueuedUser user : waitingQueue) {
            if (count >= 3) break; // Only show first 3
            
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("email", maskEmail(user.getEmail()));
            userInfo.put("waitTime", ChronoUnit.MINUTES.between(user.getJoinedAt(), LocalDateTime.now()) + " min");
            userInfo.put("position", count + 1);
            waitingList.add(userInfo);
            count++;
        }
        
        status.put("waitingUsers", waitingList);
        status.put("estimatedWaitTime", calculateEstimatedWaitTime());
        
        return status;
    }
    
 // Add method to check if it's user's turn
    public boolean isUserTurn(String email) {
        QueuedUser nextUser = waitingQueue.peek();
        return nextUser != null && nextUser.getEmail().equalsIgnoreCase(email);
    }

    // Add method to handle turn timeout
    public void handleTurnTimeout(String email) {
        QueuedUser nextUser = waitingQueue.peek();
        if (nextUser != null && nextUser.getEmail().equalsIgnoreCase(email)) {
            removeFromQueue(nextUser.getQueueId());
            logger.info("User {} forfeited their turn due to timeout", maskEmail(email));
        }
    }
    
    public VerifyTokenResult verifyAccessToken(String token) {
        try {
            AccessToken accessToken = accessTokens.get(token);
            
            if (accessToken == null) {
                return new VerifyTokenResult(false, "Invalid access token", null);
            }
            
            if (accessToken.isExpired()) {
                accessTokens.remove(token);
                return new VerifyTokenResult(false, "Access token has expired", null);
            }
            
            // Remove user from queue if they're still there
            String email = accessToken.getEmail();
            QueuedUser userInQueue = null;
            for (QueuedUser user : waitingQueue) {
                if (user.getEmail().equalsIgnoreCase(email)) {
                    userInQueue = user;
                    break;
                }
            }
            
            if (userInQueue != null) {
                removeFromQueue(userInQueue.getQueueId());
            }
            
            // Remove the token after use
            accessTokens.remove(token);
            
            return new VerifyTokenResult(true, "Token verified successfully", email);
            
        } catch (Exception e) {
            logger.error("Error verifying access token", e);
            return new VerifyTokenResult(false, "Failed to verify token", null);
        }
    }
    
    /**
     * Calculate estimated wait time based on queue position
     */
    private long calculateEstimatedWaitTime() {
        if (currentActiveSession == null) return 0;
        
        long remainingMinutes = getRemainingSessionTime(currentActiveSession);
        long queueSize = waitingQueue.size();
        
        // Each person gets 7 minutes, plus current session time
        return remainingMinutes + (queueSize * sessionDurationMinutes);
    }
    
    
    // ========================================
    // EMAIL TRACKING METHODS (NEW)
    // ========================================
    
    /**
     * Store email record for tracking
     */
    private void storeEmailRecord(String email, String sessionId) {
        try {
            String emailKey = email.toLowerCase().trim();
            EmailRecord existingRecord = emailHistory.get(emailKey);
            
            if (existingRecord != null) {
                // Update existing record
                existingRecord.incrementUsageCount();
                existingRecord.setLastUsed(LocalDateTime.now());
                existingRecord.setLastSessionId(sessionId);
            } else {
                // Create new record
                EmailRecord newRecord = new EmailRecord(email, sessionId, LocalDateTime.now());
                emailHistory.put(emailKey, newRecord);
            }
            
            logger.info("Email usage tracked for: {}", maskEmail(email));
            
        } catch (Exception e) {
            logger.error("Error storing email record for: {}", maskEmail(email), e);
        }
    }
    
    /**
     * Get all email records (for admin viewing)
     */
    public List<Map<String, Object>> getAllEmailRecords() {
        try {
            return emailHistory.values().stream()
                .map(record -> {
                    Map<String, Object> recordMap = new ConcurrentHashMap<>();
                    recordMap.put("email", maskEmail(record.getEmail()));
                    recordMap.put("usageCount", record.getUsageCount());
                    recordMap.put("firstUsed", record.getFirstUsed().toString());
                    recordMap.put("lastUsed", record.getLastUsed().toString());
                    recordMap.put("lastSessionId", record.getLastSessionId());
                    return recordMap;
                })
                .sorted((a, b) -> ((String)b.get("lastUsed")).compareTo((String)a.get("lastUsed")))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.error("Error retrieving email records", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get email statistics
     */
    public Map<String, Object> getEmailStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        try {
            stats.put("totalUniqueEmails", emailHistory.size());
            stats.put("totalSessions", emailHistory.values().stream()
                .mapToInt(EmailRecord::getUsageCount)
                .sum());
            
            // Recent activity (last 24 hours)
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            long recentUsers = emailHistory.values().stream()
                .filter(record -> record.getLastUsed().isAfter(yesterday))
                .count();
            stats.put("recentUsers24h", recentUsers);
            
            // Most active users
            List<Map<String, Object>> topUsers = emailHistory.values().stream()
                .filter(record -> record.getUsageCount() > 1)
                .sorted((a, b) -> Integer.compare(b.getUsageCount(), a.getUsageCount()))
                .limit(5)
                .map(record -> {
                    Map<String, Object> userMap = new ConcurrentHashMap<>();
                    userMap.put("email", maskEmail(record.getEmail()));
                    userMap.put("usageCount", record.getUsageCount());
                    return userMap;
                })
                .collect(Collectors.toList());
            stats.put("topUsers", topUsers);
            
            stats.put("timestamp", LocalDateTime.now().toString());
            
        } catch (Exception e) {
            logger.error("Error generating email statistics", e);
            stats.put("error", "Failed to generate statistics");
        }
        
        return stats;
    }
    
    // Helper methods
    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }
    
    private boolean isSessionExpired(DemoSession session) {
        if (session == null) return true;
        return ChronoUnit.MINUTES.between(session.getStartTime(), LocalDateTime.now()) >= sessionDurationMinutes;
    }
    
    private long getRemainingSessionTime(DemoSession session) {
        if (session == null) return 0;
        long elapsed = ChronoUnit.MINUTES.between(session.getStartTime(), LocalDateTime.now());
        return Math.max(0, sessionDurationMinutes - elapsed);
    }
    
    private long getRemainingSessionTimeInSeconds(DemoSession session) {
        if (session == null) return 0;
        long elapsed = ChronoUnit.SECONDS.between(session.getStartTime(), LocalDateTime.now());
        return Math.max(0, (sessionDurationMinutes * 60) - elapsed);
    }
    
    private String generateSessionId() {
        return "demo_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }
    
    public String maskEmail(String email) {
        if (email == null || email.length() < 3) return email;
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) return email;
        
        String username = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        
        if (username.length() <= 2) {
            return username.charAt(0) + "*" + domain;
        } else {
            return username.charAt(0) + "*".repeat(username.length() - 2) + username.charAt(username.length() - 1) + domain;
        }
    }
    
    // Inner classes
    public static class SessionStatus {
        private final boolean available;
        private final String currentUserEmail;
        private final Long remainingMinutes;
        
        public SessionStatus(boolean available, String currentUserEmail, Long remainingMinutes) {
            this.available = available;
            this.currentUserEmail = currentUserEmail;
            this.remainingMinutes = remainingMinutes;
        }
        
        public boolean isAvailable() { return available; }
        public String getCurrentUserEmail() { return currentUserEmail; }
        public Long getRemainingMinutes() { return remainingMinutes; }
    }
    
    public static class SessionStartResult {
        private final boolean success;
        private final String message;
        private final String sessionId;
        
        public SessionStartResult(boolean success, String message, String sessionId) {
            this.success = success;
            this.message = message;
            this.sessionId = sessionId;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getSessionId() { return sessionId; }
    }
    
    public static class EmailVerificationResult {
        private final boolean success;
        private final String message;
        
        public EmailVerificationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
    
    public static class DemoSession {
        private final String sessionId;
        private final String email;
        private final LocalDateTime startTime;
        
        public DemoSession(String sessionId, String email, LocalDateTime startTime) {
            this.sessionId = sessionId;
            this.email = email;
            this.startTime = startTime;
        }
        
        public String getSessionId() { return sessionId; }
        public String getEmail() { return email; }
        public LocalDateTime getStartTime() { return startTime; }
    }
    
    public static class EmailVerification {
        private final String email;
        private final String code;
        private final LocalDateTime createdAt;
        
        public EmailVerification(String email, String code, LocalDateTime createdAt) {
            this.email = email;
            this.code = code;
            this.createdAt = createdAt;
        }
        
        public String getEmail() { return email; }
        public String getCode() { return code; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
    
    // NEW: Session cleanup result
    public static class SessionCleanupResult {
        private final boolean sessionExpired;
        private final String expiredUserEmail;
        
        public SessionCleanupResult(boolean sessionExpired, String expiredUserEmail) {
            this.sessionExpired = sessionExpired;
            this.expiredUserEmail = expiredUserEmail;
        }
        
        public boolean isSessionExpired() { return sessionExpired; }
        public String getExpiredUserEmail() { return expiredUserEmail; }
    }
    
    //inner class for queue management
    public static class QueuedUser {
        private final String email;
        private final LocalDateTime joinedAt;
        private final String queueId;
        
        public QueuedUser(String email, LocalDateTime joinedAt) {
            this.email = email;
            this.joinedAt = joinedAt;
            this.queueId = UUID.randomUUID().toString();
        }
        
        public String getEmail() { return email; }
        public LocalDateTime getJoinedAt() { return joinedAt; }
        public String getQueueId() { return queueId; }
    }
    
    public static class AccessToken {
        private final String email;
        private final String token;
        private final LocalDateTime expiresAt;
        
        public AccessToken(String email, String token, LocalDateTime expiresAt) {
            this.email = email;
            this.token = token;
            this.expiresAt = expiresAt;
        }
        
        public String getEmail() { return email; }
        public String getToken() { return token; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }
    
    public static class VerifyTokenResult {
        private final boolean success;
        private final String message;
        private final String email;
        
        public VerifyTokenResult(boolean success, String message, String email) {
            this.success = success;
            this.message = message;
            this.email = email;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getEmail() { return email; }
    }
    
    // NEW: Email record for tracking
    public static class EmailRecord {
        private final String email;
        private final String firstSessionId;
        private final LocalDateTime firstUsed;
        private String lastSessionId;
        private LocalDateTime lastUsed;
        private int usageCount;
        
        public EmailRecord(String email, String sessionId, LocalDateTime timestamp) {
            this.email = email;
            this.firstSessionId = sessionId;
            this.firstUsed = timestamp;
            this.lastSessionId = sessionId;
            this.lastUsed = timestamp;
            this.usageCount = 1;
        }
        
        public void incrementUsageCount() {
            this.usageCount++;
        }
        
        // Getters
        public String getEmail() { return email; }
        public String getFirstSessionId() { return firstSessionId; }
        public LocalDateTime getFirstUsed() { return firstUsed; }
        public String getLastSessionId() { return lastSessionId; }
        public void setLastSessionId(String lastSessionId) { this.lastSessionId = lastSessionId; }
        public LocalDateTime getLastUsed() { return lastUsed; }
        public void setLastUsed(LocalDateTime lastUsed) { this.lastUsed = lastUsed; }
        public int getUsageCount() { return usageCount; }
    }
}