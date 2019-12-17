(defproject verdun-app "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [io.pedestal/pedestal.service "0.5.7"]
                 [hiccup "1.0.5"]
                 [seancorfield/next.jdbc "1.0.12"]
                 [mysql/mysql-connector-java "8.0.18"]
                 [com.zaxxer/HikariCP "3.4.1"]
                 [ragtime "0.8.0"]
                 [org.clojure/data.json "0.2.7"]
                 [buddy "2.0.0"]
                 [reduce-fsm "0.1.4"]
                 [com.taoensso/carmine "2.19.1"]
                 [org.mongodb/bson "3.12.0"]

                 [io.pedestal/pedestal.jetty "0.5.7"]

                 [ch.qos.logback/logback-classic "1.2.3" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.26"]
                 [org.slf4j/jcl-over-slf4j "1.7.26"]
                 [org.slf4j/log4j-over-slf4j "1.7.26"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  ;; If you use HTTP/2 or ALPN, use the java-agent to pull in the correct alpn-boot dependency
                                        ;:java-agents [[org.mortbay.jetty.alpn/jetty-alpn-agent "2.0.5"]]
  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "verdun-app.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.5.7"]]}
             :uberjar {:aot [verdun-app.server]}}
  :aliases {"migrate"  ["run" "-m" "verdun-app.store/migrate"]
            "rollback" ["run" "-m" "verdun-app.store/rollback"]}
  :main ^{:skip-aot true} verdun-app.server)
