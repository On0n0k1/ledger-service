(ns ledger.domain.ledger
  "Pure domain logic for the account ledger: no I/O, no framework
  dependencies. Every function here takes and returns plain data.")

(defn new-account
  [id]
  {:id id :balance 0M})

(defn deposit
  [account amount]
  (if (pos? amount)
    {:ok true :account (update account :balance + amount)}
    {:ok false :error :invalid-amount}))

(defn withdraw
  [account amount]
  (cond
    (not (pos? amount))
    {:ok false :error :invalid-amount}

    (> amount (:balance account))
    {:ok false :error :insufficient-funds}

    :else
    {:ok true :account (update account :balance - amount)}))

(defn balance
  [account]
  (:balance account))
