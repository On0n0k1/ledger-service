(ns ledger.adapters.dynamodb
  "LedgerStore adapter backed by DynamoDB (or DynamoDB Local for dev).
  Same protocol as the in-memory adapter — domain code never sees the
  difference."
  (:require [cognitect.aws.client.api :as aws]
            [ledger.ports.ledger-store :as store]))

(def table-name "ledger-accounts")

(defn client
  "Build a DynamoDB client. `endpoint-override` (e.g.
  {:protocol :http :hostname \"localhost\" :port 8000}) points it at
  DynamoDB Local; omit it to hit real AWS DynamoDB."
  [{:keys [region endpoint-override]}]
  (aws/client (cond-> {:api :dynamodb}
                region (assoc :region region)
                endpoint-override (assoc :endpoint-override endpoint-override))))

(defn- invoke!
  [aws-client op request]
  (let [response (aws/invoke aws-client {:op op :request request})]
    (if (:cognitect.anomalies/category response)
      (throw (ex-info (str "DynamoDB " (name op) " failed") response))
      response)))

(defn ensure-table!
  "Creates the ledger-accounts table if it doesn't already exist.
  Dev/test convenience only — a real deployment provisions tables via
  infrastructure-as-code, not application code."
  [aws-client]
  (let [{:keys [TableNames]} (invoke! aws-client :ListTables {})]
    (when-not (some #{table-name} TableNames)
      (invoke! aws-client :CreateTable
        {:TableName table-name
         :AttributeDefinitions [{:AttributeName "id" :AttributeType "S"}]
         :KeySchema [{:AttributeName "id" :KeyType "HASH"}]
         :BillingMode "PAY_PER_REQUEST"}))))

(defn- account->item
  [{:keys [id balance]}]
  {:id {:S id}
   :balance {:N (str balance)}})

(defn- item->account
  [item]
  (when (seq item)
    {:id (get-in item [:id :S])
     :balance (bigdec (get-in item [:balance :N]))}))

(defn new-store
  [aws-client]
  (reify store/LedgerStore
    (get-account [_ id]
      (item->account (:Item (invoke! aws-client :GetItem
                               {:TableName table-name
                                :Key {:id {:S id}}}))))
    (save-account [_ account]
      (invoke! aws-client :PutItem
        {:TableName table-name
         :Item (account->item account)})
      account)))
