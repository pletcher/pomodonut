(ns pomodonut.migrations
  (:require [environ.core :refer [env]]
            [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl]))

(defn load-config []
  {:datastore (jdbc/sql-database (env :database-url))
   :migrations (jdbc/load-resources "db/migrations")})

(defn migrate []
  (repl/migrate (load-config)))

(defn rollback []
  (repl/rollback (load-config)))
