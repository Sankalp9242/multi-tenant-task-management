package com.techpulse.taskmanagement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.jwt.secret=test-only-signing-key-with-at-least-32-bytes")
class MultiTenantTaskManagementApplicationTests {

	@Test
	void contextLoads() {
	}

}
