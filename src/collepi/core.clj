(ns collepi.core
  (:use
     [compojure.core :only [GET POST defroutes wrap!]]
     [compojure.route :only [not-found]]
     [ring.util.response :only [redirect]]
     [ring.middleware.session :only [wrap-session]]
     [collepi model util]
     clj-gravatar.core
     )
  (:require
     [appengine-magic.core :as ae]
     [appengine-magic.services.datastore :as ds]
     [appengine-magic.services.user :as du]
     [clojure.contrib.string :as string]
     )
  )

(defmacro json-service [method path bind & body] ; {{{
  `(~method ~path ~bind
      (let [res# (do ~@body)]
        (if (and (map? res#) (contains? res# :status) (contains? res# :headers) (contains? res# :body))
          (assoc res# :body (to-json (:body res#)))
          (to-json res#)))))
(defmacro jsonGET [path bind & body] `(json-service GET ~path ~bind ~@body))
(defmacro jsonPOST [path bind & body] `(json-service POST ~path ~bind ~@body))
(defmacro apiGET [path fn] `(jsonGET ~path {params# :params} (~fn (convert-map params#))))
(defmacro apiGET-with-session [path fn] `(jsonGET ~path {params# :params, session# :session}
                                                  (~fn (convert-map params#) session#)))
(defmacro apiPOST [path fn] `(jsonPOST ~path {params# :params} (~fn (convert-map params#))))
(defmacro apiPOST-with-session [path fn] `(jsonPOST ~path {params# :params, session# :session}
                                                  (~fn (convert-map params#) session#)))
; }}}

(defn convert-rank-for-response [rank]
  (let [user (:user rank)]
    (assoc rank
           :user
           {
            :nickname (.getNickname user)
            :avatar (gravatar-image (.getEmail user))
            }
           :book (get-book (:book rank))
           )

    )
  )

; controller {{{
(defn get-user-controller []
  )
(defn check-login-controller [_]
  (if (du/user-logged-in?)
    (let [user (du/current-user)]
      {
       :loggedin true
       :nickname (.getNickname user)
       :avatar (gravatar-image (.getEmail user))
       :url (du/logout-url)
       }
      )
    {:loggedin false
     :url (du/login-url)
     }
    )
  )

(defn get-my-ranks-controller [params]
  (when (du/user-logged-in?)
    {:count (count-user-rank (du/current-user))
     :result (map convert-rank-for-response (get-rank-from-user (du/current-user)))
     }
    )
  )

(defn set-isbn-controller [{:keys [isbn rank] :or {rank 1} :as params}]
  (println "kitane:" isbn "/" rank "---" params)
  (when (and (du/user-logged-in?) (not (string/blank? isbn)))
    (println "login and isbn is ok")
    (let [book (create-book isbn)]
      (println "setting book:" book)
      (create-rank book (du/current-user) rank "")
      true
      )
    )
  )

(defn hoge [& args] nil)
; }}}

(defroutes api-handler
  (apiGET "/user" hoge);get-user-books-controller)
  (apiGET "/book" hoge)
  (apiGET "/my" get-my-ranks-controller)
  (apiGET "/set" set-isbn-controller)
  (apiGET "/check/login" check-login-controller)
  )

(defroutes main-handler
  (GET "/login" _ (redirect (du/login-url)))
  (GET "/logout" _ (with-session (redirect "/") {}))
  (not-found "page not found")
  )


(defroutes app-handler
  api-handler
  main-handler
  )

(wrap! app-handler wrap-session)

(ae/def-appengine-app collepi-app #'app-handler)
