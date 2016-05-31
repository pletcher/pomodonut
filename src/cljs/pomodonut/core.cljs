(ns pomodonut.core
  (:require [cljs.core.async :refer [chan close!]]
            [goog.dom :as gdom]
            [om.dom :as dom]
            [om.next :as om :refer-macros [defui]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(enable-console-print!)

(defonce app-state (atom {}))
(defonce interval (atom nil))
(defonce timeout (atom nil))

(defonce ONE-MINUTE 60)
(defonce FIVE-MINUTES (* 5 ONE-MINUTE))
(defonce TWENTY-FIVE-MINUTES (* 25 ONE-MINUTE))

(defn timer-class [break?]
  (str "bg-" (if break? "green" "red") " circle flex flex-center mx-auto"))

(defn format-time
  "Format time as mm:ss"
  [t]
  (let [minutes (js/parseInt (/ t ONE-MINUTE))
        seconds (.slice
                  (str "00" (js/parseInt (mod t ONE-MINUTE)))
                  -2)]
    (str minutes ":" seconds)))

;; https://gist.github.com/swannodette/5882703
(defn wait [ms]
  (let [c (chan)
        t (js/setTimeout (fn [] (close! c)) ms)]
    (reset! timeout t)
    c))

(defn start-timer
  [c duration s]
  (let [i (go-loop [t (inc s)]
            (do
              (om/transact! c `[(timer/update-elapsed-time {:t ~t})])
              (when (and
                      (not (nil? @interval))
                      (< t duration))
                (do
                  (<! (wait 1000))
                  (recur (inc t))))
              (when (>= t duration)
                (when-not (nil? @interval)
                  (close! @interval)
                  (reset! interval nil)))))]
    (reset! interval i)))

(defn stop-timer
  [c]
  (do
    (js/clearTimeout @timeout)
    (close! @interval)
    (reset! interval nil)
    (reset! timeout nil)
    (om/transact! c `[(timer/update-elapsed-time {:t ~0})])))

(defui Timer
  static om/IQuery
  (query [this]
    [:break? :duration :elapsed])
  Object
  (componentDidMount [this]
    (om/transact! this `[(timer/reset {:break? false
                                       :duration ~TWENTY-FIVE-MINUTES
                                       :elapsed ~0})]))
  (render [this]
    (let [{:keys [break? duration elapsed]} (om/props this)]
      (dom/div #js {:className (timer-class break?)
                    :onClick #(if (nil? @interval)
                                (start-timer this duration elapsed)
                                (stop-timer this))
                    :style #js {:height 400
                                :width 400}}
        (dom/span #js {:className "h1 white"}
          (format-time (- duration elapsed)))))))

(def timer-component (om/factory Timer))

(defui Root
  static om/IQuery
  (query [this]
    [{:timer (om/get-query Timer)}])
  Object
  (render [this]
    (let [{:keys [timer]} (om/props this)]
      (dom/div #js {:className "full-width"}
        (timer-component timer)))))

(defmulti mutate om/dispatch)

(defmethod mutate :default
  [_ k _]
  {:value {:error (str "No mutation defined for key " k)}})

(defmethod mutate 'timer/reset
  [{:keys [state]} k params]
  (swap! state assoc :timer params))

(defmethod mutate 'timer/update-elapsed-time
  [{:keys [state]} k {:keys [t]}]
  (swap! state assoc-in [:timer :elapsed] t))

(defmulti read om/dispatch)

(defmethod read :default
  [{:keys [state]} k params]
  (let [st @state]
    (if-let [[_ v] (find st k)]
      {:value v}
      {:value :not-found})))

(defmethod read :timer
  [{:keys [state]} k params]
  (let [st @state]
    (if-let [[_ v] (find st k)]
      {:value v}
      {:value {:duration TWENTY-FIVE-MINUTES
               :elapsed 0}})))

(def reconciler
  (om/reconciler
    {:parser (om/parser
               {:mutate mutate
                :read read})
     :state app-state}))

(om/add-root! reconciler
  Root (gdom/getElement "app"))
