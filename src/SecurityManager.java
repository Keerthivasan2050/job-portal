import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SecurityManager — centralised security controls for the SmartHire Job Portal.
 *
 * Implements:
 *  - Password hashing with SHA-256 + unique per-account salt (PBKDF2-style stretched)
 *  - CSRF token generation and validation (double-submit pattern)
 *  - Login rate-limiting and account lockout (brute-force defence)
 *  - Security event audit logging (to DB when available, always to stdout)
 *  - Input validation and sanitization
 *  - Centralized security-header injection
 *
 * References: OWASP ASVS 5.0, OWASP Cheat Sheet Series, NIST SP 800-63B-4
 */
public class SecurityManager {

    // ============================================================
    // CONSTANTS
    // ============================================================

    /** Stretching iterations for SHA-256 password hashing. */
    private static final int HASH_ITERATIONS  = 10_000;
    /** Salt byte length — 16 bytes = 128-bit entropy. */
    private static final int SALT_BYTES        = 16;
    /** CSRF token byte length. */
    private static final int CSRF_BYTES        = 32;
    /** Max failed login attempts before account is temporarily locked. */
    public  static final int MAX_FAILED_LOGINS = 5;
    /** Lockout duration in milliseconds (15 minutes). */
    public  static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000L;
    /** Absolute session lifetime in milliseconds (8 hours). */
    public  static final long SESSION_MAX_AGE_MS  = 8 * 60 * 60 * 1000L;
    /** Inactivity timeout in milliseconds (30 minutes). */
    public  static final long SESSION_IDLE_MS     = 30 * 60 * 1000L;
    /** Max input field length for general text fields. */
    public  static final int MAX_FIELD_LENGTH  = 1000;
    /** Max password length (prevents DOS via long bcrypt inputs). */
    public  static final int MAX_PASSWORD_LENGTH = 128;
    /** Minimum password length per NIST SP 800-63B. */
    public  static final int MIN_PASSWORD_LENGTH = 8;
    /** Max file size for resume uploads: 5 MB. */
    public  static final int MAX_FILE_BYTES    = 5 * 1024 * 1024;

    private static final SecureRandom RNG = new SecureRandom();

    // ============================================================
    // RATE LIMITING & ACCOUNT LOCKOUT
    // ============================================================

    /** Per-identifier (email/IP) failed-attempt counters. Key = email or IP. */
    private static final ConcurrentHashMap<String, AtomicInteger> FAIL_COUNTS  = new ConcurrentHashMap<>();
    /** Timestamp of first failure within the current window (ms). */
    private static final ConcurrentHashMap<String, Long>          FAIL_FIRST   = new ConcurrentHashMap<>();
    /** Lockout expiry timestamp per identifier. */
    private static final ConcurrentHashMap<String, Long>          LOCKOUT_UNTIL = new ConcurrentHashMap<>();

    /**
     * Record a failed login attempt for the given identifier.
     * Returns true if the account is now locked.
     */
    public static boolean recordFailedLogin(String identifier) {
        String key = identifier.toLowerCase().trim();
        long now = System.currentTimeMillis();

        // Reset window if last failure was more than 15 minutes ago
        Long firstFail = FAIL_FIRST.get(key);
        if (firstFail != null && (now - firstFail) > LOCKOUT_DURATION_MS) {
            FAIL_COUNTS.remove(key);
            FAIL_FIRST.remove(key);
            LOCKOUT_UNTIL.remove(key);
        }

        FAIL_COUNTS.computeIfAbsent(key, k -> new AtomicInteger(0));
        FAIL_FIRST.putIfAbsent(key, now);
        int count = FAIL_COUNTS.get(key).incrementAndGet();

        if (count >= MAX_FAILED_LOGINS) {
            LOCKOUT_UNTIL.put(key, now + LOCKOUT_DURATION_MS);
            logSecurityEvent("ACCOUNT_LOCKOUT", key, "Account locked after " + count + " failed attempts", (String) null);
            return true;
        }
        return false;
    }

