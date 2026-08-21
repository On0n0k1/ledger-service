(ns ledger.main
  (:require [ledger.http :as http]
            [ledger.adapters.in-memory :as in-memory]
            [ring.adapter.jetty :as jetty])
  (:gen-class))

(defn -main
  [& _args]
  (let [ledger-store (in-memory/new-store)
        publisher (in-memory/new-publisher)]
    (jetty/run-jetty (http/app ledger-store publisher) {:port 3000 :join? true})))
