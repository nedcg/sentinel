(ns verdun-app.service
  (:require [verdun-app.store :as store]
            [verdun-app.template :as template]
            [buddy.sign.jws :as jws]
            [buddy.sign.util :as sign-util]
            [buddy.core.keys :as ks]
            [buddy.hashers :as hashers]
            [clj-time.core :as t]
            [taoensso.carmine :as car :refer (wcar)]
            [taoensso.carmine.message-queue :as car-mq]
            [next.jdbc :as jdbc]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import org.bson.types.ObjectId))

(defn- now [] (new java.util.Date))
(def server1-conn {:pool {} :spec {:uri "redis://localhost:6379/"}})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))
(defn- oid [] (str (ObjectId.)))
(defn- default-new-record []
  {:id (oid)})

(def privkey
  (ks/private-key
   (io/resource
    (or (System/getenv "VERDUN_AUTH_KEY_PRIVATE") "auth_key.pem"))
   (or (System/getenv "VERDUN_AUTH_SECRET") "!y36q4=>.qbwBu`:")))

(def pubkey
  (ks/public-key
   (io/resource
    (or (System/getenv "AUTH_KEY_PUBLIC") "auth_key.pub.pem"))))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn get-user-by [db field value]
  (if (some? value)
    (store/find-one db :user {field value})
    nil))

(defn check-password [password-plain password-hash]
  (hashers/check password-plain password-hash))

(defn generate-token [data]
  (let [exp (-> (t/plus (t/now) (t/hours 16)) (sign-util/to-timestamp))]
    (jws/sign (json/write-str data) privkey {:alg :rs256 :exp exp})))

(defn unsign-token [token]
  (when-let [data (jws/unsign token pubkey {:alg :rs256})]
    (json/read-str (String. data) :key-fn keyword)))

(defn signup [db user]
  (let [user (-> user
                 (update :password hashers/derive)
                 (assoc :email_verification_token (uuid)))]
    (store/insert! db :user (merge (default-new-record) user))))

(defn mq-enqueue [db queue msg]
  (wcar* (car-mq/enqueue (name queue) msg)))

(defn- increase-tasks-priorities [db assigned-to from-priority]
  (store/execute! db [(str "UPDATE priority "
                           "SET priority_user=priority_user + 1 "
                           "WHERE user_id=? AND priority_user GE ? ")
                      assigned-to from-priority]))

(defn- decrease-tasks-priorities [db assigned-to from-priority]
  (store/execute! db [(str "UPDATE priority "
                           "SET priority_user=priority_user - 1 "
                           "WHERE user_id=? AND priority_user GE ? ")
                      assigned-to from-priority]))

(defn decreate-user-assigned-task-count [db user-id]
  (store/execute! db [(str "UPDATE user "
                           "SET task_assigned_count=task_assigned_count-1 "
                           "WHERE id=?")
                      user-id]))

(defn increate-user-assigned-task-count [db user-id]
  (store/execute! db [(str "UPDATE user "
                           "SET task_assigned_count=task_assigned_count+1 "
                           "WHERE id=?")
                      user-id]))

(defn unassign-task [db user-id task-id]
  (when-let [priority (when (some? user-id)
                        (-> (store/find-one db :priority {:user_id user-id :task_id task-id})
                            :priority_user))]
    (decreate-user-assigned-task-count db user-id)
    (decrease-tasks-priorities db user-id priority))
  (let [set-map {:assigned_to nil}]
    (store/update! db :task {:id task-id} set-map)
    (store/insert! db :task_history (merge (default-new-record)
                                           {:type (name :update)
                                            :task_id task-id
                                            :fields {:assigned_to nil}}))))

(defn- priorities-fetch [db user-id]
  (let [nodes (store/execute! db ["SELECT task_id, next_task_id, prev_task_id FROM priority WHERE user_id=?" user-id])]
    (reduce
     (fn [res node]
       [(if (some? (:prev_task_id node)) (first res) node)
        (assoc (last res) (:task_id node) node)])
     [nil {}]
     nodes)))

