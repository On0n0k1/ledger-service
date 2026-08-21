(ns ledger.main
  (:require [ledger.http :as http]
            [ring.adapter.jetty :as jetty])
  (:gen-class))

(defn -main
  [& _args]
  (jetty/run-jetty http/app {:port 3000 :join? true}))
