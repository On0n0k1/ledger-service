(ns ledger.metrics
  (:require [iapetos.core :as prometheus]
            [iapetos.collector.ring :as ring-metrics]))

(defonce registry
  (-> (prometheus/collector-registry)
      (ring-metrics/initialize)))
