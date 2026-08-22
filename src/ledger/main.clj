(ns ledger.main
  (:require [ledger.http :as http]
            [ledger.adapters.in-memory :as in-memory]
            [ledger.adapters.dynamodb :as dynamodb]
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

(defn -main
  [& _args]
  (jetty/run-jetty (http/app (ledger-store) (in-memory/new-publisher))
                    {:port 3000 :join? true}))
