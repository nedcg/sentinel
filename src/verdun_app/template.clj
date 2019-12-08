(ns verdun-app.template
  (:require [hiccup.core :as hiccup]
            [hiccup.page :as hiccup-page]
            [hiccup.form :as hiccup-form]))

(defn navbar []
  [:nav.bp3-navbar.bp3-dark
   [:div
    {:style "margin: 0 auto; width: 480px;"}
    [:div.bp3-navbar-group.bp3-align-left
     [:div.bp3-navbar-heading "Verdun"]]
    [:div.bp3-navbar-group.bp3-align-right
     [:button.bp3-button.bp3-minimal.bp3-icon-home "Home"]
     [:span.bp3-navbar-divider]
     [:button.bp3-button.bp3-minimal.bp3-icon-user]
     [:button.bp3-button.bp3-minimal.bp3-icon-notifications]
     [:button.bp3-button.bp3-minimal.bp3-icon-cog]]]])

(defn html5-base [body]
  (hiccup/html
   (hiccup-page/html5
    {:lang "en"}
    [:head
     [:link {:type "text/css" :href "https://unpkg.com/normalize.css@^7.0.0" :rel "stylesheet"}]
     [:link {:type "text/css" :href "https://unpkg.com/@blueprintjs/icons@^3.4.0/lib/css/blueprint-icons.css" :rel "stylesheet"}]
     [:link {:type "text/css" :href "https://unpkg.com/@blueprintjs/core@^3.10.0/lib/css/blueprint.css" :rel "stylesheet"}]
     [:link {:type "text/css" :href "css/style.css" :rel "stylesheet"}]]
    [:body
     (navbar)
     [:div.row
      [:div.side]
      [:div.main
       body]]]
    [:footer
     [:script {:src "https://unpkg.com/@blueprintjs/icons@^3.4.0"}]
     [:script {:src "https://unpkg.com/@blueprintjs/core@^3.10.0"}]])))

(defn signup-page []
  (html5-base
   (hiccup-form/form-to
    [:post "/signup"]
    [:div
     (hiccup-form/label "name" "Name")
     (hiccup-form/text-field "name")]
    [:div
     (hiccup-form/label "email" "Email")
     (hiccup-form/text-field {:type "email"} "email")]
    [:div
     (hiccup-form/label "password" "Password")
     (hiccup-form/password-field "password")]
    [:div
     (hiccup-form/label "password_repeat" "Password repeat")
     (hiccup-form/password-field "password_repeat")]
    (hiccup-form/submit-button "Sign up"))))

(defn login-page [flash]
  (html5-base
   [:div
    (when (some? flash) [:span flash])
    (hiccup-form/form-to
     [:post "/login"]
     [:div
      (hiccup-form/label "username" "Username")
      (hiccup-form/text-field "username")]
     [:div
      (hiccup-form/label "password" "Password")
      (hiccup-form/password-field "password")]
     (hiccup-form/submit-button "Login"))]))

(defn home-page []
  (html5-base
   [:ol
    [:li [:a {:href "/signup"} "sign-up"]]
    [:li [:a {:href "/login"} "login"]]
    [:li [:a {:href "/logout"} "logout"]]]))
