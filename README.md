# kee-frame

Micro framework on top of [re-frame](https://github.com/Day8/re-frame). Inspired by ideas from the [Keechma](https://keechma.com/) framework.

[![Build Status](https://travis-ci.org/ingesolvoll/kee-frame.svg?branch=master)](https://travis-ci.org/ingesolvoll/kee-frame)

## Rationale
Re-frame events are very simple and generic, making them perfect building blocks for higher level abstractions. Kee-frame is leveraging this to implement the main ideas from the Keechma framework in re-frame. An opiniated out-of-the-box routing solution makes it easier to get started with re-frame. Controllers and event chains helps you scale in the long run.

## Features
* Automatic router setup
* URL as the single source of truth
* Route controllers for data loading.
* Event chains for reducing callback ping pong.
* Spec checking for your app DB
* Figwheel-friendly.

## Benefits of leaving the URL in charge
* Back/forward and bookmarking in general just works
* When figwheel reloads the code, you keep your state and stay on the same page.
* No need for `component-did-mount` to trigger data loading from your view.
* UI code is purely declarative

## Demo application
I made a simple demo app showing football results. Have a look around, and observe how all data loading just works while navigating and refreshing the page.

[Online demo app](http://kee-frame-sample.herokuapp.com/) 

[Demo app source](https://github.com/ingesolvoll/kee-frame-sample)

Feel free to clone the demo app and do some figwheelin' with it!

## Installation
Add the following dependency to your `project.clj` file:
```
[kee-frame "0.1.4"]
```

## Getting started

The `kee-frame.core` namespace contains the public API. It also contains wrapped versions of `reg-event-db` and `reg-event-fx`.

```clojure
(require '[kee-frame.core :as k :refer [reg-controller reg-chain reg-event-db reg-event-fx]])
```

## Routes
Kee-frame uses [bidi](https://github.com/juxt/bidi) for routing. I won't go into detail here about the bidi way of doing things, go read their docs if you're unfamiliar with the structure.

Here are the routes from the demo app:

```clojure
(def my-routes ["" {"/"                       :index
                    ["/league/" :id "/" :tab] :league}])
```

## Starting your app
The `start!` function starts the router and configures the application.

```clojure
(k/start!  {:routes       my-routes
            :app-db-spec :my-app/db-spec
            :initial-db   your-blank-db-map
            :debug?       true})
```

Subsequent calls to start are not a problem, browser events will only get hooked up once. 

The `routes` property is required, the rest are opt-in features. 

The `debug` boolean option is for enabling debug interceptors on all your events, as well traces from the activities of controllers. 

If you provide an `app-db-spec`, the framework will let you know when a bug in your event handler is trying to corrupt your DB structure. This is incredibly useful, so you should put down the effort to spec up your db!

## Controllers
A controller is a connection between the route data and your event handlers. It is a map with two required keys (`params` and `start`), and one optional (`stop`).

The `params` function receives the route data every time the URL changes. Its only job is to return the part of the route that it's interested in. This value combined with the previous value decides the next state of the controller. I'll come back to that in more detail.

The `start` function accepts the full re-frame context and the value returned from `params`. It should return nil or an event vector to be dispatched.

The `stop` function receives the re-frame context and also returns nil or an event vector.

```clojure      
(reg-controller :league
                {:params (fn [{:keys [handler route-params]}]
                           (when (= handler :league)
                             (:id route-params)))
                 :start  (fn [_ id]
                           [:league/load id])})
```

For `start` and `stop` it's very common to ignore the parameters and just return an event vector, and for that you can use a vector instead of a function:

```clojure      
(reg-controller :leagues
                {:params (constantly true) ;; Will cause the controller to start immediately, but only once
                 :start  [:leagues/load]})
```

## Controller state transitions
This rules of controller states are stolen entirely from Keechma. They are:
* When previous and current parameter values are the same, do nothing
* When previous parameter was nil and current is not nil, call `start`.
* When previous parameter was not nil and current is nil, call `stop`.
* When both previous and current are not nil, but different, call `stop`, then `start`.

## Event chains
One very common pattern in re-frame is to register 2 events, one for doing a side effect like HTTP, one for handling the response data. Sometimes you need more than 2 events. Creating these event chains is boring and verbose, and you easily lose track of the flow. See an example below:

```clojure      
(reg-event-fx :add-customer
              interceptors
              (fn [_ [_ customer]]
                {:http-xhrio {:method          :post
                              :uri             "/customers"
                              :body            customer-data
                              :on-success      [:customer-added]}}))

(reg-event-db :customer-added
              interceptors
              (fn [db [_ customer]]
                (update db :customers conj customer)))
```

If some code ends up in between these 2 close friends, the cost of following the flow greatly increases. Even when they are positioned next to each other, an extra amount of thinking is required in order to see where the data goes.

Kee-frame tries to solve the problem of verbosity and readability by using event chains. 

A chain is a list of FX (not DB) type event handlers. 

Through the magic of re-frame `interceptors`, we are able to chain together event handlers without registering them by name. We are also able to infer how to dispatch to next in chain. Here's the above example using a chain:

```clojure      
(reg-chain :add-customer
            (fn [_ [_ customer]]
              {:http-xhrio {:method          :post
                            :uri             "/customers"
                            :body            customer-data}})
            (fn [{:keys [db]} [_ _ added-customer]] ;; Remember: No DB functions, only FX.
              {:db (update db :customers conj added-customer)}))
```

The chain code does the same thing as the event code. It registers the events `:add-customer` and `:add-customer-1` as normal re-frame events. The events are registered with an interceptor that processes the event effects and finds the appropriate `on-success` handler for the HTTP effect. Less work for you to do and less cognitive load reading the code later on.

The chain concept might not always be a good fit, but quite often it does a great job of uncluttering your event ping pong.

## Chain rules
Every parameter received through the chain is passed on to the next step. So the parameters to the first chain function will be appended to the head of the next function's parameters, and so on. The last function called will receive the concatenation of all previous parameter lists. This might seem a bit odd, but quite often you need the id received on step 1 to do something in step 3.

You are allowed to dispatch out of chain, but there must always be a "slot" available for the chain to put its next dispatch. Currently only `dispatch` and `on-success` of :http-xhrio are supported, one of them must be not set by the previous event. The effects supported by the inference algorithm will be configurable soon.

You can specify your dispatch explicitly using a special keyword as your event id, like this: `{:on-success [:kee-frame.core/next 1 2 3]}`. The keyword will be replaced by a generated id for the next in chain. 


## Introducing kee-frame into an existing app

The chain feature is a pure add-on to re-frame, and can be easily introduced in an existing app. The controller feature depends on the specific routing implementation of kee-frame, so to use controllers you might need to adapt your routing. In order to ease this process, the `start!` function has a configuration option named `:process-route`. This can be a function that accepts the route data and modifies it to fit your existing app.

## Credits

The implementation of kee-frame is quite simple, building on rock solid libraries and other people's ideas. The main influence is the [Keechma](https://keechma.com/) framework. It is a superb piece of thinking and work, go check it out! Apart from that, the following libraries make kee-frame possible:

* [re-frame](https://github.com/Day8/re-frame) and [reagent](https://reagent-project.github.io/). The world needs to know about these 2 kings of frontend development, and we all need to contribute to their widespread use. This framework is an attempt in that direction.
* [bidi](https://github.com/juxt/bidi). Simple and easy bidirectional routing. I love bidi and think it fits very well with kee-frame, but I'm considering adding support for more routing libraries.
* [accountant](https://github.com/venantius/accountant). A navigation library that hooks to any routing system. Made my life so much easier when I discovered it.
* [etaoin](https://github.com/igrishaev/etaoin) and [lein-test-refresh](https://github.com/jakemcc/lein-test-refresh). 2 good examples of how powerful Clojure is. Etaoin makes browser integration testing fun again, while lein-test-refresh provides you with a development flow that no other platform will give you.

Thank you!
