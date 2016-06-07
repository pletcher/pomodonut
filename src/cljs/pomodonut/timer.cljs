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
            [pomodonut.state :refer [app-state]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(defonce interval (atom nil))
(defonce timeout (atom nil))

(defn timer-class [break?]
  (str
    "bg-"
    (if break? "green" "red")
    " circle flex flex-center mx-auto shadow "
    (if-not (nil? @interval) " pointer")))

(defn format-time
  "Format time as mm:ss"
  [t]
  (let [minutes (js/parseInt (/ t ONE_MINUTE))
        seconds (.slice
                  (str "00" (js/parseInt (mod t ONE_MINUTE)))
                  -2)]
    (str minutes ":" seconds)))

;; https://gist.github.com/swannodette/5882703
(defn wait [ms]
  (let [c (chan)
        t (js/setTimeout (fn [] (close! c)) ms)]
    (reset! timeout t)
    c))

(defn clear-timeout []
  (js/clearTimeout @timeout)
  (close! @interval)
  (reset! interval nil)
  (reset! timeout nil))

(defn count-tasks
  "Counts the number of tasks for which f
  is `true`"
  [f]
  (count (filter #(f %) (:tasks @app-state))))

(defn start-timer
  [duration s]
  (when-not (nil? @interval)
    (clear-timeout))
  (let [i (go-loop [t s]
            (om/transact! reconciler
              `[(timer/update ~{:duration duration
                                :elapsed t})])
            (if (< t duration)
              (do
                (.play (gdom/getElement "tick-sound"))
                (<! (wait 1000))
                (recur (inc t)))
              (do
                (clear-timeout)
                (if (get-in @app-state [:timer :break?])
                  (om/transact! reconciler
                    `[(timer/update ~{:break? false
                                     :duration TWENTY_FIVE_MINUTES
                                     :elapsed 0})])
                  (do
                    (.play (gdom/getElement "chime-sound"))
                    (om/transact! reconciler
                      `[(tasks/complete) :tasks])
                    (om/transact! reconciler
                      `[(timer/update
                          ~{:break? true
                            :duration
                            (if (== 0 (rem (count-tasks :done?) 4))
                              TEN_MINUTES
                              FIVE_MINUTES)
                            :elapsed 0})]))))))]
    (reset! interval i)))

(defn stop-timer [c]
  (clear-timeout)
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
        (start-timer duration elapsed))))
  (render [this]
    (let [{:keys [break? duration elapsed]} (om/props this)]
      (dom/div #js {:className (timer-class break?)
                    :onClick #(when-not (nil? @interval)
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
