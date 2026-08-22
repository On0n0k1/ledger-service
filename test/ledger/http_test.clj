(ns ledger.http-test
  (:require [clojure.test :refer [deftest testing is]]
            [ring.mock.request :as mock]
            [muuntaja.core :as muuntaja]
            [ledger.http :as http]
            [ledger.adapters.in-memory :as in-memory]))

(defn- test-app
  []
  (http/app (in-memory/new-store) (in-memory/new-publisher)))

(defn- json-body
  [response]
  (muuntaja/decode-response-body response))

(deftest health-test
  (let [response ((test-app) (mock/request :get "/health"))]
    (is (= 200 (:status response)))))

(deftest deposit-and-balance-test
  (let [app (test-app)]
    (testing "depositing into a new account creates it"
      (let [response (app (-> (mock/request :post "/accounts/abc/deposit")
                               (mock/json-body {:amount 100})))]
        (is (= 200 (:status response)))
        (is (= {:balance 100} (json-body response)))))

    (testing "balance reflects the deposit"
      (let [response (app (mock/request :get "/accounts/abc/balance"))]
        (is (= 200 (:status response)))
        (is (= {:balance 100} (json-body response)))))

    (testing "a second deposit accumulates"
      (let [response (app (-> (mock/request :post "/accounts/abc/deposit")
                               (mock/json-body {:amount 25})))]
        (is (= {:balance 125} (json-body response)))))))

(deftest deposit-invalid-amount-test
  (let [response ((test-app) (-> (mock/request :post "/accounts/abc/deposit")
                                  (mock/json-body {:amount 0})))]
    (is (= 400 (:status response)))
    (is (= {:error "invalid-amount"} (json-body response)))))

(deftest withdraw-test
  (let [app (test-app)]
    (app (-> (mock/request :post "/accounts/abc/deposit") (mock/json-body {:amount 100})))

    (testing "withdrawing within the balance succeeds"
      (let [response (app (-> (mock/request :post "/accounts/abc/withdraw")
                               (mock/json-body {:amount 40})))]
        (is (= 200 (:status response)))
        (is (= {:balance 60} (json-body response)))))

    (testing "withdrawing more than the balance is rejected"
      (let [response (app (-> (mock/request :post "/accounts/abc/withdraw")
                               (mock/json-body {:amount 1000})))]
        (is (= 400 (:status response)))
        (is (= {:error "insufficient-funds"} (json-body response)))))))

(deftest balance-not-found-test
  (let [response ((test-app) (mock/request :get "/accounts/nonexistent/balance"))]
    (is (= 404 (:status response)))))

(deftest withdraw-not-found-test
  (let [response ((test-app) (-> (mock/request :post "/accounts/nonexistent/withdraw")
                                  (mock/json-body {:amount 10})))]
    (is (= 404 (:status response)))))
