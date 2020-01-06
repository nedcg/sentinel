(ns verdun-app.store
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [ragtime.jdbc :as rjdbc]
            [ragtime.repl :as repl]))

(def db-spec
  {:jdbcUrl (or (System/getenv "VERDUN_DB_URI") "jdbc:mysql://root:root@127.0.0.1:3306/verdun")})

(defn- now [] (new java.util.Date))
(def default-limit 20)

(defn load-config []
  {:datastore  (rjdbc/sql-database (:jdbcUrl db-spec))
   :migrations (rjdbc/load-resources "migrations")})

(defn migrate []
  (repl/migrate (load-config)))

(defn rollback []
  (repl/rollback (load-config)))

(defn execute! [db command]
  (jdbc/execute! db command {:builder-fn next.jdbc.result-set/as-unqualified-maps}))

(defn find-by [db table query]
  (let [limit (:limit query)
        order-by (:order-by query)
        query (dissoc query :limit :order-by)
        opts {:builder-fn next.jdbc.result-set/as-unqualified-maps
              :max-rows (or limit default-limit)}
        opts (when order-by (merge opts {:order-by order-by}) opts)]
    (if (empty? query)
      (sql/query db [(str "select * from " (name table))] opts)
      (sql/find-by-keys db table query opts))))

(defn find-one [db table query]
  (let [res (find-by db table query)]
    (if (> (count res) 1)
      (throw
       (ex-info "More than 1 record found"
                {:code :e_incorrect_query}))
      (first res))))

(defn insert! [db table record]
  (try
    (sql/insert! db table record)
    (:id record)
    (catch java.sql.SQLIntegrityConstraintViolationException e
      (throw (ex-info "duplicate key" {:code :e_duplicated_key :exception e})))))

(defn update! [db table query set-map]
  (sql/update! db table set-map query))

(defn delete! [db table query]
  (sql/delete! db table query))

;; (insert! db-spec :user {:username "nedcg1" :email "nedcg1@outlook.com" :password "nedcg123"})
