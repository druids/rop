(ns rop.core
  "Implementation of Railway Oriented Programming based on
   https://gist.github.com/ah45/7518292c620679c460557a7038751d6d"
  (:require
    [cats.builtin]
    [cats.core :as m]
    [cats.monad.either :as meither]))


(def succeed
  "Convert a value into a two-track (success) result"
  meither/right)


(def fail
  "Convert a value into a two-track (failure) result"
  meither/left)


(def success?
  "Returns true if the given two-track value is a success"
  meither/right?)


(def failure?
  "Returns true if the given two-track value is a failure"
  meither/left?)


(defn switch
  "Converts a normal fn into a switch (one-track input, two-track output)"
  [f]
  (comp succeed f))


(defn tee
  "Returns a fn that calls f on its argument and returns its argument.
   Converts otherwise 'dead-end' fns into one-track fns."
  [f]
  (fn [v]
    (f v)
    v))


(def dead
  "A shortcut for calling (rop/switch (rop/tee send-email!)"
  (comp switch tee))


(defn >>=
  "An infix version of bind for piping two-track values into switch fns. Can be used to pipe two-track values
   through a series of switch fns. First is an input hash-map it will be passed throgh switch fns.
   Rest parameters as switch fns."
  [input & fns]
  (-> m/>>=
      (apply (into [(succeed input)] fns))
      m/extract))


(defn- extract-output-keys
  [result output-keys]
  (if (map? result)
    (select-keys result output-keys)
    (map #(select-keys % output-keys) result)))


(defn- strip-nonexistent-keys
  [data steps]
  (reduce-kv (fn [acc path _]
               (let [path-vec (if (vector? path) path [path])
                     value (get-in data path-vec ::notexists)]
                 (if (= value ::notexists)
                   acc
                   (assoc-in acc path-vec value))))
             {}
             steps))


(defn >>=*
  "An infix version of bind for piping two-track values into switch fns. Can be used to pipe two-track values
   through a series of switch fns. A result of this function is Ring's response.
   First parameter is a success key (it will be used as :body in result hash-map) or a tuple with success-key and
   output-keys (at the end `select-keys` will be applied on a success result with these `output-keys`).
   Second is an input hash-map it will be passed throgh switch fns. Rest parameters as switch fns."
  [success-key-or-tuple input & fns]
  (let [[success-key output-keys] (if (vector? success-key-or-tuple) success-key-or-tuple [success-key-or-tuple nil])
        result (apply m/>>= (into [(succeed input)] fns))
        extracted-result (m/extract result)
        format-output (fn [success-result]
                        (if (coll? output-keys)
                          (extract-output-keys success-result output-keys)
                          success-result))]
    (if (success? result)
      {:body (format-output (get extracted-result success-key))
       :status (get-in extracted-result [:response :status] 200)
       :headers (get-in extracted-result [:response :headers] {})}
      extracted-result)))


(defn =validate-request=
  "A railway function that validates a request by a given scheme.
   If data are valid it updates them in the request (with coerced data), otherwise returns Bad Requests within errors.

   Parameters:
     - `validate` a function that takes an input and a validation scheme, it should return a tuple of errors
          and validated input
     - `scheme` a validation scheme
     - `default` default values as a `hash-map`, it will be merged into a validated input
     - `request-key a key in a `request` that holds the input data`
     - `input` a ROP input"
  [validate scheme defaults request-key {:keys [request] :as input}]
  (let [[errors validated-input] (validate (strip-nonexistent-keys (get request request-key) scheme) scheme)]
    (if (nil? errors)
      (succeed (assoc-in input [:request request-key] (merge defaults
                                                             validated-input)))
      (fail {:status 400, :body {:errors errors}}))))


(defn =merge-params=
  "A railway function that merges a given `source` key into a `target` key in a request.
   It's useful when route params and body params are validated together."
  [source target {:keys [request] :as input}]
  (succeed (update-in input [:request target] merge (get request source))))
