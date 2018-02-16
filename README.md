# re-frain

> refrain (verb): Stop oneself from doing something.

> refrain (noun): A repeated line or number of lines in a poem or song, typically at the end of each verse.

## Rationale
Working with re-frame is a very productive and satisfying experience. The minimal structure enforced by the framework is very well thought out, everything just fits. However, it does not provide a complete solution for everything a modern SPA needs. Setting up bookmarkable and back-button friendly routes is a challenge, as well as finding a clean way of loading data from the server at the right times. core.async loops for polling the server can be challenging in combination with navigation and figwheel code reloading.

I'm very intrigued by the idea of the URL as the "single source of truth". When you are able to recreate the application state by only using the URL, some  

re-frain tries to bring some of these ideas into re-frame, hopefully getting some of the benefits in the process.

## Features
* No changes to the way you use reagent and re-frame
* Browser navigation and route dispatch is set up by the framework
* Controllers provide loose coupling and clean functional views. No `component-did-mount` for querying the database. 
* Pluggable dispatch components, for selecting view based on route.

## Installation
Add the following dependency to your `project.clj` file:
```
[re-frain "0.0.1"]
```

## Getting started
The `re-frain.core` namespace contains the public API
```clojure
(require '[re-frain.core :refer [reg-controller reg-view dispatch-view]])
```

## Controllers
re-frain introduces a couple of new concepts in re-frame, but tries to stay close to the original API.

```clojure      
(reg-controller :main
                {:params (constantly true)
                 :start  (fn [params ctx] [:all-todos-poll/start])
                 :stop (fn [ctx] [:all-todos-poll/stop])})

(reg-controller :todo
                {:params (fn [route] (-> route :route-params :todo-id))
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

## Routes
Any data-centric router lib is a good fit for re-frain. The Keechma router was chosen because it is simple and because I wanted to promote the high quality libraries of Keechma. The routes are nothing but patterns to be matched agains the URL. Here's an example:

```clojure
(def routes ["/todos/:todo-id"
             "/todos/:todo-id/:mode"])
```
