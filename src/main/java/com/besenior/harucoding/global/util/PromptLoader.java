package com.besenior.harucoding.global.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class PromptLoader {

    @Value("${prompt.lang:en}")
    private String lang;

    private static final String SYSTEM_DELIMITER = "## SYSTEM";
    private static final String USER_DELIMITER = "## USER";

    public String getSystemPrompt(String promptType) {
        String content = loadFile(promptType);
        return extractSection(content, SYSTEM_DELIMITER, USER_DELIMITER);
    }

    public String getUserPrompt(String promptType, Map<String, String> vars) {
        String content = loadFile(promptType);
        String template = extractSection(content, USER_DELIMITER, null);
        return replacePlaceholders(template, vars);
    }

    private String loadFile(String promptType) {
        String path = "prompts/" + promptType + "_" + lang + ".md";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            try (InputStream is = resource.getInputStream()) {
                return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("프롬프트 파일 로드 실패: {}, 기본값(en) 사용", path);
            try {
                ClassPathResource fallback = new ClassPathResource("prompts/" + promptType + "_en.md");
                try (InputStream is = fallback.getInputStream()) {
                    return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                }
            } catch (IOException ex) {
                throw new IllegalStateException("프롬프트 파일을 찾을 수 없습니다: " + path, ex);
            }
        }
    }

    private String extractSection(String content, String startDelimiter, String endDelimiter) {
        int start = content.indexOf(startDelimiter);
        if (start == -1) return content;
        start += startDelimiter.length();

        if (endDelimiter != null) {
            int end = content.indexOf(endDelimiter, start);
            if (end != -1) return content.substring(start, end).trim();
        }
        return content.substring(start).trim();
    }

    private String replacePlaceholders(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    public static Map<String, String> vars(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("vars() must receive key-value pairs");
        }
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
