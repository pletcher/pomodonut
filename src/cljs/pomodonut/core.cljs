(ns pomodonut.core
  (:require [cljs.core.async :refer [chan close!]]
            [goog.dom :as gdom]
            [om.dom :as dom]
            [om.next :as om :refer-macros [defui]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(enable-console-print!)

(def app-state (atom {}))
(defonce interval (atom nil))
(defonce timeout (atom nil))

(defonce ONE_MINUTE 60)
(defonce FIVE_MINUTES (* 5 ONE_MINUTE))
(defonce TWENTY_FIVE_MINUTES (* 25 ONE_MINUTE))

(defonce ENTER_KEY 13)

(defonce tick-sound (js/Audio. "wav/watch-tick.wav"))

(declare reconciler)

(defn timer-class [break?]
  (str
    "bg-"
    (if break? "green" "red")
    " circle flex flex-center mx-auto shadow"))

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

(defn start-timer
  [duration s]
  (let [i (go-loop [t (inc s)]
            (om/transact! reconciler
              `[(timer/update ~{:duration duration
                                :elapsed t})])
            (.play tick-sound)
            (if (< t duration)
              (do
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
                    (om/transact! reconciler
                      `[(tasks/complete) :tasks])
                    (om/transact! reconciler
                      `[(timer/update ~{:break? true
                                        :duration FIVE_MINUTES
                                        :elapsed 0})]))))))]
    (reset! interval i)))

(defn stop-timer [c]
  (clear-timeout)
  (om/transact! c `[(timer/update {:elapsed ~0})]))

(defn button-class
  "Returns the class of a button based on its type.
  button-type should be in
  [\"default\", \"disabled\", \"primary\"]"
  [button-type]
  (str "btn btn-" button-type " shadow"))

(defn button
  [props & children]
  (let [{:keys [button-type on-click]} props]
    (dom/a #js {:className (button-class button-type)
                :onClick on-click}
      children)))

(defn task-class
  [{:keys [done?]}]
  (str "list-item px3 py1" (when done? " gray")))

(defui Task
  static om/IQuery
  (query [this]
    [:done? :title :ts])
  Object
  (render [this]
    (let [{:keys [title] :as props} (om/props this)]
      (dom/li #js {:className (task-class props)} title))))

(def task (om/factory Task {:keyfn :ts}))

(defn submit
  [c e]
  (do
    (om/transact! reconciler `[(tasks/create ~(om/props c)) :tasks])
    (om/transact! reconciler `[(tasks/update-temp ~nil) :tasks/temp])
    (start-timer 1 0)
    (doto e (.preventDefault) (.stopPropagation))))

(defn change-title
  [c e]
  (when-not (.-defaultPrevented e)
    (om/transact! c `[(tasks/update-temp ~{:title (-> e .-target .-value)})])))

(defn key-down
  [c e]
  (condp == (.-keyCode e)
    ENTER_KEY (submit c e)
    nil))

(defui TaskForm
  static om/IQuery
  (query [this]
    [:title])
  Object
  (render [this]
    (let [{:keys [title]} (om/props this)]
      (dom/div #js {:className "mx-auto"
                    :style #js {:width 400}}
        (dom/div #js {:className "full-width group"}
          (dom/input #js {:className "field full-width px0 py1"
                          :onChange #(change-title this %)
                          :onKeyDown #(key-down this %)
                          :placeholder "What are you going to do?"
                          :required true
                          :type "text"
                          :value (or title "")}))))))

(def task-form (om/factory TaskForm))

(defui TaskList
  static om/IQuery
  (query [this]
    {:tasks (om/get-query Task)})
  Object
  (render [this]
    (let [tasks (om/props this)]
      (if (> (count tasks) 0)
        (dom/div #js {:className "sm-absolute border list mt4 px0 py1 shadow sm-mr3 sm-mt1 sm-right"
                      :style #js {:borderRadius 2
                                  :minWidth 240}}
          (apply dom/ul nil (map task tasks)))))))

(def task-list (om/factory TaskList))

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
                                (om/transact! this `[(tasks/abandon) :tasks]))
                    :style #js {:height "auto"
                                :maxWidth 300
                                :minHeight 300
                                :minWidth 300}}
        (dom/span #js {:className "h1 white"}
          (format-time (- duration elapsed)))))))

(def timer-component (om/factory Timer))

(defui Root
  static om/IQuery
  (query [this]
    [{:tasks/temp (om/get-query TaskForm)}
     (om/get-query TaskList)
     {:timer (om/get-query Timer)}])
  Object
  (render [this]
    (let [{:keys [tasks tasks/temp timer]} (om/props this)]
      (dom/div #js {:className "full-width"}
        (dom/div #js {:className "sm-flex flex-center py4"}
          (timer-component timer)
          (task-list tasks))
        (task-form temp)))))

(defmulti mutate om/dispatch)

(defmethod mutate :default
  [_ k _]
  {:value {:error (str "No mutation defined for key " k)}})

(defmethod mutate 'tasks/abandon
  [{:keys [state]} k _]
  {:action #(let [st @state
                  title (:title (last (:tasks st)))]
              (swap! state update-in [:tasks]
                (fn [list]
                  (into [] (drop-last list))))
              (swap! state assoc-in [:tasks/temp :title] title))})

(defmethod mutate 'tasks/complete
  [{:keys [state]} k _]
  {:action #(swap! state update-in [:tasks]
              (fn [list]
                (assoc list
                  (- (count list) 1)
                  (assoc (last list) :done? true))))})

(defmethod mutate 'tasks/create
  [{:keys [state]} k {:keys [title]}]
  {:action #(swap! state update-in [:tasks]
              (fn [list]
                (into [] (conj (or list [])
                           {:done? false
                            :title title
                            :ts (js/Date.)}))))})

(defmethod mutate 'tasks/update-temp
  [{:keys [state]} k {:keys [title]}]
  {:action #(swap! state assoc-in [:tasks/temp :title] title)})

(defmethod mutate 'timer/update
  [{:keys [state]} k params]
  {:action #(swap! state update-in [:timer]
              (fn [t] (merge t params)))})

(defmulti read om/dispatch)

(defmethod read :default
  [{:keys [state]} k params]
  (let [st @state]
    (if-let [[_ v] (find st k)]
      {:value v}
      {:value :not-found})))

(defmethod read :tasks
  [{:keys [state]} k params]
  (let [st @state]
    (if-let [[_ v] (find st k)]
      {:value v}
      {:value []})))

(defmethod read :tasks/temp
  [{:keys [state]} k _]
  (let [st @state]
    (if-let [[_ v] (find st k)]
      {:value v}
      {:value {:title ""}})))

(defmethod read :timer
  [{:keys [state]} k params]
  (let [st @state]
    (if-let [[_ v] (find st k)]
      {:value v}
      {:value {:duration TWENTY_FIVE_MINUTES
               :elapsed 0}})))

(def reconciler
  (om/reconciler
    {:parser (om/parser
               {:mutate mutate
                :read read})
     :state app-state}))

(om/add-root! reconciler
  Root (gdom/getElement "app"))
