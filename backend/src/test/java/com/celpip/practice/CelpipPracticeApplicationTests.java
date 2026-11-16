package com.celpip.practice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.test.annotation.DirtiesContext
@SpringBootTest
class CelpipPracticeApplicationTests {
    @org.junit.jupiter.api.io.TempDir
    static java.nio.file.Path data;
    @org.springframework.test.context.DynamicPropertySource
    static void properties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("celpip.data-dir", () -> { try { return data.toRealPath().toString(); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); } });
    }


	@Test
	void contextLoads() {
	}

}
