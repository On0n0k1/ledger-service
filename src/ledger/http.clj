(ns ledger.http
  (:require [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja-mw]
            [muuntaja.core :as muuntaja]
            [ledger.domain.ledger :as domain]
            [ledger.ports.ledger-store :as store]
            [ledger.ports.event-publisher :as pub]))

(defn health-handler
  [_request]
  {:status 200 :body {:status "ok"}})

(defn- account-id
  [request]
  (get-in request [:path-params :id]))

(defn- amount
  [request]
  (bigdec (get-in request [:body-params :amount])))

(defn- publish-transaction
  [publisher account-id kind amount]
  (pub/publish-event publisher {:type :transaction-recorded
                                 :account-id account-id
                                 :kind kind
                                 :amount amount}))

(defn balance-handler
  [ledger-store]
  (fn [request]
    (if-let [account (store/get-account ledger-store (account-id request))]
      {:status 200 :body {:balance (domain/balance account)}}
      {:status 404 :body {:error "account-not-found"}})))

(defn deposit-handler
  [ledger-store publisher]
  (fn [request]
    (let [id (account-id request)
          amt (amount request)
          existing (store/get-account ledger-store id)
          {:keys [ok account error]} (domain/deposit (or existing (domain/new-account id)) amt)]
      (if ok
        (do
          (store/save-account ledger-store account)
          (publish-transaction publisher id :deposit amt)
          {:status 200 :body {:balance (domain/balance account)}})
        {:status 400 :body {:error (name error)}}))))

(defn withdraw-handler
  [ledger-store publisher]
  (fn [request]
    (let [id (account-id request)
          amt (amount request)]
      (if-let [existing (store/get-account ledger-store id)]
        (let [{:keys [ok account error]} (domain/withdraw existing amt)]
          (if ok
            (do
              (store/save-account ledger-store account)
              (publish-transaction publisher id :withdraw amt)
              {:status 200 :body {:balance (domain/balance account)}})
            {:status 400 :body {:error (name error)}}))
        {:status 404 :body {:error "account-not-found"}}))))

(defn app
  [ledger-store publisher]
  (ring/ring-handler
    (ring/router
      [["/health" {:get {:handler health-handler}}]
       ["/accounts/:id/balance" {:get {:handler (balance-handler ledger-store)}}]
       ["/accounts/:id/deposit" {:post {:handler (deposit-handler ledger-store publisher)}}]
       ["/accounts/:id/withdraw" {:post {:handler (withdraw-handler ledger-store publisher)}}]]
      {:data {:muuntaja muuntaja/instance
              :middleware [muuntaja-mw/format-middleware]}})
    (ring/create-default-handler)))
