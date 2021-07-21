# kee-frame

[re-frame](https://github.com/Day8/re-frame) with batteries included.  

[![Build Status](https://travis-ci.org/ingesolvoll/kee-frame.svg?branch=master)](https://travis-ci.org/ingesolvoll/kee-frame)

[![Clojars Project](https://img.shields.io/clojars/v/kee-frame.svg)](https://clojars.org/kee-frame)

[![cljdoc badge](https://cljdoc.xyz/badge/kee-frame/kee-frame)](https://cljdoc.xyz/d/kee-frame/kee-frame/CURRENT)

[![project chat](https://img.shields.io/badge/slack-join_chat-brightgreen.svg)](https://clojurians.slack.com/archives/CA7EJ0Y2U)


## Project status (October 2020)

The API and functionality of kee-frame is stable and proven to work.
Pull requests are welcome!
 
## Quick walkthrough
- If you prefer, you can go straight to some [articles](http://ingesolvoll.github.io/tags/kee-frame/) or the [demo app](https://github.com/ingesolvoll/kee-frame-sample)

- Require core namespace
```clojure
(require '[kee-frame.core :as k])
```


- Start your re-frame app and mount it into the DOM with sensible defaults for routing, logging, spec validation, etc.
Call this function on figwheel reload.

```clojure
(k/start!  {:routes         [["/" :live]
                             ["/league/:id/:tab" :league]]
            :app-db-spec    :my-app/db-spec
            :initial-db     {:some-prop true}
            :root-component [my-root-reagent-component]
            :debug?         true})
```

- Declare that you want some data to be loaded when the user navigates to the league page
```clojure      
(k/reg-controller :league
                  {:params (fn [route-data]
                             (when (-> route-data :data :name (= :league))
                               (-> route-data
                                   :path-params
                                   :id)))
                   :start  (fn [ctx id] [:league/load id])})
```

- Declare how to get those data from the server
```clojure      
(k/reg-chain :league/load
            
             (fn [ctx [id]]
               {:http-xhrio {:method          :get
                             :uri             (str "/leagues/" id)}})
            
             (fn [{:keys [db]} [_ league-data]]
               {:db (assoc db :league league-data)}))
```

- You can use a Finite State Machine to handle error paths and complexity, with or without controllers and chains. See FSM doc section for details.

- Make a URL for your `<a href="">` using nothing but data
```clojure
(k/path-for [:league {:id 14 :tab :fixtures}]) => "/league/14/fixtures"
```

- Let your event handler trigger browser navigation as a side effect, using nothing but data
```clojure      
(reg-event-fx :todo-added
              (fn [_ [todo]]
                {:navigate-to [:todo {:id (:id todo)}]]}))
```

- Let the route data decide what view to display
```clojure
(defn main-view []
  [k/switch-route (fn [route] (-> route :data :name))
   :index [index-page] 
   :orders [orders-page]
   nil [:div "Loading..."])
```

## Benefits of leaving the URL in charge

Kee-frame wants you to focus on the URL and let it contain all data necessary to load a view. When you let 
the URL guide you app architecture like this, strange things start to happen: 
* Back/forward and bookmarking in general just works. The internet is back!
* Figwheel reloading gets even better
* UI code gets more declarative
* Cohesion goes up, coupling goes down


## That's it!
You've reached the end of the quick summary. Keep reading for a more in-depth guide!

## Articles

[Learning kee-frame in 5 minutes](http://ingesolvoll.github.io/posts/2018-04-01-learning-kee-frame-in-5-minutes/)

[Introduction and background for kee-frame controllers](http://ingesolvoll.github.io/posts/2018-04-01-kee-frame-putting-the-url-in-charge/)

[Controller tricks](http://ingesolvoll.github.io/posts/2018-06-18-kee-frame-controller-tricks/)

## Demo application
I made a simple demo app showing football results. Have a look around, and observe how all data loading just works while navigating and refreshing the page.

[Online demo app](http://kee-frame-sample.herokuapp.com/) 

[Demo app source](https://github.com/ingesolvoll/kee-frame-sample)

Feel free to clone the demo app and do some figwheelin' with it!

## Support
Contact the author on [Twitter](https://twitter.com/ingesol) or join the discussion on [Slack](https://clojurians.slack.com/messages/kee-frame). Don't be afraid to create [issues](https://github.com/ingesolvoll/kee-frame/issues). Lack of user friendliness is also a bug!

## Installation
There are 2 simple options for bootstrapping your project:

### 1. Manual installation
Add the following dependency to your `project.clj` file:
```clojure
[kee-frame "0.4.0"]
```
### 2. Luminus template
[Luminus](http://www.luminusweb.net) is a framework that makes it easy to get started with web app development
in clojure. It comes with kee-frame if you do this:

```
lein new luminus your-app-name-here +kee-frame
``` 

## API stability
This library tries hard conform to the high standards of many Clojure libraries, by not breaking backwards compatibility.
I believe this is very important, an application made several years ago should be able to upgrade with close to zero effort.

The kee-frame API has remained stable since the launch in early 2018. Here is a list of important/breaking changes:
* 0.3.0: Reitit replaces Bidi as the default routing library. Causes a breaking change in the data structures of routes and route matches. [The bidi router implementation can be found here, it's easy to fit back in.](https://github.com/ingesolvoll/kee-frame-sample/blob/master/src/cljs/kee_frame_sample/routers.cljs)
* 0.3.4: Changes in log configuration, might render unexpected results in more/less logging for existing projects.
* 0.4.0: FSM API introduced. No breaking API change, additions only.
* 0.4.0: Websocket API moved out to separate library, to reduce bundle size. Requires separate dependency.

## Getting started

The `kee-frame.core` namespace contains the public API. It also contains wrapped versions of `reg-event-db` and `reg-event-fx`.

```clojure
(require '[kee-frame.core :as k :refer [reg-controller reg-chain reg-event-db reg-event-fx]])
```

## Routes
Kee-frame uses [reitit](https://github.com/metosin/reitit) for routing (since 0.3.0). Read the reitit docs for more details on the syntax, here are the routes from the demo app:

```clojure
(def routes [["/" :live]
             ["/league/:id/:tab" :league]])
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

You can set the `hash-routing?` property to `true` for `/#/todos/1` style urls. Otherwise kee-frame defaults to using the browser
history without the hash. The hash bit should not be included in your route definition, kee-frame strips it off before matching
the route.

By default an unknown URL/route causes an error. You can provide a string URL under config key `:not-found` 
as your default 404 when no route is found.

If you provide `:root-component`, kee-frame will render that component in the DOM element with id "app". Make sure you have such an element in your index.html. You are free to do the initial rendering yourself if you want, just skip this setting. If you use this feature, make sure that `k/start!` is called every time figwheel reloads your code. 

The `:log` option accepts a timbre log configuration map. See the [Logging](#logging) section for more details.

The `debug?` and `debug-config` options were replaced by the `:log` option in 0.4.1. 

If you provide an `app-db-spec`, the framework will let you know when a bug in your event handler is trying to corrupt your DB structure. This is incredibly useful, so you should put down the effort to spec up your db!

You can override kee-frame's behaviour on route change through the `:route-change-event` option.
Just specify the id of the event you want to use. One possible case is to perform some gatekeeping
before executing the controllers for that route. If route execution is ok, 
the event could dispatch to kee-frame's built in `:kee-frame.router/route-changed`.

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
                 :start  [:leagues/load]}) ;; The route params will be appended to this vector, as the first event param.
```

## Controller state transitions
This rules of controller states are stolen entirely from Keechma. 

NOTE: `false` is not treated as falsy in controllers, it is considered as any other value. The explicitly negative value that stops controllers is `nil`.

The controller rules are:
* When previous and current parameter values are the same, do nothing
* When previous parameter was nil and current is not nil, call `start`.
* When previous parameter was not nil and current is nil, call `stop`.
* When both previous and current are not nil, but different, call `stop`, then `start`.

All controllers `:params` fns are called every time the route changes. Therefore `:params` should return `nil` for routes on which it should not be started.

## Event chains

Kee-frame uses [re-chain](https://github.com/ingesolvoll/re-chain) to chain event handlers together for increased readability
less boilerplate for common cases. See the example below for how to use it, visit the [re-chain](https://github.com/ingesolvoll/re-chain) 
page for details and documentation

```clojure      
(k/reg-chain :league/load
            
             (fn [ctx [id]]
               {:http-xhrio {:method          :get
                             :uri             (str "/leagues/" id)}})
            
             (fn [{:keys [db]} [_ league-data]]
               {:db (assoc db :league league-data)}))
```
 

## Browser navigation

Using URL strings in your links and navigation is error prone and quickly becomes a maintenance problem. Therefore, kee-frame encourages you to only interact with route data instead of concrete URLs. It provides 2 abstractions to help you with that:

The `kee-frame.core/path-for` function accepts a reitit route and returns a URL string:

`(k/path-for [:todos {:id 14}]) => "/todos/14"`

Kee-frame also includes a re-frame effect for triggering a browser navigation, after all navigation is a side effect. The effect is `:navigate-to` and it accepts a reitit route. The example below shows a handler that receives some data and navigates to the view page for those data.

```clojure      
(reg-event-fx :todo-added
              (fn [_ [todo]]
                {:db          (update db :todos conj todo)
                 :navigate-to [:todo :id (:id todo)]]})) ;; "/todos/14"
```

See [this issue](https://github.com/ingesolvoll/kee-frame/issues/64) for some hints on how to use query parameters in your browser navigation.

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
  [k/switch-route (fn [route] (-> route :data :name))
   :index [index-page] ;; Explicit call to reagent component, ignoring route data
   :orders orders-page]) ;; Orders page will receive the route data as its parameter because of missing []
```

It looks pretty much the same, only more concise. But it does help you with a few subtle but important things:

* Forces you into a known working pattern
* No explicit reference to the route. The first argument to `switch-route` is a function that accepts the route and returns the value you are dispatching on
* If you pass only a function reference to your reagent components (no surrounding []), kee-frame will invoke them with the route as the first parameter.
* It will give you nice error messages when you make a mistake.

## Finite State Machines (alpha since 0.4.0)

#### FSM API is marked as alpha, as it is more likely to receive breaking changes for the coming months.

Initial source code and design of API by https://github.com/zalky

Most people are not using state machines in their daily programming tasks. Or actually they are, it's just that the state machines are hidden
inside normal code, incomplete and filled with fresh custom made bugs. A `{:loading true}` here, a missing `:on-failure` there. You may get
it right eventually, but it's hard to read the distributed state logic and it is easy to mess it up later.

A kee-frame event chain is a kind of state machine. But in the examples, it only handles the
happy path of successful HTTP requests. It does not have a good answer to error handling, and you have to make custom solutions for displaying
the state of an ongoing process (retrying, loading, sending, failed etc).

Here's the structure of an FSM in kee-frame:

```clojure
(require '[kee-frame.fsm.alpha :as fsm])

(def my-http-request-fsm
   {:id    :my-http-request
    :start ::loading
    :stop  ::loaded
    :fsm   {::loading        {[::fsm/on-enter]      {:dispatch [[:contact-the-server]]}
                              [:server-responded]   {:to ::loaded}
                              [:default-on-failure] {:to ::loading-failed}}
            ::loading-failed {[::fsm/timeout 10000] {:to ::loading}}}})
```

The `:start` param (required) identifies the initial state of the FSM. 
If you specify a `:stop` state, the machine will halt when it enters that state.

The `:fsm` param is the interesting part. It's a map from states to available transitions. A transition key/value pair
consists of a re-frame event and information about what happens to the FSM state when that event is seen.
The actual event vector will usually contain more items, the FSM considers it a match if the event starts
with the vector provided in the FSM.

If an event is matched, the right-side map decides what happens next. It can transition into a new state,
or dispatch re-frame events. Both are optional.

There are 2 special "events" here:
- `::fsm/timeout` triggers the specified number of ms after entering that state. Will not trigger if state has changed.
- `::fsm/on-enter` triggers immediately when entering that state.

FSMs can be started and stopped like this:

```clojure
(rf/dispatch [::fsm/start my-http-request-fsm])

(rf/dispatch [::fsm/stop my-http-request-fsm])
```

Controllers support returning FSM maps instead of event vectors. Like this:

```clojure      
(defn league-fsm [id]
  {:id :league-fsm
   ....})

(k/reg-controller :league
                  {:params (fn [route-data] ...)
                   :start  (fn [ctx id] (league-fsm id)})
```

This FSM will be started and stopped by the controller start/stop lifecycle. See the demo app for extended examples.

### Depending on FSM state
There are multiple ways you can utilize FSMs in your rendering.

You can start and stop the FSM manually by dispatching the events above, and roll your own rendering the way you prefer
it. The main benefit is being able to simply subscribe to the current state of the FSM.

Next level is using the `kee-frame.fsm/with-fsm` macro to automatically control the lifecycle of your FSM, like this:

```clojure
(fsm/with-fsm [fsm (league-fsm id)]
  (let [fsm-state (f/subscribe [::fsm/state fsm])]
    [:div "My fsm state is " @fsm-state]))
```

For a fully declarative FSM UI, use the `fsm/render` component. It renders a materialized view of the FSM state.
The `fsm/step` multimethod must be implemented for all possible states of the FSM.

Here's an example:

```clojure
(defn light-switch-fsm [city] {:id    (str "switch-for-" city)
                               :start ::off
                               :fsm   {::off {[::turn-on]  {:to ::on}}
                                       ::on  {[::turn-off] {:to ::off}}}})

(defmethod fsm/step ::off
  [fsm city]
  [:div "Lights are off in " city])

(defmethod fsm/step ::on
  [fsm city]
  [:div "Lights are on in " city])

[fsm/render light-switch-fsm "London"]
```

## Logging

https://github.com/ptaoussanis/timbre is included for logging. The `kee-frame.core/start!` function accepts an optional timbre config map. The default
config logs to browser console at level `:info`. Here's an example config you could pass in:

```clojure
{:log {:level        :debug
       :ns-blacklist ["kee-frame.event-logger"]}
...
}
```

Kee-frame uses debug logging as an aid in debugging applications. This means that turning on all logging
will get quite noisy. You might want to include some of these namespaces in your blacklist if you don't need them:
- `kee-frame.event-logger`
- `kee-frame.fsm.alpha`

IMPORTANT: For debug logging to show up in Chrome you need to enable "Verbose" logging in Chrome dev tools. See https://github.com/ptaoussanis/timbre/issues/249


## Introducing kee-frame into an existing app

Several parts of kee-frame are designed to be opt-in. This means that you can include kee-frame in your project and start using parts of it.

If you want controllers and routes, you need to replace your current routing with kee-frame's routing. If your current routing requires a lot of work
  to fit with the standard reitit routing solution, you may implement a custom router. See the next section for more details.

Alternatively, make your current router dispatch the event `[:kee-frame.router/route-changed route-data]` on every route change. That should enable what you need for the controllers.

## Using a different router implementation

You may not like reitit, or you are already using a different router. In that case, all you have to do is implement your own version of the protocol
`kee-frame.api/Router` and pass it in with the rest of your config:

```clojure
(k/start!  {:router         (->BidiRouter bidi-style-routes)
            :root-component [my-root-reagent-component]
            ...})
```

[Here are some example (not fully tested) router implementations](https://github.com/ingesolvoll/kee-frame-sample/blob/master/src/cljs/kee_frame_sample/routers.cljs). If you are upgrading from a pre 0.3.0
version of kee-frame, you probably want to keep your current bidi routes. The old bidi router implementation can be found here, just make a copy
and use it as your `:router`.

If you choose to use a different router than reitit, you also need to use the corresponding routing data format when using `path-for` and the `:navigate-to` effect.

## Server side routes
If you want to use links without hashes (`/some-route` instead of `/#/some-route`), you need a bit of server setup for it to work perfectly. 
A React SPA is typically loaded from the `"app"` element inside `index.html` served from the root `/` of your server. 
If the user navigates to some client route `/leagues/465` and then hits refresh, the server will be unable to match that 
route as it exists only on the client. We will get a 404 instead of the `index.html` that we need. We want this to work, 
so that URLs can still be deterministic, even if they exist only on the client.

You can solve this in several ways, the simplest way is to include a wildcard route as the last route on the server. The server should serve `index.html` on any route not found on the server. This works, the downside is that you won't be able to serve a 404 page for non-matched URLs on the server. 

In compojure, the wildcard route would look like this:

```clojure
(GET "*" req {:headers {"Content-Type" "text/html"}
                  :status  200
                  :body    (index-handler req)})
```

## Screen size breakpoints

Most web apps benefit from having direct access to information about the size and orientation of the screen. Kee-frame
ships with the nice and simple [breaking-points](https://github.com/gadfly361/breaking-point) library that provides 
subscriptions for the screen properties you're interested in.

The screen breakpoints are completely configurable, you can pass your preferred ones to the `start!` function. The ones
listed in the example below are the defaults, so if you're happy with those you can just pass `true` to the `:screen`
parameter. If you omit it altogether, or pass `false` - the screen breakpoints will be disabled.

```clojure
(k/start!  {:screen {:breakpoints 
                        [:mobile
                         768
                         :tablet
                         992
                         :small-monitor
                         1200
                         :large-monitor]
                     :debounce-ms 166}
              ;; Other settings here
              })
```

The subscriptions available are:

```clojure
(rf/subscribe [:breaking-point.core/screen-width]) ;; will be an int
(rf/subscribe [:breaking-point.core/screen-height]) ;; will be an int
(rf/subscribe [:breaking-point.core/screen]) ;; will be one of the following: :mobile, :tablet, :small-monitor, :large-monitor

(rf/subscribe [:breaking-point.core/orientation]) ;; will be either :portrait or :landscape
(rf/subscribe [:breaking-point.core/landscape?]) ;; true if width is >= height
(rf/subscribe [:breaking-point.core/portrait?]) ;; true if height > width

;; these will be based on the breakpoint names that you provide
(rf/subscribe [:breaking-point.core/mobile?]) ;; true if screen-width is < 768
(rf/subscribe [:breaking-point.core/tablet?]) ;; true if screen-width is >= 768 and < 992
(rf/subscribe [:breaking-point.core/small-monitor?]) ;; true if window width is >= 992 and < 1200
(rf/subscribe [:breaking-point.core/large-monitor?]) ;; true if window width is >= 1200
```
## Websockets (removed since 0.4.0)

I believe it was a mistake to introduce websockets into kee-frame. It's not what this library is
about. The code has been put in a separate repo, and can be used as before. See docs at
https://github.com/ingesolvoll/kee-frame-sockets

If you are a user of the websocket code and you find that the new lib has bugs, 
please downgrade to kee-frame 0.3.4 and submit an issue.

## Error messages

Helpful error messages are important to kee-frame. You should not get stuck because of "undefined is not a function". If you make a mistake, kee-frame should make it very clear to you what you did wrong and how you can fix it. If you find pain spots, please post an issue so we can find better solutions.

## React error boundaries

If you are unfamiliar with error boundaries, you can read the [docs](https://reactjs.org/docs/error-boundaries.html). After reading [this](https://lilac.town/writing/modern-react-in-cljs-error-boundaries/), I decided to include Will's code snippet
in kee-frame. Usage:

```clojure
[kee-frame.error/boundary
  [your-badly-behaving-component-here props]]
```

This will print JS errors inside the component on screen instead of breaking the whole rendering tree.
You can optionally include your own error-handling component function as the first param.

[Example usage from demo app](https://github.com/ingesolvoll/kee-frame-sample/blob/master/src/cljs/kee_frame_sample/core.cljs#L26)

## Scroll behavior on navigation
In a traditional static website, the browser handles the scrolling for you nicely. Meaning that when you navigate back
and forward, the browser "remembers" how far down you scrolled on the last visit. This is convenient for many websites,
so Kee-frame utilizes a third-party JS lib to get this behavior for a SPA. The only thing you need to do is this in
your main namespace:

```clojure
(:require [kee-frame.scroll])
```

## Credits

The implementation of kee-frame is quite simple, building on rock solid libraries and other people's ideas. The main influence is the [Keechma](https://keechma.com/) framework. It is a superb piece of thinking and work, go check it out! Apart from that, the following libraries make kee-frame possible:

* [re-frame](https://github.com/Day8/re-frame) and [reagent](https://reagent-project.github.io/). The world needs to know about these 2 kings of frontend development, and we all need to contribute to their widespread use. This framework is an attempt in that direction.
* [reitit](https://github.com/metosin/reitit). Simple and easy bidirectional routing, with very little noise in the syntax.
* [accountant](https://github.com/venantius/accountant). A navigation library that hooks to any routing system. Made my life so much easier when I discovered it.
* [etaoin](https://github.com/igrishaev/etaoin) and [lein-test-refresh](https://github.com/jakemcc/lein-test-refresh). 2 good examples of how powerful Clojure is. Etaoin makes browser integration testing fun again, while lein-test-refresh provides you with a development flow that no other platform will give you.

Thank you!
