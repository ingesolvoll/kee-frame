# kee-frame

Micro framework on top of [re-frame](https://github.com/Day8/re-frame). Inspired by ideas from the [Keechma](https://keechma.com/) framework.

## Rationale
I love re-frame. Compared to 

Everyone loves re-frame. Very little boilerplate, just enough structure for your app. So what's to improve? Nothing really. It does what it does perfectly well. But it still has the same weakness as every focused Clojure lib out there:

* It's not a complete solution. There are missing parts. Important parts.
* The other parts are also small focused libs.

To me, the most obvious parts missing are routing and a higher level approach to data loading. Users, particularly beginners, don't need the abundance of options in client side routing, they need a setup that just works. And they need clean and simple solutions to common patterns in SPAs. Solutions that don't involve `component-did-mount` and other low level constructs. kee-frame provides a tiny bit of architecture, and some library glue to get you started quickly.

## Features
* Automatic router configuration
* URL as the single source of truth
* Route controllers for data setup and teardown.
* Chained event handlers with shorthand syntax
* Figwheel-friendly.

## Benefits of chosen architecture
* Back/forward and all browser history in general just works
* Bookmarkable URLs all the way. Same URL, same view.
* When figwheel reloads the code, you keep your state and stay on the same page.
* No need for `component-did-mount` to trigger data loading from your view components means stronger decoupling.

## Demo application
I made an example app that shows historical and real-time soccer results. I believe it shows very well the strengths of the framework.

[Online demo app](http://kee-frame-sample.herokuapp.com/) 

[Demo app source](https://github.com/ingesolvoll/kee-frame-sample)

Feel free to clone the demo app and do some figwheelin' with it!

## Installation
Add the following dependency to your `project.clj` file:
```
[kee-frame "0.1.1"]
```

## Getting started
kee-frame can be introduced into your re-frame app without affecting any existing code. You wire up your views, events and subscriptions as you normally do, and sprinkle a bit of kee-frame on top of that.

The `kee-frame.core` namespace contains the public API
```clojure
(require '[kee-frame.core :as kee-frame :refer [reg-controller]])
```

## Routes
Any data-centric router lib is a good fit for kee-frame, [bidi](https://github.com/juxt/bidi) was chosen out of familiarity.

```clojure
(def my-routes ["" {"/"                       :index
                    ["/league/" :id "/" :tab] :league}])

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
