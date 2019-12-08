(ns verdun-app.handler
  (:require [ring.util.response :as ring-resp]
            [io.pedestal.log :as log]
            [io.pedestal.http.route :as route]
            [buddy.hashers :as hashers]
            [verdun-app.service :as service]
            [verdun-app.template :as template]))

(defn html-response
  [html]
  (-> html
      ring-resp/response
      (ring-resp/content-type "text/html")))

(defn json-response
  [json]
  (-> json
      ring-resp/response
      (ring-resp/content-type "application/json")))

(defn edn-response
  [edn]
  (-> edn
      ring-resp/response
      (ring-resp/content-type "application/edn")))

(defn handle-tasks-get
  [request]
  (let [{:keys [accept db query-params]} request
        pagination                       (-> query-params
                                             (select-keys [:lk :limit]))]
    (condp = (:accept request)
      "text/html" (html-response
                   (format "Clojure %s - served from %s, session %s"
                           (clojure-version)
                           (route/url-for ::handle-tasks-get)
                           (:session request)))
      (edn-response (service/find-tasks db pagination)))))

(defn handle-tasks-post
  [request]
  (let [db   (:db request)
        uid  (:auth-userid request)
        task (-> request :params
                 (select-keys [:title :description])
                 (assoc :created-by uid))]
    (edn-response (service/create-task db task))))

(defn handle-home-get
  [request]
  (html-response (template/home-page)))

(defn handle-login-get
  [request]
  (html-response (template/login-page (:flash request))))

(defn handle-signup-get
  [request]
  (html-response (template/signup-page)))

(defn handle-signup-post
  [request]
  (try
    (let [db   (:db request)
          user (-> request :form-params (select-keys [:name :username :email :password]))
          user (service/signup db user)]
      (assoc (ring-resp/created (route/url-for ::handle-login-get)) :flash "login succed"))
    (catch Exception e
      (let [{:keys [code]} (ex-data e)]
        (if (= code :e_duplicated_key)
          (assoc (ring-resp/redirect (route/url-for ::handle-signup-get)) :flash "user already registered")
          (throw e))))))

(defn handle-login-post
  [request]
  (let [db                          (:db request)
        {:keys [username password]} (:form-params request)
        user                        (or (service/get-user-by db :username username)
                                        (service/get-user-by db :email username))]
    (if (some? user)
      (if (service/check-password password (:password user))
        (let [username                          (:username user)
              token-data                        {:id (str (:_id user))}
              {key :privkey secret :passphrase} (:auth-conf request)
              token                             (service/generate-token token-data)]
          (-> (ring-resp/redirect (route/url-for ::handle-tasks-get))
              (assoc :session {:token token})))
        (do (println "invalid password") (-> (ring-resp/redirect (route/url-for ::handle-login-get))
                                             (assoc :flash "Invalid password"))))
      (do (println "user not found") (-> (ring-resp/redirect (route/url-for ::handle-login-get))
                                         (assoc :flash "User not found"))))))
