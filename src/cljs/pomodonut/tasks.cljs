(ns pomodonut.tasks
  (:require [om.dom :as dom]
            [om.next :as om :refer-macros [defui]]
            [pomodonut.constants :refer [TWENTY_FIVE_MINUTES
                                         ENTER_KEY
                                         ESCAPE_KEY]]
            [pomodonut.timer :refer [start-timer]]))

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
  (str "list-item pointer px3 py1" (when done? " gray")))

(defn toggle-done
  [c e]
  (let [{:keys [done?] :as props} (om/props c)]
    (om/transact! c
      `[(tasks/update ~(merge props {:done? (not done?)})) :tasks])))

(defui Task
  static om/IQuery
  (query [this]
    [:done? :title :ts])
  Object
  (render [this]
    (let [{:keys [title] :as props} (om/props this)]
      (dom/li #js {:className (task-class props)
                   :onClick #(toggle-done this %)} title))))

(def task (om/factory Task {:keyfn :ts}))

(defn submit
  [c e]
  (do
    (om/transact! c `[(tasks/create ~(om/props c)) :tasks])
    (om/transact! c `[(tasks/update-temp ~nil) :tasks/temp])
    (start-timer TWENTY_FIVE_MINUTES 0)
    (.blur (.-target e))
    (doto e
      (.preventDefault)
      (.stopPropagation))))

(defn change-title
  [c e]
  (when-not (.-defaultPrevented e)
    (om/transact! c
      `[(tasks/update-temp ~{:title (-> e .-target .-value)})])))

(defn key-down
  [c e]
  (condp == (.-keyCode e)
    ENTER_KEY (submit c e)
    ESCAPE_KEY (.blur (.-target e))
    nil))

(defui TaskForm
  static om/IQuery
  (query [this]
    [:title])
  Object
  (render [this]
    (let [{:keys [title]} (om/props this)]
      (dom/div #js {:className "mx-auto"
                    :style #js {:width 340}}
        (dom/div #js {:className "full-width group"}
          (dom/input #js {:className "field full-width px0 py1"
                          ;; FIXME: This is a bit of a hack to
                          ;; focus the form when abandoning a
                          ;; task
                          :id "task-form"
                          :onChange #(change-title this %)
                          :onKeyDown #(key-down this %)
                          :placeholder "What are you going to do?"
                          :required true
                          :type "text"
                          :value (or title "")}))))))

(def task-form (om/factory TaskForm))

(defui TaskList
  Object
  (render [this]
    (let [tasks (om/props this)]
      (if (> (count tasks) 0)
        (dom/div #js {:className "sm-absolute border list mt4 px0 py1 shadow sm-mr3 sm-mt1 sm-right"
                      :style #js {:borderRadius 2
                                  :minWidth 240}}
          (apply dom/ul nil (map task tasks)))))))

(def task-list (om/factory TaskList))
