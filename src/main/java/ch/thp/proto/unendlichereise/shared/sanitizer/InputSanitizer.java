package ch.thp.proto.unendlichereise.shared.sanitizer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@Slf4j
public class InputSanitizer {

    private static final Pattern SUSPICIOUS_PATTERN = Pattern.compile(
            "(?i)(ignore|forget|disregard|you are|act as|pretend|system prompt)"
    );

    public String sanitize(String input) {
        if (input == null) return null;

        String sanitized = input.replaceAll("[\\p{Cntrl}]", "");

        if (SUSPICIOUS_PATTERN.matcher(sanitized).find()) {
            log.warn("Suspicious input pattern detected: {}",
                    sanitized.substring(0, Math.min(50, sanitized.length())));
        }

        return sanitized.trim();
    }

    public String truncate(String input, int maxLength) {
        if (input == null) return null;
        return input.length() > maxLength ? input.substring(0, maxLength) : input;
    }

    public String escapeXml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
