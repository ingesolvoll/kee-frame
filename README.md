# kee-frame

Micro framework on top of [re-frame](https://github.com/Day8/re-frame). Inspired by ideas from the [Keechma](https://keechma.com/) framework.

[![Build Status](https://travis-ci.org/ingesolvoll/kee-frame.svg?branch=master)](https://travis-ci.org/ingesolvoll/kee-frame)

[![Clojars Project](https://img.shields.io/clojars/v/kee-frame.svg)](https://clojars.org/kee-frame)

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

## Articles

[Learning kee-frame in 5 minutes](http://ingesolvoll.github.io/posts/2018-04-01-learning-kee-frame-in-5-minutes/)

[Introduction and background for kee-frame controllers](http://ingesolvoll.github.io/posts/2018-04-01-kee-frame-putting-the-url-in-charge/)

## Demo application
I made a simple demo app showing football results. Have a look around, and observe how all data loading just works while navigating and refreshing the page.

[Online demo app](http://kee-frame-sample.herokuapp.com/) 

[Demo app source](https://github.com/ingesolvoll/kee-frame-sample)

Feel free to clone the demo app and do some figwheelin' with it!

## Installation
Add the following dependency to your `project.clj` file:
```clojure
[kee-frame "0.2.1"]
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
(k/start!  {:routes         my-routes
            :app-db-spec    :my-app/db-spec
            :initial-db     your-blank-db-map
            :root-component [my-root-reagent-component]
            :debug?         true})
```

Subsequent calls to start are not a problem, so call this function as often as you want. Typically on every figwheel reload.

The `routes` property causes kee-frame to wire up the browser to navigate by those routes. Skip this property if you want to do your own routing. See the "Introducing kee-frame into an existing app" section.

If you provide `:root-component`, kee-frame will render that component in the DOM element with id "app". Make sure you have such an element in your index.html. You are free to do the initial rendering yourself if you want, just skip this setting. If you use this feature, make sure that `k/start!` is called every time figwheel reloads your code. 

The `debug` boolean option is for enabling debug interceptors on all your events, as well as traces from the activities of controllers. 

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

You are allowed to dispatch out of chain, but there must always be a "slot" available for the chain to put its next dispatch.

You can specify your dispatch explicitly using a special keyword as your event id, like this: `{:on-success [:kee-frame.core/next 1 2 3]}`. The keyword will be replaced by a generated id for the next in chain. 

## But I want to decide the name of my events!

Sometimes you may want to specify your event names, to ease debugging or readability. In that case, use the `kee-frame.core/reg-chain-named`, like this: 

```clojure
(reg-chain-named :first-id 
                  first-fn 
                  :second-id 
                  second-fn
                  ....)
```

## Configuring chains (since 0.2.0)

`dispatch` and `on-success` of :http-xhrio are supported by default in event chains. Apps that introduce their own effect handlers, or use libraries with custom effect handlers, need to tell the chain system how to dispatch using these handlers. The default config looks like this:

```clojure
[{
  ;; Is the effect in the map?
  :effect-present?   (fn [effects] (:http-xhrio effects)) 
  
  ;;  The dispatch set for this effect in the map returned from the event handler
  :get-dispatch (fn [effects] (get-in effects [:http-xhrio :on-success]))
  
  ;; Framework will call this function to insert inferred dispatch to next handler in chain
  :set-dispatch   (fn [effects dispatch] (assoc-in effects [:http-xhrio :on-success] dispatch))  
}]
```

Add it through the start function like this:

```clojure
(k/start!  {:chain-links    [chain-config-map-1 chain-config-map-2]
            :root-component [my-root-reagent-component]
            ...})
```

## Browser navigation

Using URL strings in your links and navigation is error prone and quickly becomes a maintenance problem. Therefore, kee-frame encourages you to only interact with route data instead of concrete URLs. It provides 2 abstractions to help you with that:

The `kee-frame.core/path-for` function accepts a bidi route and returns a URL string:

`(k/path-for [:todos :id 14]) => "/todos/14"`

Kee-frame also includes a re-frame effect for triggering a browser navigation, after all navigation is a side effect. The effect is `:navigate-to` and it accepts a bidi route. The example below shows a handler that receives some data and navigates to the view page for those data.

```clojure      
(reg-event-fx :todo-added
              (fn [_ [_ todo]]
                {:db          (update db :todos conj todo)
                 :navigate-to [:todo :id (:id todo)]]})) ;; "/todos/14"
```

## Routing in your views

Most apps need to different views for different URLs. This isn't too hard to solve in re-frame, just subscribe to your route and implement your dispatch logic like this:

```clojure      
(defn main-view []
  (let [route (subscribe [:kee-frame/route])]
    (fn []
      [:div
       (case (:handler @route)
         :index [index-page]
         :orders [orders-page])])))
```

Kee-frame provides a simple helper to do this:

```clojure
(defn main-view []
  [k/switch-route (fn [route] (:handler route))
   :index [index-page] ;; Explicit call to reagent component, ignoring route data
   :orders orders-page]) ;; Orders page will receive the route data as its parameter because of missing []
```

It looks pretty much the same, only more concise. But it does help you with a few subtle but important things:

* Forces you into a known working pattern
* No explicit reference to the route. The first argument to `switch-route` is a function that accepts the route and returns the value you are dispatching on
* If you pass only a function reference to your reagent components (no surrounding []), kee-frame will invoke them with the route as the first parameter.
* It will give you nice error messages when you make a mistake.

## Introducing kee-frame into an existing app

Several parts of kee-frame are designed to be opt-in. This means that you can include kee-frame in your project and start using parts of it.

If you want controllers and routes, you need to replace your current routing with kee-frame's routing. If your current routing requires a lot of work
  to fit with the standard bidi routing solution, you may implement a custom router. See the next section for more details.

Alternatively, make your current router dispatch the event `[:kee-frame.router/route-changed route-data]` on every route change. That should enable what you need for the controllers.

## Using a different router implementation (since 0.2.0)

You may not like bidi, or you are already using a different router. In that case, all you have to do is implement your own version of the protocol
`kee-frame.api/Router` and pass it in with the rest of your config:

```clojure
(k/start!  {:router         (->ReititRouter reitit-routes)
            :root-component [my-root-reagent-component]
            ...})
```

[Here are some example (not fully tested) router implementations](https://github.com/ingesolvoll/kee-frame-sample/blob/master/src/cljs/kee_frame_sample/routers.cljs).

If you choose to use a different router than bidi, you also need to use the corresponding routing data format when using `path-for` and the `:navigate-to` effect.

## Server side routes
Kee-frame does not use hash based routing (/#/some-route), URLs look like regular server URLs. I prefer this approach, but it requires a bit of server setup to work perfectly. A React SPA is typically loaded from the `"app"` element inside `index.html` served from the root `/` of your server. If the user navigates to some client route `/leagues/465` and then hits refresh, the server will be unable to match that route as it exists only on the client. We will get a 404 instead of the `index.html` that we need. We want this to work, so that URLs can still be deterministic, even if they exist only on the client.

You can solve this in several ways, the simplest way is to include a wildcard route as the last route on the server. The server should serve `index.html` on any route not found on the server. This works, the downside is that you won't be able to serve a 404 page for non-matched URLs on the server. 

In compojure, the wildcard route would look like this:

```clojure
(GET "*" req {:headers {"Content-Type" "text/html"}
                  :status  200
                  :body    (index-handler req)})
```

## Error messages

Helpful error messages are important to kee-frame. You should not get stuck because of "undefined is not a function". If you make a mistake, kee-frame should make it very clear to you what you did wrong and how you can fix it. If you find pain spots, please post an issue so we can find better solutions.

## Maturity
Reasonably well tested through the demo app and production apps at my work. API might see some breaking changes in the near future, but hopefully not. Eagerly awaiting feedback!

## Credits

The implementation of kee-frame is quite simple, building on rock solid libraries and other people's ideas. The main influence is the [Keechma](https://keechma.com/) framework. It is a superb piece of thinking and work, go check it out! Apart from that, the following libraries make kee-frame possible:

* [re-frame](https://github.com/Day8/re-frame) and [reagent](https://reagent-project.github.io/). The world needs to know about these 2 kings of frontend development, and we all need to contribute to their widespread use. This framework is an attempt in that direction.
* [bidi](https://github.com/juxt/bidi). Simple and easy bidirectional routing. I love bidi and think it fits very well with kee-frame, but I'm considering adding support for more routing libraries.
* [accountant](https://github.com/venantius/accountant). A navigation library that hooks to any routing system. Made my life so much easier when I discovered it.
* [etaoin](https://github.com/igrishaev/etaoin) and [lein-test-refresh](https://github.com/jakemcc/lein-test-refresh). 2 good examples of how powerful Clojure is. Etaoin makes browser integration testing fun again, while lein-test-refresh provides you with a development flow that no other platform will give you.

Thank you!
