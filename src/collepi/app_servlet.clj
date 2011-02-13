(ns collepi.app_servlet
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:use collepi.core)
  (:use [appengine-magic.servlet :only [make-servlet-service-method]]))


(defn -service [this request response]
  ((make-servlet-service-method collepi-app) this request response))