(defn- priorities-get [db user-id]
  (let [[root nodes] (priorities-fetch db user-id)
        sorted-nodes (loop [next (:task_id root)
                            res  []]
                       (if-not (some? (get nodes next))
                         res
                         (recur (:next_task_id (get nodes next))
                                (conj res (get nodes next)))))]
    (map #(store/find-one db :task {:id (:task_id %)}) sorted-nodes)))

(defn- priorities-insert [db user-id task-id]
  (let [[{last-task-id :task_id}] (store/execute! db ["SELECT task_id FROM priority WHERE user_id=? AND next_task_id IS NULL" user-id])]
    (if (some? last-task-id)
      (do
        (store/insert! db :priority {:user_id user-id :task_id task-id :prev_task_id last-task-id})
        (store/update! db :priority {:user_id user-id :task_id last-task-id} {:next_task_id task-id}))
      (store/insert! db :priority {:user_id user-id :task_id task-id}))))

(defn- priorities-move [db nodes user-id node-id node-before-id]
  (let [a (get nodes node-id)
        b (get nodes node-before-id)]
    (when (not= (:task_id a) (:prev_task_id b))
      (store/update! db :priority {:user_id user-id :task_id (:prev_task_id a)} {:next_task_id (:next_task_id a)})
      (store/update! db :priority {:user_id user-id :task_id (:next_task_id a)} {:prev_task_id (:prev_task_id a)})

      (store/update! db :priority {:user_id user-id :task_id (:task_id a)} {:next_task_id (:task_id b)})
      (store/update! db :priority {:user_id user-id :task_id (:task_id a)} {:prev_task_id (:prev_task_id b)})

      (store/update! db :priority {:user_id user-id :task_id (:prev_task_id b)} {:next_task_id (:task_id a)})
      (store/update! db :priority {:user_id user-id :task_id (:task_id b)} {:prev_task_id (:task_id a)}))))

(defn- priorities-delete [db user-id task-id]
  (when-let [priority-record (store/find-by db :priority {:user_id user-id :task_id task-id})]
    (let [{next-task-id :next_task_id
           prev-task-id :prev_task_id} priority-record]
      (if (some? next-task-id)
        (store/update! db :priority {:user_id user-id :task_id next-task-id} {:prev_task_id prev-task-id}))
      (if (some? prev-task-id)
        (store/update! db :priority {:user_id user-id :task_id prev-task-id} {:next_task_id next-task-id}))
      (store/delete! db :priority {:user_id user-id :task_id task-id}))))

(defn assign-task
  [db task-id user-id assigned-by]
  (let [{prev-assigned-to :assigned_to} (store/find-one db :task {:id task-id})
        set-map                         {:updated_by  assigned-by
                                         :assigned_to user-id}]
    (when prev-assigned-to (unassign-task db prev-assigned-to task-id))
    (increate-user-assigned-task-count db user-id)
    (store/update! db :task {:id task-id} set-map)
    (store/insert! db :task_history (merge (default-new-record)
                                           {:type       (name :update)
                                            :task_id    task-id
                                            :created_by assigned-by
                                            :fields     (json/write-str set-map)}))))

(defn create-task [db task]
  (let [assigned-to (:assigned_to task)
        task-id (store/insert! db :task (merge (default-new-record) task))]
    (when (some? assigned-to)
      (assign-task db task-id assigned-to assigned-to)
      (priorities-insert db assigned-to task-id))
    task))

(defn find-tasks [db {:keys [lk limit]}]
  (store/find-by db :task {}))

(comment
  (let [db {:jdbcUrl (or (System/getenv "VERDUN_DB_URI") "jdbc:mysql://root:root@127.0.0.1:3306/verdun")}
        user-id (signup db {:name "Eduardo Caceres" :email "eduardo.caceres@outlook.com" :password "secret123" :username "nedcg"})]
    (doseq [t (range 5)]
      (create-task db {:title (str "title " t) :description (str "description " t) :created_by user-id :assigned_to user-id}))))
