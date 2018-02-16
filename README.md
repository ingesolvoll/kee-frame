# re-start

## Rationale
Working with re-frame is a very productive and satisfying experience. The minimal structure enforced by the framework is very well thought out, everything just fits. However, it does not provide a complete solution for everything a modern SPA needs. Setting up bookmarkable and back-button friendly routes is a challenge, as well as finding a clean way of loading data from the server at the right times. core.async loops for polling the server can be challenging in combination with navigation and figwheel code reloading.

The Keechma micro framework has some pretty good solutions to these problems. The core idea is to let the view be a function of the URL, and to strictly separate views from logic and data loading. re-start tries to bring some of these ideas into re-frame, hopefully getting some of the benefits in the process.

## Benefits
* Routing just works. You provide the route data, re-start hooks it up for you.
* No `component-did-mount` for loading data. The URL decides when and what.
* Strong separation between controller logic and view rendering. 
* Opt-in feature for dispatching views by route.

## Installation

Add the following dependency to your `project.clj` file:

```
[re-start "0.0.1"]
```

## Getting started

re-start introduces a couple of new concepts in re-frame, but tries to stay close to the original API. First we need to require re-frame and re-start

```clojure
(require '[re-start.core :refer [reg-controller reg-view dispatch]]
          [re-frame.core :as re-frame]
          [reagent.core :as r])
```

```clojure
(reg-controller :todo
                {:params (fn [route] (-> route :route-params :todo-id))
                 :start  (fn [todo-id ctx]
                           [:load-todo-from-server todo-id])})
```


## Controllers
A re-start controller is a map