    /**
     * Returns true if the identifier is currently locked out.
     */
    public static boolean isLockedOut(String identifier) {
        String key = identifier.toLowerCase().trim();
        Long until = LOCKOUT_UNTIL.get(key);
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            // Lockout expired — clear state
            FAIL_COUNTS.remove(key);
            FAIL_FIRST.remove(key);
            LOCKOUT_UNTIL.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Returns remaining lockout seconds for the given identifier (0 if not locked).
     */
    public static long lockoutRemainingSeconds(String identifier) {
        String key = identifier.toLowerCase().trim();
        Long until = LOCKOUT_UNTIL.get(key);
        if (until == null) return 0;
        long remaining = until - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : 0;
    }

    /**
     * Clears failed login state for the given identifier (call on successful login).
     */
    public static void clearFailedLogins(String identifier) {
        String key = identifier.toLowerCase().trim();
        FAIL_COUNTS.remove(key);
        FAIL_FIRST.remove(key);
        LOCKOUT_UNTIL.remove(key);
    }

    // ============================================================
    // PASSWORD HASHING  (SHA-256 + unique salt, stretched)
    // ============================================================

    /**
     * Hash a plaintext password with a new random salt.
     * Returns "HASH:<hexSalt>:<hexDigest>" — safe to store in the database.
     */
    public static String hashPassword(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return null;
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        String hexSalt  = bytesToHex(salt);
        String hexDigest = stretchHash(plaintext, salt);
        return "HASH:" + hexSalt + ":" + hexDigest;
    }

    /**
     * Verify a plaintext password against a stored hash produced by {@link #hashPassword}.
     * Also accepts legacy plaintext passwords (those not starting with "HASH:") for
     * backward compatibility with existing seeded accounts.
     */
    public static boolean verifyPassword(String plaintext, String stored) {
        if (plaintext == null || stored == null) return false;
        if (!stored.startsWith("HASH:")) {
            // Legacy plaintext comparison — used during migration
            return stored.equals(plaintext);
        }
        String[] parts = stored.split(":");
        if (parts.length != 3) return false;
        byte[] salt      = hexToBytes(parts[1]);
        String expected  = parts[2];
        String computed  = stretchHash(plaintext, salt);
        // Constant-time comparison to prevent timing attacks
        return constantTimeEquals(expected, computed);
    }

    /** True if the stored value is a legacy plaintext (needs migration). */
    public static boolean isLegacyPassword(String stored) {
        return stored != null && !stored.startsWith("HASH:");
    }

    // --- internal helpers ---

    private static String stretchHash(String plaintext, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);
            // Seed the digest with salt
            md.update(salt);
            md.update(data);
            byte[] digest = md.digest();
            // Stretch: iterate HASH_ITERATIONS times for brute-force resistance
            for (int i = 1; i < HASH_ITERATIONS; i++) {
                md.reset();
                md.update(salt);
                md.update(digest);
                digest = md.digest();
            }
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }

    // ============================================================
    // CSRF TOKEN MANAGEMENT  (double-submit cookie pattern)
    // ============================================================

    /** In-memory store of valid CSRF tokens per session ID. */
    private static final ConcurrentHashMap<String, String> CSRF_TOKENS = new ConcurrentHashMap<>();

    /**
     * Generate a new CSRF token for a session and store it.
     */
    public static String generateCsrfToken(String sessionId) {
        byte[] bytes = new byte[CSRF_BYTES];
        RNG.nextBytes(bytes);
        String token = bytesToHex(bytes);
        CSRF_TOKENS.put(sessionId, token);
        return token;
    }

    /**
     * Validate a submitted CSRF token against the one stored for the session.
     * Returns true only if they match (constant-time).
     */
    public static boolean validateCsrfToken(String sessionId, String submitted) {
        if (sessionId == null || submitted == null) return false;
        String expected = CSRF_TOKENS.get(sessionId);
        return expected != null && constantTimeEquals(expected, submitted);
    }

    /** Remove CSRF token on logout or session destruction. */
    public static void removeCsrfToken(String sessionId) {
        if (sessionId != null) CSRF_TOKENS.remove(sessionId);
    }

    // ============================================================
    // INPUT VALIDATION
    // ============================================================

    /** Returns true if the value is non-null, non-blank, and within max length. */
    public static boolean isValidInput(String value, int maxLen) {
        return value != null && !value.trim().isEmpty() && value.length() <= maxLen;
    }

