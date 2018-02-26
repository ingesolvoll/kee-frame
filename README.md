# kee-frame

Micro framework on top of [re-frame](https://github.com/Day8/re-frame). Heavily inspired by ideas from the [Keechma](https://keechma.com/) framework.

## Rationale
Working with re-frame is a very productive and satisfying experience. The minimal structure enforced by the framework is very well thought out, everything just fits. However, it does not provide a complete solution for everything a modern SPA needs. I always spend a good chunk of time setting up the missing bits, copy-pasting from previous projects. For me it's routine, for a beginner it's probably frustrating, difficult and time consuming.

The first version of kee-frame tries to solve this problem, delivering an out-of-the-box solution for routing and data lifecycle.

## Features
* Automatic router configuration
* URL as the single source of truth
* Route controllers for data setup and teardown.
* Figwheel-friendly. No duplicated events, no loops gone wild.

## Benefits of chosen architecture
* Back/forward and all browser history in general just works
* Bookmarkable URLs all the way. Same URL, same view.
* When figwheel reloads the code, you keep your state and stay on the same page.
* No need for `component-did-mount` to trigger data loading from your view components means stronger decoupling.

## Installation
Add the following dependency to your `project.clj` file:
```
[kee-frame "0.1.0-SNAPSHOT"]
```

## Getting started
The `kee-frame.core` namespace contains the public API
```clojure
(require '[kee-frame.core :as kee-frame :refer [reg-controller reg-view dispatch-view]])
```

## Routes
Any data-centric router lib is a good fit for kee-frame. Bidi was chosen as the default format to use, support for additional routing lbs might come in the near future.

Here's an example, using an example from the bidi docs

```clojure
(def my-routes ["/" {"" :index
                     "/" {"todos" :todos
                          ":id" :article}}])

(kee-frame/start! my-routes)
```

## Controllers
A controller is a map with two required keys (`params` and `start`), and one optional (`stop`). 

The `params` function receives the route data on every navigation event from the router. Its only job is to return the part of the route that it's interested in. This value combined with the previous value decides the next state of the controller. I'll come back to that in more detail.

The `start` function takes as parameters the value returned from `params` and the full re-frame context. It should return nil or an event vector to be dispatched.

The `stop` function receives the re-frame context and also returns nil or an event vector.

```clojure      
(reg-controller :main
                {:params (fn [route] (-> route :page (= "todos"))
                 :start  (fn [params ctx] [:all-todos-poll/start])
                 :stop (fn [ctx] [:all-todos-poll/stop])})

(reg-controller :todo
                {:params (fn [route] (number? (-> route :params :id))
                 :start  (fn [todo-id ctx]
                           [:load-todo-from-server todo-id])})
```

For `start` and `stop` it's very common to ignore the parameters and just return an event vector, and for that you can use a vector instead of a function:

```clojure      
(reg-controller :main
                {:params (fn [route] (-> route :page (= "todos"))
                 :start  [:all-todos-poll/start]
                 :stop   [:all-todos-poll/stop]})
```

## Controller state transitions
This rules of controller states are stolen entirely from Keechma. They are:
* When previous and current parameter values are the same, do nothing
* When previous parameter was nil and current is not nil, call `start`.
* When previous parameter was not nil and current is nil, call `stop`.
* When both previous and current are not nil, but different, call `stop`, then `start`.
