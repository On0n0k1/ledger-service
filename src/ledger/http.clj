(ns ledger.http
  (:require [reitit.ring :as ring]
            [ring.util.response :as response]))

(defn health-handler
  [_request]
  (response/response {:status "ok"}))

(def app
  (ring/ring-handler
    (ring/router
      [["/health" {:get {:handler health-handler}}]])
    (ring/create-default-handler)))
