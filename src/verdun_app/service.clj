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
            [clojure.java.io :as io]))

(defn- now [] (new java.util.Date))
(def server1-conn {:pool {} :spec {:uri "redis://localhost:6379/"}})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

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
    (store/insert! db :user user)))

(defn mq-enqueue [db queue msg]
  (wcar* (car-mq/enqueue (name queue) msg)))

(defn- bump-tasks-priorities [db assigned-to from-priority]
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
                           "WHERE user_id=?")
                      user-id]))

(defn unassign-task [db user-id task-id]
  (when-let [priority (when (some? user-id)
                        (-> (store/find-one db :priority {:user_id user-id :task_id task-id})
                            :priority_user))]
    (decreate-user-assigned-task-count db user-id)
    (decrease-tasks-priorities db user-id priority))
  (store/update! db :task {:id task-id} {:assigned_to nil})
  (store/insert! db :task_history {:type :update :task_id task-id :fields {:assigned_to nil}}))

(defn assign-task
  [db task-id user-id user-priority]
  (let [task (store/find-one db :task {:id task-id})]
    (when-let [prev-user-id (:assigned_to task)]
      (unassign-task db prev-user-id task-id))
    (store/update! db :task {:id task-id} {:assigned_to user-id})
    (store/insert! db :task_history {:type :update :task_id task-id :fields {:assigned_to user-id}})))

(defn create-task [db task]
  (let [task (store/insert! db :task task)]
    (when-let [user-id (:assigned_to task)]
      (assign-task db (:id task) user-id))
    task))

(defn prioritize-task [db task-id priority]
  (let [task               (store/find-by db :task {:id task-id})
        lower-priority     (-> (store/find-by db :priority {:task_id task-id :order-by [[:priority_user :desc]]})
                               first :priority_user inc)
        sanitized-priority (cond
                             (< 1)              1
                             (> lower-priority) lower-priority
                             :else              priority)
        assigned-to        (:assigned_to task)]
    (bump-tasks-priorities db assigned-to priority)
    (store/insert! db :priority {:task_id task-id :user_id assigned-to :priority_user priority})))

(defn find-tasks [db {:keys [lk limit]}]
  (store/find-by db :task {}))
