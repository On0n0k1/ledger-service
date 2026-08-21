(ns ledger.ports.ledger-store)

(defprotocol LedgerStore
  (get-account [this id]
    "Fetch the account with the given id, or nil if it doesn't exist.")
  (save-account [this account]
    "Persist the account, returning the saved account."))
