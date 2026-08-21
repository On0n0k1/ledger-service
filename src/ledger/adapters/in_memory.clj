(ns ledger.adapters.in-memory
  "In-memory adapters for the LedgerStore and EventPublisher ports, backed
  by atoms. Useful for getting the HTTP layer working end-to-end before
  real infra (DynamoDB, Kafka) is wired in."
  (:require [ledger.ports.ledger-store :as store]
            [ledger.ports.event-publisher :as pub]))

(defn new-store
  []
  (let [accounts (atom {})]
    (reify store/LedgerStore
      (get-account [_ id]
        (get @accounts id))
      (save-account [_ account]
        (swap! accounts assoc (:id account) account)
        account))))

(defn new-publisher
  []
  (let [events (atom [])]
    (reify pub/EventPublisher
      (publish-event [_ event]
        (swap! events conj event)
        nil))))
