package com.example.bank.concurrency;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Runs the concurrency scenario on H2 (always available). */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentTransferH2Test extends AbstractConcurrentTransferTest {
}
