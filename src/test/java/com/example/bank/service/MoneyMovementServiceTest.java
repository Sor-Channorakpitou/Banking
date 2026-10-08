package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.AccountType;
import com.example.bank.domain.EntryDirection;
import com.example.bank.domain.LedgerEntry;
import com.example.bank.domain.Role;
import com.example.bank.domain.Transaction;
import com.example.bank.domain.TransactionType;
import com.example.bank.domain.User;
import com.example.bank.dto.AmountRequest;
import com.example.bank.dto.TransferRequest;
import com.example.bank.exception.BankException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.exception.ResourceNotFoundException;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.LedgerEntryRepository;
import com.example.bank.repository.TransactionRepository;
import com.example.bank.repository.UserRepository;
import com.example.bank.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The business rules of money movement, tested without a database. Each test
 * builds the state it needs (accounts, balances) through mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // shared setup stubs aren't used by every test
class MoneyMovementServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private BankMetrics metrics;

    private MoneyMovementService service;

    private final User alice = AccountServiceTest.user(1L, Role.CUSTOMER);
    private final User bob = AccountServiceTest.user(2L, Role.CUSTOMER);
    private final AuthenticatedUser aliceCaller = new AuthenticatedUser(1L, "u1@example.com", Role.CUSTOMER);
    private final AuthenticatedUser bobCaller = new AuthenticatedUser(2L, "u2@example.com", Role.CUSTOMER);
    private final AuthenticatedUser adminCaller = new AuthenticatedUser(9L, "admin@example.com", Role.ADMIN);

    private Account aliceUsd;   // id 10
    private Account bobUsd;     // id 20
    private Account bobKhr;     // id 30
    private Account cashUsd;    // id 99

    @BeforeEach
    void setUp() {
        service = new MoneyMovementService(accountRepository, ledgerEntryRepository, transactionRepository,
                userRepository, auditService, metrics,
                new MoneyRules(accountRepository, ledgerEntryRepository));
        aliceUsd = account(10L, alice, "USD");
        bobUsd = account(20L, bob, "USD");
        bobKhr = account(30L, bob, "KHR");
        cashUsd = Account.cash("USD");
        ReflectionTestUtils.setField(cashUsd, "id", 99L);

        for (Account a : List.of(aliceUsd, bobUsd, bobKhr)) {
            when(accountRepository.findByIdForUpdate(a.getId())).thenReturn(Optional.of(a));
            when(accountRepository.findCustomerAccountIdByNumber(a.getAccountNumber())).thenReturn(Optional.of(a.getId()));
        }
        when(accountRepository.findByTypeAndCurrency(AccountType.CASH, "USD")).thenReturn(Optional.of(cashUsd));
        when(userRepository.getReferenceById(1L)).thenReturn(alice);
        when(userRepository.getReferenceById(2L)).thenReturn(bob);
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.balanceOf(any())).thenReturn(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------ happy paths

    @Test
    void depositDebitsCashAndCreditsCustomer() {
        MoneyMovementResult result = service.deposit(aliceCaller, 10L, amount("100.50"), "key-1");

        List<LedgerEntry> entries = savedEntries();
        assertThat(entries).extracting(LedgerEntry::getDirection, LedgerEntry::getAccount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(EntryDirection.DEBIT, cashUsd),
                        org.assertj.core.groups.Tuple.tuple(EntryDirection.CREDIT, aliceUsd));
        assertThat(result.replayed()).isFalse();
        assertThat(result.transaction().amount()).isEqualByComparingTo("100.50");
        assertThat(result.transaction().fromAccountNumber()).isNull(); // cash side is hidden
        assertThat(result.transaction().toAccountNumber()).isEqualTo(aliceUsd.getAccountNumber());
    }

    @Test
    void transferPostsBalancedEntries() {
        when(ledgerEntryRepository.balanceOf(10L)).thenReturn(new BigDecimal("50.0000"));

        service.transfer(aliceCaller, transfer(10L, bobUsd, "50.00"), "key-1");

        List<LedgerEntry> entries = savedEntries();
        BigDecimal debits = sum(entries, EntryDirection.DEBIT);
        BigDecimal credits = sum(entries, EntryDirection.CREDIT);
        assertThat(debits).isEqualByComparingTo(credits).isEqualByComparingTo("50");
        assertThat(entries).filteredOn(e -> e.getDirection() == EntryDirection.DEBIT)
                .extracting(LedgerEntry::getAccount).containsExactly(aliceUsd);
    }

    @Test
    void transferLocksAccountsInAscendingIdOrder() {
        when(ledgerEntryRepository.balanceOf(20L)).thenReturn(new BigDecimal("10"));

        // From the higher ID (20) to the lower ID (10): the lower one must still be locked first.
        service.transfer(bobCaller, transfer(20L, aliceUsd, "5"), "key-1");

        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).findByIdForUpdate(10L);
        order.verify(accountRepository).findByIdForUpdate(20L);
    }

    // ------------------------------------------------------------ rejections

    @Test
    void withdrawRejectsInsufficientFunds() {
        when(ledgerEntryRepository.balanceOf(10L)).thenReturn(new BigDecimal("10.0000"));

        assertRejected(() -> service.withdraw(aliceCaller, 10L, amount("10.01"), "key-1"),
                ErrorCode.INSUFFICIENT_FUNDS);
    }

    @Test
    void transferRejectsInsufficientFunds() {
        when(ledgerEntryRepository.balanceOf(10L)).thenReturn(new BigDecimal("49.99"));

        assertRejected(() -> service.transfer(aliceCaller, transfer(10L, bobUsd, "50"), "key-1"),
                ErrorCode.INSUFFICIENT_FUNDS);
    }

    @Test
    void rejectsFrozenSourceAccount() {
        when(ledgerEntryRepository.balanceOf(10L)).thenReturn(new BigDecimal("100"));
        aliceUsd.freeze();

        assertRejected(() -> service.transfer(aliceCaller, transfer(10L, bobUsd, "5"), "key-1"),
                ErrorCode.ACCOUNT_NOT_ACTIVE);
        assertRejected(() -> service.deposit(aliceCaller, 10L, amount("5"), "key-2"),
                ErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    void rejectsClosedDestinationAccount() {
        when(ledgerEntryRepository.balanceOf(10L)).thenReturn(new BigDecimal("100"));
        bobUsd.close();

        assertRejected(() -> service.transfer(aliceCaller, transfer(10L, bobUsd, "5"), "key-1"),
                ErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    void rejectsCurrencyMismatch() {
        when(ledgerEntryRepository.balanceOf(10L)).thenReturn(new BigDecimal("100"));

        assertRejected(() -> service.transfer(aliceCaller, transfer(10L, bobKhr, "5"), "key-1"),
                ErrorCode.CURRENCY_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01"})
    void rejectsZeroAndNegativeAmounts(String value) {
        assertRejected(() -> service.deposit(aliceCaller, 10L, amount(value), "key-1"), ErrorCode.INVALID_AMOUNT);
        assertRejected(() -> service.withdraw(aliceCaller, 10L, amount(value), "key-2"), ErrorCode.INVALID_AMOUNT);
        assertRejected(() -> service.transfer(aliceCaller, transfer(10L, bobUsd, value), "key-3"),
                ErrorCode.INVALID_AMOUNT);
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyAllows() {
        assertRejected(() -> service.deposit(aliceCaller, 10L, amount("1.001"), "key-1"), ErrorCode.INVALID_AMOUNT);
    }

    @Test
    void rejectsTransferToTheSameAccount() {
        assertRejected(() -> service.transfer(aliceCaller, transfer(10L, aliceUsd, "5"), "key-1"),
                ErrorCode.SAME_ACCOUNT_TRANSFER);
    }

    @Test
    void rejectsAccountNumberWithBadCheckDigit() {
        String good = bobUsd.getAccountNumber();
        String bad = good.substring(0, good.length() - 1) + ((good.charAt(good.length() - 1) - '0' + 1) % 10);

        assertRejected(() -> service.transfer(aliceCaller,
                new TransferRequest(10L, bad, new BigDecimal("5"), null), "key-1"), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void customersCannotMoveMoneyOutOfOtherPeoplesAccounts() {
        assertThatThrownBy(() -> service.withdraw(bobCaller, 10L, amount("5"), "key-1"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.transfer(bobCaller, transfer(10L, bobUsd, "5"), "key-2"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertRejected(() -> service.withdraw(adminCaller, 10L, amount("5"), "key-3"), ErrorCode.ACCESS_DENIED);
    }

    // ------------------------------------------------------------ idempotency

    @Test
    void repeatedKeyReturnsOriginalResultWithoutPostingAgain() {
        String fingerprint = MoneyRules.fingerprint(TransactionType.DEPOSIT, 10L,
                new BigDecimal("100.5"), null);
        Transaction original = new Transaction(TransactionType.DEPOSIT, new BigDecimal("100.5000"), "USD",
                "key-1", null, alice, fingerprint);
        ReflectionTestUtils.setField(original, "id", 500L);
        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(original));

        // Same amount written differently (100.50 vs 100.5) is the same request.
        MoneyMovementResult result = service.deposit(aliceCaller, 10L, amount("100.50"), "key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.transaction().id()).isEqualTo(500L);
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(ledgerEntryRepository, never()).saveAll(anyList());
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() {
        Transaction original = new Transaction(TransactionType.DEPOSIT, new BigDecimal("100"), "USD",
                "key-1", null, alice, MoneyRules.fingerprint(TransactionType.DEPOSIT, 10L,
                new BigDecimal("100"), null));
        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(original));

        assertRejected(() -> service.deposit(aliceCaller, 10L, amount("999"), "key-1"),
                ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void keyBelongingToAnotherUserIsRejected() {
        Transaction bobsTx = new Transaction(TransactionType.DEPOSIT, new BigDecimal("100"), "USD",
                "key-1", null, bob, "whatever");
        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(bobsTx));

        assertRejected(() -> service.deposit(aliceCaller, 10L, amount("100"), "key-1"),
                ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    // ------------------------------------------------------------ helpers

    private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BankException.class).extracting("errorCode").isEqualTo(code);
    }

    @SuppressWarnings("unchecked")
    private List<LedgerEntry> savedEntries() {
        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static BigDecimal sum(List<LedgerEntry> entries, EntryDirection direction) {
        return entries.stream().filter(e -> e.getDirection() == direction)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static AmountRequest amount(String value) {
        return new AmountRequest(new BigDecimal(value), null);
    }

    private static TransferRequest transfer(Long fromId, Account to, String value) {
        return new TransferRequest(fromId, to.getAccountNumber(), new BigDecimal(value), null);
    }

    /** Account numbers carry a valid Luhn digit, like real ones. */
    private static Account account(Long id, User owner, String currency) {
        String base = "10000000" + String.format("%02d", id);
        Account account = new Account(base + AccountNumberGenerator.luhnCheckDigit(base), owner, currency);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
