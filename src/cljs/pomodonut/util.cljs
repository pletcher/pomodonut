(ns pomodonut.util
  (:require [pomodonut.constants :refer [ONE_MINUTE]]))

(defn format-time
  "Format time as mm:ss"
  [t]
  (let [minutes (js/parseInt (/ t ONE_MINUTE))
        seconds (.slice (str "00" (js/parseInt (mod t ONE_MINUTE))) -2)]
    (str minutes ":" seconds)))
