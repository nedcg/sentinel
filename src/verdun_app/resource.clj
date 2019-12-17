(ns verdun-app.resource
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.params :as params]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.ring-middlewares :as middlewares]
            [io.pedestal.interceptor.error :as error-int]
            [io.pedestal.log :as log]
            [io.pedestal.http.content-negotiation :as conneg]
            [ring.util.response :as ring-resp]
            [verdun-app.handler :as handler]
            [verdun-app.service :as service]
            [next.jdbc.connection :as connection]
            [cheshire.core :as json]
            [ring.middleware.session.cookie :refer [cookie-store]])
  (:import org.bson.types.ObjectId
           (com.zaxxer.hikari HikariDataSource)))

(def db-spec
  {:jdbcUrl (or (System/getenv "VERDUN_DB_URI") "jdbc:mysql://root:root@127.0.0.1:3306/verdun")})

(defonce ^HikariDataSource ds
  (connection/->pool HikariDataSource db-spec))

(def content-neg-interceptor
  (conneg/negotiate-content ["application/edn" "application/json" "text/html"]))

(def coerce-body
  {:name ::coerce-body
   :leave
   (fn [context]
     (let [accepted         (get-in context [:request :accept :field] "text/plain")
           response         (get context :response)
           body             (get response :body)
           coerced-body     (case accepted
                              "text/html"        body
                              "text/plain"       body
                              "application/edn"  (pr-str body)
                              "application/json" (json/generate-string body))
           updated-response (assoc response
                                   :headers {"Content-Type" accepted}
                                   :body    coerced-body)]
       (assoc context :response updated-response)))})

(def db-interceptor
  {:name  ::db-interceptor
   :enter #(let [db ds]
             (update % :request assoc :db db))})

(def header-auth-token-interceptor
  {:name ::wrap-auth-token-interceptor
   :enter (fn [ctx]
            (if-let [auth-token (get-in ctx [:request :headers "authentication"])]
              (update-in ctx [:request :session] assoc :token auth-token)
              ctx))})

(def wrap-auth-token-interceptor
  {:name ::wrap-auth-token-interceptor
   :enter (fn [ctx]
            (if-let [user-id (:id (when-let [token (get-in ctx [:request :session :token])]
                                    (service/unsign-token token)))]
              (update ctx :request assoc :auth-userid user-id)
              ctx))})

(def auth-interceptor
  {:name ::auth-interceptor
   :enter (fn [ctx]
            (if-not (some? (get-in ctx [:request :auth-userid]))
              (assoc ctx :response (ring-resp/redirect (str "/login?m=" (get-in ctx [:request :uri]))))
              ctx))})

(def spy-interceptor
  {:name ::auth-interceptor
   :enter (fn [ctx]
            (log/debug :request (:request ctx))
            ctx)
   :leave (fn [ctx]
            (log/debug :response (:response ctx))
            ctx)})

(defn- chain-error [ctx ex]
  (assoc ctx :io.pedestal.interceptor.chain/error ex))

(def error-handler-interceptor
  (error-int/error-dispatch
   [ctx ex]
   [{:interceptor :verdun-app.resource/wrap-auth-token-interceptor}] ;; not working
   (assoc ctx :response {:status 401 :body {:code :e_invalid_token}})
   [{:exception-type :clojure.lang.ExceptionInfo}]
   (clojure.core.match/match
    [(ex-data ex)]
    [{:code :e_duplicated_key}] (assoc ctx :response {:status 400 :body {:code :e_duplicated_key}})
    [{:code :w_user_not_found}] (assoc ctx :response {:status 400 :body {:code :e_invalid_credentials}})
    [{:code :w_password_incorrect}] (assoc ctx :response {:status 400 :body {:code :e_invalid_credentials}})
    :else (chain-error ctx ex))
   :else (chain-error ctx ex)))

(def common-interceptors
  [coerce-body
   content-neg-interceptor
   (body-params/body-params)
   params/keyword-params
   (middlewares/session {:store (cookie-store)
                         :cookie-name "verdun"
                         :cookie-attrs {:max-age (* 60 60 24)}})
   db-interceptor
   header-auth-token-interceptor
   wrap-auth-token-interceptor
   error-handler-interceptor])

(def routes
  #{["/signup"  :post  (conj common-interceptors `handler/handle-signup-post)]
    ["/login"   :post  (conj common-interceptors `handler/handle-login-post)]
    ["/tasks"   :get   (conj common-interceptors auth-interceptor `handler/handle-tasks-get)]
    ["/tasks"   :post  (conj common-interceptors auth-interceptor `handler/handle-tasks-post)]})

(def resource
  {:env :prod
   ::http/routes routes
   ::http/resource-path "/public"
   ::http/type :jetty
   ::http/host "0.0.0.0"
   ::http/port 8080
   ::http/container-options {:h2c? true
                             :h2? false
                             :ssl? false}})
