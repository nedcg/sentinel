(ns verdun-app.store
  (:require [monger.core :as mg]
            [monger.query :as mq]
            [monger.util :as mu]
            [monger.operators :refer [$gt]]
            [monger.collection :as mc])
  (:import org.bson.types.ObjectId))

(def db-name "verdun")
(defn- now [] (new java.util.Date))

(defn- oid [id]
  (if (instance? ObjectId id)
    id
    (mu/object-id id)))

(defn- default-new-record []
  (let [now (now)]
    {:_id (mu/object-id)
     :created-at now
     :updated-at now}))

(defn default-update-record []
  {:updated-at (now)})

(defn setup-db [db]
  (mc/ensure-index db :users (array-map :email 1) {:unique true})
  (mc/ensure-index db :users (array-map :username 1) {:unique true}))

(defn get-by-id [db table id]
  (mc/find-map-by-id db table id))

(defn get-by [db table query]
  (mc/find-one-as-map db table query))

(defn find-by [db table query {:keys [lk limit]}]
  (mq/with-collection db (name table)
    (mq/find (if (some? lk)
               (merge query {:_id {$gt (oid lk)}})
               query))
    (mq/limit limit)))

(defn insert [db table record]
  (try
    (mc/insert-and-return db table (merge (default-new-record) record))
    (catch com.mongodb.DuplicateKeyException e
      (throw (ex-info "duplicate key" {:code :e_duplicated_key})))))

(defn update-one [db table query update]
  (mc/update db table query (merge (default-update-record) update)))

(defn update-by-id [db table id update]
  (mc/update-by-id db table id (merge (default-update-record) update)))
