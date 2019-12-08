(ns verdun-app.resource
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.params :as params]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.ring-middlewares :as middlewares]
            [io.pedestal.interceptor.error :as error-int]
            [io.pedestal.log :as log]
            [ring.util.response :as ring-resp]
            [verdun-app.handler :as handler]
            [verdun-app.service :as service]
            [monger.core :as monger]
            [ring.middleware.session.cookie :refer [cookie-store]])
  (:import org.bson.types.ObjectId))

(def db-interceptor
  {:name ::db-interceptor
   :enter #(let [conn (monger/connect)
                 db   (monger/get-db conn "verdun")]
             (update % :request assoc :conn conn :db db))})

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

(def error-handler-interceptor
  (error-int/error-dispatch [ctx ex]
                            [{:exception-type :java.lang.ArithmeticException}]
                            (assoc ctx :response {:status 400 :body "Another bad one"})
                            :else (assoc ctx :io.pedestal.interceptor.chain/error ex)))

(def common-interceptors
  [(body-params/body-params)
   params/keyword-params
   http/html-body
   (middlewares/session {:store (cookie-store)
                         :cookie-name "verdun"
                         :cookie-attrs {:max-age (* 60 60 24)}})
   (middlewares/flash)
   db-interceptor
   wrap-auth-token-interceptor
   spy-interceptor])

(def routes
  #{["/signup"  :get   (conj common-interceptors `handler/handle-signup-get)]
    ["/signup"  :post  (conj common-interceptors `handler/handle-signup-post)]
    ["/login"   :get   (conj common-interceptors `handler/handle-login-get)]
    ["/login"   :post  (conj common-interceptors `handler/handle-login-post)]
    ["/tasks"   :get   (conj common-interceptors auth-interceptor spy-interceptor `handler/handle-tasks-get)]
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
