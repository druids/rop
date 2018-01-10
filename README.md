Railway Oriented Programming
============================

This is another Clojure implementation of [Railway Oriented
Programming](https://fsharpforfunandprofit.com/posts/recipe-part2/). It's based on
[this gist](https://gist.github.com/ah45/7518292c620679c460557a7038751d6d).

The reason for another implementation is to provide more pleasant usage for common cases. See example of usage below.

[![CircleCI](https://circleci.com/gh/druids/rop.svg?style=svg)](https://circleci.com/gh/druids/rop)
[![Dependencies Status](https://jarkeeper.com/druids/rop/status.png)](https://jarkeeper.com/druids/rop)
[![License](https://img.shields.io/badge/MIT-Clause-blue.svg)](https://opensource.org/licenses/MIT)


Leiningen/Boot
--------------

```clojure
[rop "0.2.0"]
```


Documentation
-------------

```clojure
(require [rop.core :as rop])

```

Let's define "bussiness logic" functions like this
```clojure
(defn format-email
  [input]
  (update input :email lower-case))


(defn validate-email
  [input]
  (if (-> input :email blank?)
    (rop/fail {:status 400, :body {:errors {:email ["Invalid format"]}}})
    (rop/succeed input)))


(defn create-user
  [input]
  (assoc input :new-user {:email (:email input), :id 1})


(defn send-email!
  [input]
  ;; send e-mail here
  (println "Sending e-mail"))
```

A simple use case looks like this
```clojure
(rop/>>=  {:email "FOO@BAR.COM", :new-user nil}
          (rop/switch format-email)
          validate-email
          (rop/switch create-user)
          (rop/dead send-email!)))
```

A result of the use case is
```clojure
{:email "foo@bar.com", :new-user {:email "foo@bar.com", :id 1}}
```

An input hash-map flows through functions defined in `>>=`, until any function returns `rop/fail`.
 Otherwise the input hash-map is returned at the end. Internally `>>=` uses `funcool/cats` library, but the result is
 extracted for you.


### succeed

It marks a result of a function as a success result. Thus `>>=` will call another function.

```clojure

(defn format-email
  [input]
  (rop/succeed (update input :email lower-case)))
```


### fail

It marks a result of a function as a fail result. It stops computation.

```clojure
(defn validate-email
  [input]
  (if (-> input :email blank?)
    (rop/fail {:errors {:email ["Invalid format"]}})
    (rop/succeed input)))
```

### switch

Makes a normal function to be tautological (always returns a success result). It's a shortcut for wrapping functions.

```clojure
(defn format-email
  [input]
  (update input :email lower-case)) ;; <-- see not marking a result as a success

(rop/>>=  {:email "FOO@BAR.COM", :new-user nil}
          (rop/switch format-email) ;; <-- I can wrap here
          validate-email
          (rop/switch create-user)
          (rop/dead send-email!)))
```

### dead

A wrapper for deadend functions. Any side effect can be done here and I don't need to care about returning `succeed`.

```clojure
(defn send-email!
  [input]
  ;; send e-mail here
  (println "Sending e-mail")) ;; <-- See no `succeed` here


(rop/>>=  {:email "FOO@BAR.COM", :new-user nil}
          (rop/switch format-email)
          validate-email
          (rop/switch create-user)
          (rop/dead send-email!))) ;; <-- I can wrap here
```


### >>=

An infix version of bind for piping two-track values into switch fns. Can be used to pipe two-track values
 through a series of switch fns. First is an input hash-map it will be passed throgh switch fns.
 Rest parameters as switch fns.


### >>=*

It's advanced `>>=` function. Returning Ring's response from `>>=` is a common use case and this function helps with it.
 First parameter is a success key (it will be used as :body in result hash-map) or a tuple with success-key and
 output-keys (at the end `select-keys` will be applied on a success result with these `output-keys`).
 Second is an input hash-map it will be passed throgh switch fns. Rest parameters as switch fns.

Above use case can be improved via `>>=*`
```clojure
(rop/>>=  {:email "FOO@BAR.COM", :new-user nil}
          (rop/switch format-email)
          validate-email
          (rop/switch create-user)
          (rop/dead send-email!)))
;; returns
{:email "foo@bar.com", :new-user {:email "foo@bar.com", :id 1}}
```

But it's common that we want to take just one key and return it as a Ring's response. We can do it with `>>=*` by
 passing `:new-user` as a first argument.
```clojure
(rop/>>=*  :new-user
           {:email "FOO@BAR.COM", :new-user nil}
           (rop/switch format-email)
           validate-email
           (rop/switch create-user)
           (rop/dead send-email!)))
;; returns
{:body {:email "foo@bar.com", :id 1}, :status 200, :headers {}}
```

Also it's common that not all keys of hash-map can be exposed. Output keys can be limited like this
```clojure
(rop/>>=*  [:new-user #{:id}]
           {:email "FOO@BAR.COM", :new-user nil}
           (rop/switch format-email)
           validate-email
           (rop/switch create-user)
           (rop/dead send-email!)))
;; returns
{:body {:id 1}, :status 200, :headers {}}
```

Of course it also works with sequences.
```clojure
(rop/>>=*  [:new-users #{:id}]
           {:email "FOO@BAR.COM", :new-user nil, :new-users nil}
           (rop/switch format-email)
           validate-email
           (rop/switch create-user)
           (rop/switch #(assoc % :new-users [(:new-user %)]))
           (rop/dead send-email!))))))
;; returns
{:body [{:id 1}], :status 200, :headers {}} ;; <-- See :body
```

Sure HTTP headers and status code can be defined with `>>=*` too
```clojure
(defn create-user
  [input]
  (-> input
      (assoc :new-user {:email (:email input), :id 1})
      (assoc-in [:response :status] 201)
      (assoc-in [:response :headers] {:content-type :application/json})))

(rop/>>=*  [:new-user #{:id}]
           {:email "FOO@BAR.COM", :new-user nil}
           (rop/switch format-email)
           validate-email
           (rop/switch create-user)
           (rop/dead send-email!)))
;; returns
{:body {:id 1}, :status 201, :headers {:content-type :application/json}}
```

Same when a failure needs to define status of headers
```clojure
(defn validate-email
  [input]
  (if (-> input :email blank?)
    (rop/fail {:status 400, :body {:errors {:email ["Invalid format"]}}}) ;; <-- a whole Ring's response here
    (rop/succeed input)))

(rop/>>=*  [:new-user #{:id}]
           {:email "", :new-user nil}
           (rop/switch format-email)
           validate-email
           (rop/switch create-user)
           (rop/dead send-email!)))
;; returns
{:status 400, :body {:errors {:email ["Invalid format"]}}}
```
