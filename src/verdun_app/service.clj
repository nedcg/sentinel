(ns verdun-app.service
  (:require [verdun-app.store :as store]
            [verdun-app.template :as template]
            [buddy.sign.jws :as jws]
            [buddy.sign.util :as sign-util]
            [buddy.core.keys :as ks]
            [buddy.hashers :as hashers]
            [monger.util :as monger-util]
            [monger.operators :refer :all]
            [clj-time.core :as t]
            [taoensso.carmine :as car :refer (wcar)]
            [taoensso.carmine.message-queue :as car-mq]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defn- now [] (new java.util.Date))
(def default-pagination {:lk nil :limit 20})

(def server1-conn {:pool {} :spec {:uri "redis://localhost:6379/"}})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

(def privkey
  (ks/private-key
   (io/resource
    (or (System/getenv "AUTH_KEY_PRIVATE") "auth_key.pem"))
   (or (System/getenv "AUTH_SECRET") "!y36q4=>.qbwBu`:")))

(def pubkey
  (ks/public-key
   (io/resource
    (or (System/getenv "AUTH_KEY_PUBLIC") "auth_key.pub.pem"))))

(defn get-user-by [db field value]
  (if (some? value)
    (store/get-by db :users {field value})
    nil))

(defn check-password [password-plain password-hash]
  (hashers/check password-plain password-hash))

(defn generate-token [data]
  (let [exp (-> (t/plus (t/now) (t/hours 6)) (sign-util/to-timestamp))]
    (jws/sign (json/write-str data) privkey {:alg :rs256 :exp exp})))

(defn unsign-token [token]
  (when-let [data (jws/unsign token pubkey {:alg :rs256})]
    (json/read-str (String. data) :key-fn keyword)))

(defn signup [db user]
  (let [user (-> user
                 (update :password hashers/derive)
                 (assoc :email-verified-status :initial ;; :initial :email-sent :verified
                        :email-verification-token (monger-util/random-uuid)))]
    (store/insert db :users user)))

(defn create-task [db task]
  (let [default-values {:status :initial}]
    (store/insert db :tasks (merge task default-values))))

(def workers
  (car-mq/worker
   db (name :task-assigned)
   {:handler
    (fn [{:keys [message attempt]
          {:keys [user-id task-id]} message}]
      {:status :success})}))

(defn mq-enqueue [db queue msg]
  (wcar* (car-mq/enqueue (name queue) msg)))

(defn assign-task
  "Assign a task to a user. If the task currently belongs to another user,
  then it must be prioritized for the previous and new user, elsewhere
  the priority for the task will be minimum"
  [db task-id user-id]
  (let [user (store/get-by-id db :users user-id)
        task (store/get-by-id db :tasks task-id)]
    (store/update-one
     :tasks
     {:_id task-id
      :assigned-to (:assigned-to user)}
     {:assigned-to user-id
      :updated-at (now)})))

(defn prioritize-task [db task-id priority]
  )

(defn find-tasks [db pagination]
  (store/find-by db :tasks {} (merge default-pagination pagination)))
