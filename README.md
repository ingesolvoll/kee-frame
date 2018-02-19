# kee-frame

Micro framework building the core concepts of Keechma on top of Re-frame.

## Rationale
Working with re-frame is a very productive and satisfying experience. The minimal structure enforced by the framework is very well thought out, everything just fits. However, it does not provide a complete solution for everything a modern SPA needs. Setting up bookmarkable and back-button friendly routes is a challenge, as well as finding a clean way of loading data from the server at the right times. core.async loops for polling the server can be challenging in combination with navigation and figwheel code reloading.

I'm very intrigued by the idea of the URL as the "single source of truth". When you are able to recreate the application state by only using the URL, some  

kee-frame tries to bring some of these ideas into re-frame, hopefully getting some major benefits with minimal effort.

## Features
* Automatic route setup
* URL as the single source of truth
* Controllers for setup/teardown independent from view rendering.
* Pluggable dispatch components, for selecting view based on route.

## Installation
Add the following dependency to your `project.clj` file:
```
[kee-frame "0.1.0-SNAPSHOT"]
```

## Getting started
The `kee-frame.core` namespace contains the public API
```clojure
(require '[kee-frame.core :refer [reg-controller reg-view dispatch-view]])
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
kee-frame introduces a couple of new concepts in re-frame, but tries to stay close to the original API.

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

A controller is a map with two required keys (`params` and `start`), and one optional (`stop`). 

The `params` function receives the route data on every navigation event from the router. Its only job is to return the part of the route that it's interested in. This value combined with the previous value decides the next state of the controller. I'll come back to that in more detail.

The `start` function takes as parameters the value returned from `params` and the full re-frame context. It should return nil or an event vector to be dispatched.

The `stop` function receives the re-frame context and also returns nil or an event vector.

## Controller state transitions
This rules of controller states are stolen entirely from Keechma. They are:
* When previous and current parameter values are the same, do nothing
* When previous parameter was nil and current is not nil, call `start`.
* When previous parameter was not nil and current is nil, call `stop`.
* When both previous and current are not nil, but different, call `stop`, then `start`.