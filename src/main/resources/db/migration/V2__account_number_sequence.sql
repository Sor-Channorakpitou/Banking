-- V2: source of customer account numbers.
-- A sequence never hands out the same value twice, even under heavy concurrency,
-- so there's no "generate random number, check, retry" loop. The service appends
-- a Luhn check digit so a mistyped number is caught before any lookup.
CREATE SEQUENCE account_number_seq START WITH 1000000000 INCREMENT BY 1;
