(ns ledger.ports.event-publisher)

(defprotocol EventPublisher
  (publish-event [this event]
    "Publish a domain event (e.g. a TransactionRecorded map). Returns nil."))
