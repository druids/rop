(ns rop.core-test
  (:require
    [clojure.string :refer [blank? lower-case]]
    [clojure.test :refer [deftest is testing]]
    [rop.core :as rop]))


(defn- format-email
  [input]
  (update input :email lower-case))


(defn- validate-email
  [input]
  (if (-> input :email blank?)
    (rop/fail {:status 400, :body {:errors {:email ["Invalid format"]}}})
    (rop/succeed input)))


(defn- create-user
  [input]
  (-> input
      (assoc :new-user {:email (:email input), :id 1})
      (assoc-in [:response :status] 201)
      (assoc-in [:response :headers] {:content-type :application/json})))


(defn- send-email!
  [input]
  ;; send e-mail here
  (println "Sending e-mail"))


(defn try-to-create-user
  [input]
  (rop/>>=* :new-user
            input
            (rop/switch format-email)
            validate-email
            (rop/switch create-user)
            (rop/dead send-email!)))


(deftest rop-test
  (testing "Should return a success Ring's response with headers and status"
    (is (= {:body {:email "foo@bar.com", :id 1}, :status 201, :headers {:content-type :application/json}}
           (try-to-create-user {:email "FOO@BAR.COM", :new-user nil}))))

  (testing "Should return a failure Ring's response"
    (is (= {:body {:errors {:email ["Invalid format"]}} :status 400}
           (try-to-create-user {:email "", :new-user nil}))))

  (testing "Should return a success Ring's response"
    (is (= {:body "foo@bar.com", :status 200, :headers {}}
           (rop/>>=* :email
                     {:email "FOO@BAR.COM"}
                     (rop/switch format-email)
                     validate-email))))

  (testing "Should return a success Ring's response with limited output"
    (is (= {:id 1}
           (:body (rop/>>=* [:new-user #{:id}]
                            {:email "FOO@BAR.COM", :new-user nil}
                            (rop/switch format-email)
                            validate-email
                            (rop/switch create-user)
                            (rop/dead send-email!))))))

  (testing "Should return a success"
    (is (= {:email "foo@bar.com", :new-user {:email "foo@bar.com", :id 1}}
           (rop/>>= {:email "FOO@BAR.COM", :new-user nil}
                    (rop/switch format-email)
                    validate-email
                    (rop/switch #(assoc % :new-user {:email (:email %), :id 1}))
                    (rop/dead send-email!))))))
