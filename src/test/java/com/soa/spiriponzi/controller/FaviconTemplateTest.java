package com.soa.spiriponzi.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FaviconTemplateTest {

    @Test
    void everyPageDeclaresTheContextAwareIcoFavicon() throws IOException {
        Path templates = Path.of("src/main/resources/templates");
        try (var files = Files.walk(templates)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".html"))
                    .filter(path -> !path.startsWith(templates.resolve("fragments")))
                    .toList()) {
                String html = Files.readString(file);
                assertTrue(html.contains("<link rel=\"icon\" type=\"image/x-icon\" th:href=\"@{/img/favicon.ico}\">"),
                        () -> "Missing or incorrect favicon in " + file);
            }
        }
    }
}
