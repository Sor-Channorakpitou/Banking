package com.example.bank.service;

import com.example.bank.config.AccountProperties;
import com.example.bank.domain.Account;
import com.example.bank.domain.AccountStatus;
import com.example.bank.domain.Role;
import com.example.bank.domain.User;
import com.example.bank.dto.OpenAccountRequest;
import com.example.bank.exception.BankException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.exception.ResourceNotFoundException;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.LedgerEntryRepository;
import com.example.bank.repository.UserRepository;
import com.example.bank.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountNumberGenerator accountNumberGenerator;
    @Mock
    private AuditService auditService;

    private AccountService service;

    private final User alice = user(1L, Role.CUSTOMER);
    private final AuthenticatedUser aliceCaller = new AuthenticatedUser(1L, "u1@example.com", Role.CUSTOMER);
    private final AuthenticatedUser bobCaller = new AuthenticatedUser(2L, "u2@example.com", Role.CUSTOMER);
    private final AuthenticatedUser adminCaller = new AuthenticatedUser(9L, "admin@example.com", Role.ADMIN);

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, ledgerEntryRepository, userRepository,
                accountNumberGenerator, new AccountProperties(Set.of("USD", "KHR")), auditService);
    }

    @Test
    void openRejectsUnsupportedCurrency() {
        assertThatThrownBy(() -> service.open(aliceCaller, new OpenAccountRequest("EUR")))
                .isInstanceOf(BankException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNSUPPORTED_CURRENCY);
    }

    @Test
    void ownerAndAdminCanReadAccountButOthersGetNotFound() {
        Account account = account(10L, alice);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));

        assertThat(service.findAccessible(aliceCaller, 10L)).isSameAs(account);
        assertThat(service.findAccessible(adminCaller, 10L)).isSameAs(account);
        assertThatThrownBy(() -> service.findAccessible(bobCaller, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void closeRequiresZeroBalance() {
        Account account = account(10L, alice);
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(account));
        when(ledgerEntryRepository.balanceOf(10L)).thenReturn(new BigDecimal("5.0000"));

        assertThatThrownBy(() -> service.close(adminCaller, 10L))
                .isInstanceOf(BankException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_STATE_CONFLICT);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void closeWithZeroBalanceSucceeds() {
        Account account = account(10L, alice);
        when(accountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(account));
        when(ledgerEntryRepository.balanceOf(10L)).thenReturn(BigDecimal.ZERO);

        assertThat(service.close(adminCaller, 10L).status()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void statusTransitionsFollowTheRules() {
        Account account = account(10L, alice);

        account.freeze();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.FROZEN);
        assertThatThrownBy(account::freeze).isInstanceOf(BankException.class);

        account.unfreeze();
        account.close();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThatThrownBy(account::unfreeze).isInstanceOf(BankException.class);
        assertThatThrownBy(account::close).isInstanceOf(BankException.class);
    }

    @Test
    void generatedAccountNumbersPassTheLuhnCheck() {
        assertThat(AccountNumberGenerator.luhnCheckDigit("7992739871")).isEqualTo(3); // textbook example
        assertThat(AccountNumberGenerator.isValid("79927398713")).isTrue();
        assertThat(AccountNumberGenerator.isValid("79927398714")).isFalse();
    }

    static User user(Long id, Role role) {
        User user = new User("User " + id, "u" + id + "@example.com", "hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    static Account account(Long id, User owner) {
        Account account = new Account("ACC" + id, owner, "USD");
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
