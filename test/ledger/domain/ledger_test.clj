(ns ledger.domain.ledger-test
  (:require [clojure.test :refer [deftest testing is]]
            [ledger.domain.ledger :as ledger]))

(deftest new-account-test
  (testing "starts with a zero balance"
    (is (= 0M (ledger/balance (ledger/new-account "acc-1"))))))

(deftest deposit-test
  (testing "increases the balance by the deposited amount"
    (let [account (ledger/new-account "acc-1")
          {:keys [ok account]} (ledger/deposit account 50M)]
      (is (true? ok))
      (is (= 50M (ledger/balance account)))))

  (testing "accumulates across multiple deposits"
    (let [account (ledger/new-account "acc-1")
          {:keys [account]} (ledger/deposit account 50M)
          {:keys [account]} (ledger/deposit account 25M)]
      (is (= 75M (ledger/balance account)))))

  (testing "rejects a zero amount"
    (let [{:keys [ok error]} (ledger/deposit (ledger/new-account "acc-1") 0M)]
      (is (false? ok))
      (is (= :invalid-amount error))))

  (testing "rejects a negative amount"
    (let [{:keys [ok error]} (ledger/deposit (ledger/new-account "acc-1") -10M)]
      (is (false? ok))
      (is (= :invalid-amount error)))))

(deftest withdraw-test
  (testing "decreases the balance by the withdrawn amount"
    (let [funded (:account (ledger/deposit (ledger/new-account "acc-1") 100M))
          {:keys [ok account]} (ledger/withdraw funded 40M)]
      (is (true? ok))
      (is (= 60M (ledger/balance account)))))

  (testing "allows withdrawing the entire balance"
    (let [funded (:account (ledger/deposit (ledger/new-account "acc-1") 100M))
          {:keys [ok account]} (ledger/withdraw funded 100M)]
      (is (true? ok))
      (is (= 0M (ledger/balance account)))))

  (testing "rejects withdrawing more than the balance"
    (let [funded (:account (ledger/deposit (ledger/new-account "acc-1") 100M))
          {:keys [ok error]} (ledger/withdraw funded 100.01M)]
      (is (false? ok))
      (is (= :insufficient-funds error))))

  (testing "rejects a zero amount"
    (let [{:keys [ok error]} (ledger/withdraw (ledger/new-account "acc-1") 0M)]
      (is (false? ok))
      (is (= :invalid-amount error))))

  (testing "rejects a negative amount"
    (let [{:keys [ok error]} (ledger/withdraw (ledger/new-account "acc-1") -10M)]
      (is (false? ok))
      (is (= :invalid-amount error)))))
