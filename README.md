# kee-frame

Micro framework on top of [re-frame](https://github.com/Day8/re-frame). Inspired by ideas from the [Keechma](https://keechma.com/) framework.

[![Build Status](https://travis-ci.org/ingesolvoll/kee-frame.svg?branch=master)](https://travis-ci.org/ingesolvoll/kee-frame)

## Rationale
Re-frame events are, like React itself, a very useful low level abstraction. But you shouldn't build your entire system on low level abstractions. Being low level and simple makes you a great building block though. Kee-frame leverages this to help you organize your re-frame events in a simple and meaningful way.

Kee-frame, like Keechma, encourages a design where the URL is the single source of truth, while eliminating a lot of boilerplate setup code.

## Features
* Automatic router setup
* URL as the single source of truth
* Route controllers for data loading.
* Event chains for reducing callback ping pong.
* Spec checking for your app DB
* Figwheel-friendly.

## Benefits of leaving the URL in charge
* Back/forward and all browser history in general just works
* Bookmarkable URLs all the way. Same URL, same view.
* When figwheel reloads the code, you keep your state and stay on the same page.
* No need for `component-did-mount` to trigger data loading from your view.

## Demo application
I made a simple demo app showing footbal results. Have a look around, and observe how all data loading just works while navigating and refreshing the page.

[Online demo app](http://kee-frame-sample.herokuapp.com/) 

[Demo app source](https://github.com/ingesolvoll/kee-frame-sample)

Feel free to clone the demo app and do some figwheelin' with it!

## Installation
Add the following dependency to your `project.clj` file:
```
[kee-frame "0.1.4"]
```

## Getting started

The `kee-frame.core` namespace contains the public API. It mirrors `reg-event-db` and `reg-event-fx` from `re-frame.core`, as well as exposing the API of `kee-frame`.

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
