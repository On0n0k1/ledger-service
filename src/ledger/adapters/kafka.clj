(ns ledger.adapters.kafka
  "EventPublisher adapter backed by the Kafka producer API. Works against
  real Kafka or a protocol-compatible broker like Redpanda — nothing here
  is Kafka-specific beyond the wire protocol the client speaks."
  (:require [ledger.ports.event-publisher :as pub]
            [clojure.data.json :as json])
  (:import [java.util Properties]
           [org.apache.kafka.clients.producer KafkaProducer ProducerRecord]))

(def topic "transaction-recorded")

(defn producer
  [{:keys [bootstrap-servers]}]
  (let [props (doto (Properties.)
                (.put "bootstrap.servers" bootstrap-servers)
                (.put "key.serializer" "org.apache.kafka.common.serialization.StringSerializer")
                (.put "value.serializer" "org.apache.kafka.common.serialization.StringSerializer"))]
    (KafkaProducer. props)))

(defn new-publisher
  [kafka-producer]
  (reify pub/EventPublisher
    (publish-event [_ event]
      (.send kafka-producer
        (ProducerRecord. topic (:account-id event) (json/write-str event)))
      nil)))
