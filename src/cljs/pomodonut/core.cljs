(ns pomodonut.core
  (:require [goog.dom :as gdom]
            [om.dom :as dom]
            [om.next :as om :refer-macros [defui]]
            [pomodonut.parser :as parser]
            [pomodonut.reconciler :refer [reconciler]]
            [pomodonut.tasks :refer [Task task TaskForm task-form task-list]]
            [pomodonut.timer :refer [Timer timer-component]]))

(enable-console-print!)

(defn audio-node ^:private [s]
  (dom/audio #js {:id s}
    (dom/source #js {:src (str "mp3/" s ".mp3")
                     :type "audio/mpeg"})
    (dom/source #js {:src (str "wav/" s ".wav")
                     :type "audio/wav"})))

(defui Root
  static om/IQuery
  (query [this]
    [{:tasks/temp (om/get-query TaskForm)}
     {:tasks (om/get-query Task)}
     {:timer (om/get-query Timer)}])
  Object
  (render [this]
    (let [{:keys [tasks tasks/temp timer]} (om/props this)]
      (dom/div #js {:className "full-width"}
        (audio-node "watch-tick")
        (audio-node "chime")
        (dom/div #js {:className "sm-flex flex-center py4"}
          (timer-component timer)
          (task-list tasks))
        (task-form temp)))))

(om/add-root! reconciler
  Root (gdom/getElement "app"))
