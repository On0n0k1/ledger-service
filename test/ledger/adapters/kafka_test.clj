(ns ledger.adapters.kafka-test
  "Requires docker-compose up. Connects via Redpanda's external listener
  (localhost:19092) rather than the internal one ledger-service uses
  (redpanda:9092, only resolvable inside the compose network)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.data.json :as json]
            [ledger.adapters.kafka :as kafka]
            [ledger.ports.event-publisher :as pub])
  (:import [java.time Duration]
           [java.util Properties UUID]
           [org.apache.kafka.clients.consumer ConsumerConfig KafkaConsumer]
           [org.apache.kafka.common.serialization StringDeserializer]))

(defn- test-consumer
  []
  (let [props (doto (Properties.)
                (.put ConsumerConfig/BOOTSTRAP_SERVERS_CONFIG "localhost:19092")
                (.put ConsumerConfig/GROUP_ID_CONFIG (str "test-" (UUID/randomUUID)))
                (.put ConsumerConfig/AUTO_OFFSET_RESET_CONFIG "earliest")
                (.put ConsumerConfig/KEY_DESERIALIZER_CLASS_CONFIG (.getName StringDeserializer))
                (.put ConsumerConfig/VALUE_DESERIALIZER_CLASS_CONFIG (.getName StringDeserializer)))]
    (KafkaConsumer. props)))

(defn- poll-for
  "Poll until a record matching account-id shows up, or the deadline passes."
  [consumer account-id deadline-ms]
  (when (< (System/currentTimeMillis) deadline-ms)
    (let [records (.poll consumer (Duration/ofMillis 500))
          matching (->> records
                        (map #(json/read-str (.value %) :key-fn keyword))
                        (filter #(= account-id (:account-id %))))]
      (or (first matching) (recur consumer account-id deadline-ms)))))

(deftest ^:integration publish-event-test
  (let [account-id (str "test-" (random-uuid))
        consumer (test-consumer)]
    (.subscribe consumer [kafka/topic])
    ;; Give the consumer group a moment to get its partition assignment
    ;; before producing, or the first poll can miss the record entirely.
    (.poll consumer (Duration/ofMillis 1000))

    (let [publisher (kafka/new-publisher (kafka/producer {:bootstrap-servers "localhost:19092"}))]
      (pub/publish-event publisher {:type :transaction-recorded
                                     :account-id account-id
                                     :kind :deposit
                                     :amount 100}))

    (let [event (poll-for consumer account-id (+ (System/currentTimeMillis) 10000))]
      (is (some? event))
      (is (= "deposit" (:kind event)))
      (is (= 100 (:amount event))))))
