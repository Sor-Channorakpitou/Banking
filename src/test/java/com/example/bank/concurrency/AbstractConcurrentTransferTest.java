package com.example.bank.concurrency;

import com.example.bank.domain.Role;
import com.example.bank.domain.User;
import com.example.bank.dto.AmountRequest;
import com.example.bank.dto.OpenAccountRequest;
import com.example.bank.dto.TransferRequest;
import com.example.bank.exception.BusinessRuleException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.repository.LedgerEntryRepository;
import com.example.bank.repository.UserRepository;
import com.example.bank.security.AuthenticatedUser;
import com.example.bank.service.AccountService;
import com.example.bank.service.MoneyMovementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Many threads transfer money back and forth between a few accounts at the same
 * time, in both directions, which is the classic recipe for deadlocks and lost
 * updates. Afterwards:
 * <ul>
 *   <li>no unexpected errors (deadlocks, lock timeouts) occurred;</li>
 *   <li>the total across the accounts is unchanged, and none went negative;</li>
 *   <li>each balance equals what the successful transfers say it should be;</li>
 *   <li>every transaction in the ledger is balanced (debits == credits).</li>
 * </ul>
 * Not @Transactional on purpose: each service call must commit for real, exactly as
 * in production. Subclasses choose the database.
 */
abstract class AbstractConcurrentTransferTest {

    private static final int ACCOUNTS = 4;
    private static final int THREADS = 8;
    private static final int TRANSFERS_PER_THREAD = 40;
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private MoneyMovementService moneyMovementService;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record Party(AuthenticatedUser user, long accountId, String accountNumber) {
    }

    @Test
    void parallelTransfersKeepTotalMoneyUnchanged() throws Exception {
        List<Party> parties = createFundedAccounts();
        Map<Long, BigDecimal> expected = new ConcurrentHashMap<>();
        parties.forEach(p -> expected.put(p.accountId(), INITIAL_BALANCE));

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficientFunds = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                start.await(); // release all threads at once for maximum contention
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < TRANSFERS_PER_THREAD; i++) {
                    Party from = parties.get(random.nextInt(ACCOUNTS));
                    Party to = parties.get(random.nextInt(ACCOUNTS));
                    if (from == to) {
                        continue;
                    }
                    BigDecimal amount = BigDecimal.valueOf(random.nextLong(1, 30_000), 2); // 0.01 - 299.99
                    try {
                        moneyMovementService.transfer(from.user(),
                                new TransferRequest(from.accountId(), to.accountNumber(), amount, null),
                                UUID.randomUUID().toString());
                        expected.merge(from.accountId(), amount.negate(), BigDecimal::add);
                        expected.merge(to.accountId(), amount, BigDecimal::add);
                        succeeded.incrementAndGet();
                    } catch (BusinessRuleException e) {
                        if (e.getErrorCode() == ErrorCode.INSUFFICIENT_FUNDS) {
                            insufficientFunds.incrementAndGet(); // a correct rejection, not a failure
                        } else {
                            unexpected.add(e);
                        }
                    } catch (Throwable e) {
                        unexpected.add(e);
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(2, TimeUnit.MINUTES)).as("transfers finished in time").isTrue();

        assertThat(unexpected).as("unexpected errors (deadlocks, timeouts, ...)").isEmpty();
        assertThat(succeeded.get()).as("some transfers succeeded").isPositive();

        BigDecimal total = BigDecimal.ZERO;
        for (Party p : parties) {
            BigDecimal balance = ledgerEntryRepository.balanceOf(p.accountId());
            assertThat(balance).as("balance of account %d", p.accountId())
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO)
                    .isEqualByComparingTo(expected.get(p.accountId()));
            total = total.add(balance);
        }
        assertThat(total).isEqualByComparingTo(INITIAL_BALANCE.multiply(BigDecimal.valueOf(ACCOUNTS)));

        Integer unbalanced = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT transaction_id FROM ledger_entries
                    GROUP BY transaction_id
                    HAVING SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END) <> 0
                ) unbalanced
                """, Integer.class);
        assertThat(unbalanced).as("transactions whose debits != credits").isZero();

        System.out.printf("%s: %d transfers succeeded, %d rejected for insufficient funds%n",
                getClass().getSimpleName(), succeeded.get(), insufficientFunds.get());
    }

    private List<Party> createFundedAccounts() {
        List<Party> parties = new ArrayList<>();
        for (int i = 0; i < ACCOUNTS; i++) {
            User user = userRepository.save(new User("Concurrent " + i,
                    "concurrent-" + UUID.randomUUID() + "@example.com", "not-a-real-hash", Role.CUSTOMER));
            AuthenticatedUser caller = new AuthenticatedUser(user.getId(), user.getEmail(), Role.CUSTOMER);
            var account = accountService.open(caller, new OpenAccountRequest("USD"));
            moneyMovementService.deposit(caller, account.id(), new AmountRequest(INITIAL_BALANCE, "seed"),
                    UUID.randomUUID().toString());
            parties.add(new Party(caller, account.id(), account.accountNumber()));
        }
        return parties;
    }
}
