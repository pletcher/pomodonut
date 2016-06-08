(ns pomodonut.parser
  (:require [om.next :as om]
            [pomodonut.util :refer [format-time]]))

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

(defmethod mutate 'tasks/update
  [{:keys [state]} k params]
  {:action (fn []
             (swap! state update-in [:tasks]
               (fn [list]
                 (let [st @state
                       {:keys [tasks]} st
                       {:keys [ts]} params
                       task (first (filter #(= (:ts %) ts) tasks))
                       idx (.indexOf tasks task)]
                   (assoc tasks idx (merge task params))))))})

(defmethod mutate 'tasks/update-temp
  [{:keys [state]} k {:keys [title]}]
  {:action #(swap! state assoc-in [:tasks/temp :title] title)})

(defmethod mutate 'timer/tick
  [{:keys [state]} _ {:keys [duration]}]
  {:action #(let [st @state
                  {:keys [elapsed]} (get-in st [:timer])]
              (set!
                (.-title js/document)
                (str "Pomodonut · " (format-time (- duration (inc elapsed)))))
              (swap! state update-in [:timer]
                (fn [t] (merge t {:duration duration
                                  :elapsed (inc elapsed)}))))})


(defmethod mutate 'timer/update
  [{:keys [state]} k {:keys [duration elapsed] :as params}]
  {:action #((set!
               (.-title js/document)
               (str "Pomodonut · " (format-time (- duration elapsed))))
             (swap! state update-in [:timer]
               (fn [t] (merge t params))))})

#_(defmethod mutate 'timer/update
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
      {:value {:duration 1500
               :elapsed 0}})))

(def parser (om/parser {:mutate mutate
                        :read read}))
