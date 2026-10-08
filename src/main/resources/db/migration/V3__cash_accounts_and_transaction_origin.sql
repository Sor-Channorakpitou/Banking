-- V3: what money movement needs.
--
-- 1) Account types. Double-entry needs a second account for every movement.
--    A deposit is DEBIT the bank's cash account / CREDIT the customer, and a
--    withdrawal is the reverse. CASH accounts belong to the bank, not a user, so
--    owner_id becomes optional, but only for non-customer accounts.
ALTER TABLE accounts ADD COLUMN type VARCHAR(20) DEFAULT 'CUSTOMER' NOT NULL;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_type CHECK (type IN ('CUSTOMER', 'CASH'));
ALTER TABLE accounts ALTER COLUMN owner_id DROP NOT NULL;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_owner_matches_type
    CHECK ((type = 'CUSTOMER' AND owner_id IS NOT NULL) OR (type <> 'CUSTOMER' AND owner_id IS NULL));

-- 2) Who started each transaction, and a hash of the request. Together they make
--    idempotency safe: a repeated key is only replayed for the same user and the
--    same request body. No transactions could exist before this version, so the
--    NOT NULL columns can be added without a default.
ALTER TABLE transactions ADD COLUMN initiated_by_id BIGINT NOT NULL;
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_initiated_by
    FOREIGN KEY (initiated_by_id) REFERENCES users (id);
ALTER TABLE transactions ADD COLUMN request_fingerprint VARCHAR(64) NOT NULL;