    /** Returns true if the string is a syntactically valid email address. */
    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        String trimmed = email.trim();
        return trimmed.matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")
                && trimmed.length() <= 150;
    }

    /** Returns true if the value is a valid 10-digit Indian phone number (optional field). */
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) return true; // optional
        return phone.trim().matches("^[6-9]\\d{9}$");
    }

    /**
     * Validate a password against NIST SP 800-63B baseline requirements.
     * Returns null if valid, or an error message if not.
     */
    public static String validatePassword(String password) {
        if (password == null || password.isEmpty()) return "Password is required.";
        if (password.length() < MIN_PASSWORD_LENGTH)
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        if (password.length() > MAX_PASSWORD_LENGTH)
            return "Password must be at most " + MAX_PASSWORD_LENGTH + " characters.";
        // Common/breached password list (minimal inline set)
        Set<String> common = Set.of("password", "password1", "12345678", "123456789",
                "qwerty123", "admin123", "admin", "letmein", "welcome1", "monkey123");
        if (common.contains(password.toLowerCase()))
            return "This password is too common. Please choose a stronger password.";
        return null;
    }

    /**
     * Sanitize a string for safe HTML output (XSS prevention).
     * Always use this before interpolating user-supplied values into HTML.
     */
    public static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    /**
     * Truncate a value to a maximum length and trim whitespace.
     */
    public static String sanitizeInput(String value, int maxLen) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }

    /**
     * Validate that a redirect/return URL is relative (prevents open redirect).
     */
    public static boolean isSafeRedirect(String url) {
        if (url == null || url.isEmpty()) return false;
        // Only allow relative paths starting with /
        return url.startsWith("/") && !url.startsWith("//") && !url.contains("://");
    }

    // ============================================================
    // ALLOWED FILE TYPES FOR UPLOADS
    // ============================================================

    /** Permitted resume MIME types. */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "text/plain"
    );

    /** Permitted resume file extensions (lowercase). */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".docx", ".doc", ".txt");

    public static boolean isAllowedFileType(String filename, String mimeType) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        boolean extOk = ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
        boolean mimeOk = mimeType == null || ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase().split(";")[0].trim());
        return extOk && mimeOk;
    }

    // ============================================================
    // SECURITY HTTP HEADERS
    // ============================================================

    /**
     * Set all recommended security response headers on any HttpExchange.
     * Implements OWASP Cheat Sheet recommendations.
     */
    public static void setSecurityHeaders(com.sun.net.httpserver.HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();

        // Content Security Policy — restrict resource loading
        headers.set("Content-Security-Policy",
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline'; " +   // unsafe-inline needed for inline <script> blocks
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data:; " +
            "font-src 'self'; " +
            "connect-src 'self'; " +
            "frame-ancestors 'none'; " +
            "form-action 'self'; " +
            "base-uri 'self';"
        );

        // Prevent MIME-type sniffing
        headers.set("X-Content-Type-Options", "nosniff");

        // Clickjacking protection (belt-and-suspenders with CSP frame-ancestors)
        headers.set("X-Frame-Options", "DENY");

        // Referrer policy — do not leak URL path to third parties
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");

        // Restrict browser API access
        headers.set("Permissions-Policy",
            "camera=(), microphone=(), geolocation=(), payment=(), usb=()");

        // XSS protection header (legacy browsers)
        headers.set("X-XSS-Protection", "1; mode=block");

        // HSTS — 1 year, include subdomains (only effective over real HTTPS)
        headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // Disable caching for authenticated pages
        headers.set("Cache-Control", "no-store, no-cache, must-revalidate, private");
        headers.set("Pragma", "no-cache");
    }

    /**
     * Set minimal security headers for public/static resources (CSS, etc.)
     */
    public static void setStaticResourceHeaders(com.sun.net.httpserver.HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Cache-Control", "public, max-age=3600");
    }

    // ============================================================
    // AUDIT / SECURITY EVENT LOGGING
    // ============================================================

    /**
     * Log a security event to stdout and (when DB is available) to the security_audit_log table.
     *
     * @param eventType  SHORT_CAPS label, e.g. "LOGIN_SUCCESS", "LOGIN_FAILED", "CSRF_VIOLATION"
     * @param actor      Email or user ID of the actor (may be null/unknown)
     * @param details    Human-readable detail string (must NOT contain passwords or tokens)
     * @param ipAddress  Remote IP address (may be null)
     */
    public static void logSecurityEvent(String eventType, String actor, String details, String ipAddress) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        String ip  = ipAddress != null ? ipAddress : "unknown";
        String act = actor     != null ? actor     : "anonymous";

        // Always print to stdout for console monitoring
        System.out.printf("[SECURITY] %s | %-28s | actor=%-30s | ip=%-16s | %s%n",
            ts, eventType, act, ip, details);

        // Persist to database
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 INSERT INTO security_audit_log
                     (event_type, actor, ip_address, details, created_at)
                 VALUES (?, ?, ?, ?, NOW())
             """)) {
            ps.setString(1, sanitizeInput(eventType, 50));
            ps.setString(2, sanitizeInput(act, 150));
            ps.setString(3, sanitizeInput(ip, 50));
            ps.setString(4, sanitizeInput(details, 500));
            ps.executeUpdate();
        } catch (Exception ignored) {
            // DB unavailable — stdout log above is sufficient
        }
    }

    /**
     * Log a security event with request context extracted from an HttpExchange.
     */
    public static void logSecurityEvent(String eventType, String actor, String details,
                                        com.sun.net.httpserver.HttpExchange exchange) {
        String ip = null;
        try {
            // X-Forwarded-For from reverse proxy / WAF
            String xff = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
            ip = (xff != null && !xff.isEmpty())
                    ? xff.split(",")[0].trim()
                    : exchange.getRemoteAddress().getAddress().getHostAddress();
        } catch (Exception ignored) {}
        logSecurityEvent(eventType, actor, details, ip);
    }

    // ============================================================
    // DATABASE SCHEMA — create security_audit_log table
    // ============================================================

    /**
     * Called during application startup to ensure the audit log table exists.
     */
    public static void initializeSecuritySchema() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement()) {

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS security_audit_log (
                    log_id      INT AUTO_INCREMENT PRIMARY KEY,
                    event_type  VARCHAR(50)  NOT NULL,
                    actor       VARCHAR(150),
                    ip_address  VARCHAR(50),
                    details     VARCHAR(500),
                    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_event_type (event_type),
                    INDEX idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS password_reset_tokens (
                    token_id    INT AUTO_INCREMENT PRIMARY KEY,
                    user_id     INT NOT NULL,
                    token_hash  VARCHAR(64) NOT NULL,
                    expires_at  TIMESTAMP NOT NULL,
                    used        TINYINT(1) DEFAULT 0,
                    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE INDEX idx_token_hash (token_hash),
                    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

            System.out.println("✅ Security schema initialized (audit log + reset tokens).");

        } catch (Exception e) {
            System.out.println("⚠️  Security schema init skipped: " + e.getMessage());
        }
    }

    // ============================================================
    // PASSWORD RESET TOKEN  (single-use, expiring, cryptographically random)
    // ============================================================

    /**
     * Generate a password-reset token for the given user ID.
     * Returns the raw token string to be sent via email/displayed.
     * The token is stored hashed in the DB.
     */
    public static String generatePasswordResetToken(int userId) {
        byte[] raw = new byte[32];
        RNG.nextBytes(raw);
        String token = bytesToHex(raw);
        String tokenHash = sha256Hex(token);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
                 VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 30 MINUTE))
                 ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash), expires_at=VALUES(expires_at), used=0
             """)) {
            ps.setInt(1, userId);
            ps.setString(2, tokenHash);
            ps.executeUpdate();
        } catch (Exception e) {
            logSecurityEvent("RESET_TOKEN_ERROR", String.valueOf(userId), e.getMessage(), (String) null);
            return null;
        }
        return token;
    }

    /**
     * Validate a password-reset token. Returns the user ID if valid, -1 otherwise.
     * Marks the token as used on success.
     */
    public static int validatePasswordResetToken(String token) {
        if (token == null || token.isEmpty()) return -1;
        String tokenHash = sha256Hex(token);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT user_id FROM password_reset_tokens
                 WHERE token_hash=? AND used=0 AND expires_at > NOW()
             """)) {
            ps.setString(1, tokenHash);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return -1;
            int userId = rs.getInt("user_id");

            // Mark as used immediately (single-use)
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE password_reset_tokens SET used=1 WHERE token_hash=?")) {
                upd.setString(1, tokenHash);
                upd.executeUpdate();
            }
            return userId;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return bytesToHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ============================================================
    // GENERAL UTILITIES
    // ============================================================

    /**
     * Generate a cryptographically random token (hex-encoded).
     */
    public static String generateSecureToken(int bytes) {
        byte[] raw = new byte[bytes];
        RNG.nextBytes(raw);
        return bytesToHex(raw);
    }
}
