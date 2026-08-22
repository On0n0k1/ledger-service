(ns ledger.adapters.dynamodb-test
  "Requires docker-compose up (or at least DynamoDB Local on
  localhost:8000) with AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY set
  to any non-empty value."
  (:require [clojure.test :refer [deftest testing is]]
            [ledger.adapters.dynamodb :as dynamodb]
            [ledger.domain.ledger :as domain]
            [ledger.ports.ledger-store :as store]))

(defn- test-client
  []
  (dynamodb/client {:region "us-east-1"
                     :endpoint-override {:protocol :http
                                          :hostname "localhost"
                                          :port 8000}}))

(deftest ^:integration save-and-get-account-test
  (let [client (test-client)]
    (dynamodb/ensure-table! client)
    (let [ledger-store (dynamodb/new-store client)
          id (str "test-" (random-uuid))
          account (:account (domain/deposit (domain/new-account id) 42M))]
      (testing "an unknown account isn't found"
        (is (nil? (store/get-account ledger-store id))))

      (testing "a saved account round-trips"
        (store/save-account ledger-store account)
        (is (= account (store/get-account ledger-store id))))

      (testing "saving again overwrites rather than duplicates"
        (let [updated (:account (domain/deposit account 8M))]
          (store/save-account ledger-store updated)
          (is (= 50M (domain/balance (store/get-account ledger-store id)))))))))
