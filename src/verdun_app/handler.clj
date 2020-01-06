(ns verdun-app.handler
  (:require [ring.util.response :as ring-resp]
            [io.pedestal.log :as log]
            [io.pedestal.http.route :as route]
            [buddy.hashers :as hashers]
            [next.jdbc :as jdbc]
            [verdun-app.service :as service]
            [verdun-app.template :as template]))

(defn- ok [body] {:status 200 :body body})
(defn- created [body] {:status 200 :body body})

(defn- get-params [{:keys [query-params path-params form-params json-params edn-params]}]
  (merge path-params edn-params json-params form-params query-params))

(defn handle-tasks-get
  [request]
  (let [{:keys [accept db query-params]} request
        pagination                       (-> request
                                             get-params
                                             (select-keys [:lk :limit]))]
    (ok (service/find-tasks db pagination))))

(defn handle-task-post
  [request]
  (let [{:keys [auth-userid db]} request
        task (-> request
                 get-params
                 (select-keys [:title :description])
                 (assoc :created_by auth-userid))]
    (ok (service/create-task db task))))

(defn handle-task-assign-put
  [request]
  (let [{:keys [auth-userid db]} request
        {user-id :user_id
         task-id :task_id}       (log/spy (get-params request))]
    (jdbc/with-transaction [tx db]
      (ok (service/assign-task tx task-id user-id auth-userid)))))

(defn handle-signup-post
  [request]
  (let [db   (:db request)
        user (-> request get-params (select-keys [:name :username :email :password]))
        user (service/signup db user)]
    (created {:id (:id user)})))

(defn handle-login-post
  [request]
  (let [db                            (:db request)
        {:keys [username password m]} (get-params request)
        user                          (or (service/get-user-by db :username username)
                                          (service/get-user-by db :email username))]
    (if (some? user)
      (if (service/check-password password (:password user))
        (let [username                          (:username user)
              token-data                        {:id (str (:id user))}
              {key :privkey secret :passphrase} (:auth-conf request)
              result                            {:redirected-from m
                                                 :token           (service/generate-token token-data)}]
          (-> (ok result)
              (assoc :session result)))
        (throw (ex-info "invalid password" {:code :w_invalid_password})))
      (throw (ex-info "user not found" {:code :w_user_not_found})))))
