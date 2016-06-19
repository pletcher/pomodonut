(ns pomodonut.timer
  (:require [cljs.core.async :refer [chan close!]]
            [goog.dom :as gdom]
            [om.dom :as dom]
            [om.next :as om :refer-macros [defui]]
            [pomodonut.constants :refer [ONE_MINUTE
                                         FIVE_MINUTES
                                         TEN_MINUTES
                                         TWENTY_FIVE_MINUTES]]
            [pomodonut.reconciler :refer [reconciler]]
            [pomodonut.state :refer [app-state]]
            [pomodonut.util :refer [format-time]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def worker (atom nil))

(defn timer-class [break?]
  (str
    "bg-"
    (if break? "green" "red")
    " circle flex flex-center mx-auto shadow "
    (if-not (nil? @worker) " pointer")))

(defn count-tasks
  "Counts the number of tasks for which f returns true"
  [f]
  (count (filter f (:tasks @app-state))))

(defn play-sound! [name]
  (let [el (gdom/getElement "audio")]
    (aset el "src" (str "wav/" name ".wav"))
    (.play el)))

(defn take-break! []
  (om/transact! reconciler
    `[(timer/update
        ~{:break? true
          :duration
          (if (== 0 (rem (count-tasks :done?) 4))
            TEN_MINUTES
            FIVE_MINUTES)
          :elapsed 0})]))

(defn receive-msg
  [duration e]
  (let [evt (.-data e)]
    (condp == evt
      "tick" (do
               (play-sound! "watch-tick")
               (om/transact! reconciler
                 `[(timer/tick ~{:duration duration}) :timer]))
      "stop" (if (get-in @app-state [:timer :break?])
               (om/transact! reconciler
                 `[(timer/update ~{:break? false
                                   :duration TWENTY_FIVE_MINUTES
                                   :elapsed 0})])
               (do
                 (play-sound! "chime")
                 (om/transact! reconciler
                   `[(tasks/complete) :tasks])
                 (js/setTimeout take-break! 1000))))))

(defn start-timer
  [duration]
  (let [w (js/Worker. "workers/timer.js")]
    (.addEventListener w "message" (partial receive-msg duration))
    (.postMessage w #js ["start" duration])
    (reset! worker w)))

(defn stop-timer [c]
  (.postMessage @worker #js ["stop"])
  (reset! worker nil)
  (set! (.-title js/document) "Pomodonut")
  (om/transact! c `[(timer/update ~{:break? false
                                    :duration TWENTY_FIVE_MINUTES
                                    :elapsed 0})]))

(defui Timer
  static om/IQuery
  (query [this]
    [:break? :duration :elapsed])
  Object
  (componentDidUpdate [this prev-props _]
    (let [{:keys [break? duration elapsed]} (om/props this)]
      (when (and break? (not (:break? prev-props)))
        (start-timer duration))))
  (render [this]
    (let [{:keys [break? duration elapsed]} (om/props this)]
      (dom/div #js {:className (timer-class break?)
                    :onClick #(when-not (nil? @worker)
                                (stop-timer this)
                                (.focus (gdom/getElement "task-form"))
                                (when-not break?
                                  (om/transact! this
                                    `[(tasks/abandon) :tasks])))
                    :style #js {:height "auto"
                                :maxWidth 300
                                :minHeight 300
                                :minWidth 300}}
        (dom/span #js {:className "h1 white"}
          (format-time (- duration elapsed)))))))

(def timer-component (om/factory Timer))
