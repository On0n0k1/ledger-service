(ns ledger.main
  (:require [ledger.http :as http]
            [ledger.adapters.in-memory :as in-memory]
            [ledger.adapters.dynamodb :as dynamodb]
            [ledger.adapters.kafka :as kafka]
            [ring.adapter.jetty :as jetty])
  (:gen-class))

(defn- dynamodb-store
  []
  (let [client (dynamodb/client
                 {:region (or (System/getenv "AWS_REGION") "us-east-1")
                  :endpoint-override
                  {:protocol :http
                   :hostname (or (System/getenv "DYNAMODB_HOSTNAME") "localhost")
                   :port (Integer/parseInt (or (System/getenv "DYNAMODB_PORT") "8000"))}})]
    (dynamodb/ensure-table! client)
    (dynamodb/new-store client)))

(defn- ledger-store
  []
  (if (= "dynamodb" (System/getenv "LEDGER_STORE"))
    (dynamodb-store)
    (in-memory/new-store)))

(defn- kafka-publisher
  []
  (kafka/new-publisher
    (kafka/producer {:bootstrap-servers (or (System/getenv "KAFKA_BOOTSTRAP_SERVERS")
                                             "localhost:9092")})))

(defn- event-publisher
  []
  (if (= "kafka" (System/getenv "LEDGER_EVENT_PUBLISHER"))
    (kafka-publisher)
    (in-memory/new-publisher)))

(defn -main
  [& _args]
  (jetty/run-jetty (http/app (ledger-store) (event-publisher))
                    {:port 3000 :join? true}))
