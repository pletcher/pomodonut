(ns pomodonut.reconciler
  (:require [om.next :as om]
            [pomodonut.state :refer [app-state]]
            [pomodonut.parser :refer [parser]]))

(def reconciler
  (om/reconciler
    {:parser parser
     :state app-state}))
