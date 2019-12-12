(ns verdun-app.store
  (:require [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl])
  (:import org.bson.types.ObjectId))

(def db-name "verdun")
(defn- now [] (new java.util.Date))

(defn load-config []
  {:datastore  (jdbc/sql-database "jdbc:mysql://root:root@127.0.0.1:3306/verdun")
   :migrations (jdbc/load-resources "migrations")})

(defn migrate []
  (repl/migrate (load-config)))

(defn rollback []
  (repl/rollback (load-config)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; (defn- oid [id]                                                               ;;
;;   (if (instance? ObjectId id)                                                 ;;
;;     id                                                                        ;;
;;     (mu/object-id id)))                                                       ;;
;;                                                                               ;;
;; (defn- default-new-record []                                                  ;;
;;   (let [now (now)]                                                            ;;
;;     {:_id (mu/object-id)                                                      ;;
;;      :created-at now                                                          ;;
;;      :updated-at now}))                                                       ;;
;;                                                                               ;;
;; (defn default-update-record []                                                ;;
;;   {:updated-at (now)})                                                        ;;
;;                                                                               ;;
;; (defn setup-db [db]                                                           ;;
;;   (mc/ensure-index db :users (array-map :email 1) {:unique true})             ;;
;;   (mc/ensure-index db :users (array-map :username 1) {:unique true}))         ;;
;;                                                                               ;;
;; (defn get-by-id [db table id]                                                 ;;
;;   (mc/find-map-by-id db table id))                                            ;;
;;                                                                               ;;
;; (defn get-by [db table query]                                                 ;;
;;   (mc/find-one-as-map db table query))                                        ;;
;;                                                                               ;;
;; (defn find-by [db table query {:keys [lk limit]}]                             ;;
;;   (mq/with-collection db (name table)                                         ;;
;;     (mq/find (if (some? lk)                                                   ;;
;;                (merge query {:_id {$gt (oid lk)}})                            ;;
;;                query))                                                        ;;
;;     (mq/limit limit)))                                                        ;;
;;                                                                               ;;
;; (defn insert [db table record]                                                ;;
;;   (try                                                                        ;;
;;     (mc/insert-and-return db table (merge (default-new-record) record))       ;;
;;     (catch com.mongodb.DuplicateKeyException e                                ;;
;;       (throw (ex-info "duplicate key" {:code :e_duplicated_key})))))          ;;
;;                                                                               ;;
;; (defn update-one [db table query update]                                      ;;
;;   (mc/find-and-modify db table query (merge (default-update-record) update))) ;;
;;                                                                               ;;
;; (defn update-by-id [db table id update]                                       ;;
;;   (update-one db table {:_id id} update))                                     ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
