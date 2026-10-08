package com.example.bank.service;

import com.example.bank.config.AccountProperties;
import com.example.bank.domain.AuditAction;
import com.example.bank.domain.ExchangeRate;
import com.example.bank.domain.Money;
import com.example.bank.dto.ExchangeQuoteResponse;
import com.example.bank.dto.ExchangeRateResponse;
import com.example.bank.dto.SetExchangeRateRequest;
import com.example.bank.exception.BusinessRuleException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.repository.ExchangeRateRepository;
import com.example.bank.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Publishes rates and turns an amount in one currency into another.
 *
 * <p>Rates are quoted the way Cambodian banks show them: per 1 unit of the base
 * currency (USD), in the quote currency (KHR). The bank <b>buys</b> base at the
 * lower rate and <b>sells</b> it at the higher one; the gap (the spread) is the
 * bank's margin. Converted amounts are rounded <b>down</b> to the currency's
 * smallest unit, so rounding never gives away money the bank doesn't have.
 */
@Service
public class ExchangeRateService {

    private final ExchangeRateRepository repository;
    private final AccountProperties accountProperties;
    private final AuditService auditService;

    public ExchangeRateService(ExchangeRateRepository repository, AccountProperties accountProperties,
                               AuditService auditService) {
        this.repository = repository;
        this.accountProperties = accountProperties;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> current() {
        return repository.findCurrent().stream().map(ExchangeRateResponse::from).toList();
    }

    @Transactional
    public ExchangeRateResponse publish(AuthenticatedUser admin, SetExchangeRateRequest request) {
        String base = request.baseCurrency().toUpperCase(Locale.ROOT);
        String quote = request.quoteCurrency().toUpperCase(Locale.ROOT);
        if (base.equals(quote)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Base and quote currency must differ");
        }
        for (String currency : List.of(base, quote)) {
            if (!accountProperties.supportedCurrencies().contains(currency)) {
                throw new BusinessRuleException(ErrorCode.UNSUPPORTED_CURRENCY, "Currency " + currency
                        + " is not supported");
            }
        }
        if (request.sellRate().compareTo(request.buyRate()) < 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Sell rate must be at least the buy rate (the bank never sells below what it pays)");
        }
        // The opposite pair (KHR/USD) would make two sources of truth for one market.
        if (repository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByIdDesc(quote, base).isPresent()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "This pair is already quoted as " + quote + "/" + base + "; publish it that way round");
        }
        ExchangeRate saved = repository.save(new ExchangeRate(base, quote, request.buyRate(), request.sellRate(),
                admin.id()));
        auditService.success(AuditAction.EXCHANGE_RATE_SET, admin.id(), "EXCHANGE_RATE", saved.getId(),
                base + "/" + quote + " buy=" + request.buyRate().toPlainString()
                        + " sell=" + request.sellRate().toPlainString());
        return ExchangeRateResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ExchangeQuoteResponse quote(String fromCurrency, String toCurrency, BigDecimal amount) {
        String from = fromCurrency.toUpperCase(Locale.ROOT);
        String to = toCurrency.toUpperCase(Locale.ROOT);
        Quote q = convert(from, to, amount);
        return new ExchangeQuoteResponse(from, to, amount, q.convertedAmount(), q.rate(), q.rateBase(), q.rateQuote());
    }

    /**
     * @return the amount the customer receives in {@code to}, and the published rate used
     */
    Quote convert(String from, String to, BigDecimal amount) {
        if (from.equals(to)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Exchange needs two different currencies; use a transfer instead");
        }
        int digits = Money.fractionDigits(to);
        Optional<ExchangeRate> direct = repository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByIdDesc(from, to);
        if (direct.isPresent()) {
            // Customer sells the base currency: the bank pays its buy rate.
            BigDecimal rate = direct.get().getBuyRate();
            return new Quote(amount.multiply(rate).setScale(digits, RoundingMode.DOWN), rate.stripTrailingZeros(),
                    from, to);
        }
        Optional<ExchangeRate> inverse = repository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByIdDesc(to, from);
        if (inverse.isPresent()) {
            // Customer buys the base currency: the bank charges its sell rate.
            BigDecimal rate = inverse.get().getSellRate();
            return new Quote(amount.divide(rate, digits, RoundingMode.DOWN), rate.stripTrailingZeros(), to, from);
        }
        throw new BusinessRuleException(ErrorCode.EXCHANGE_RATE_UNAVAILABLE,
                "No exchange rate is published between " + from + " and " + to);
    }

    /** rate = rateQuote units per 1 rateBase unit, e.g. 4090 KHR per USD. */
    record Quote(BigDecimal convertedAmount, BigDecimal rate, String rateBase, String rateQuote) {
    }
}
