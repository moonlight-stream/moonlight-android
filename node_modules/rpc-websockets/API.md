# API Documentation

This is a JavaScript classes documentation which describes both client and server instance creation and management.

#### Table of Contents
* [Client](#client)
    * [Constructor](#new-websocketaddress-options---client)
    * [connect](#wsconnect)
    * [call](#wscallmethod-params-timeout-ws_options---promise)
    * [login](#wsloginparams---promise)
    * [listMethods](#wslistmethods---promise)
    * [notify](#wsnotifymethod-params)
    * [subscribe](#wssubscribeevent---promise)
    * [unsubscribe](#wsunsubscribeevent---promise)
    * [close](#wsclosecode-data)
    * [event:open](#event-open)
    * [event:error](#event-error)
    * [event:close](#event-close)
    * [event:notification](#event-notification)
* [Server](#server)
    * [Constructor](#new-websocketserveroptions---server)
    * [register](#serverregistermethod-handler-namespace---rpcmethod)
    * [setAuth](#serversetauthhandler-namespace)
    * [event](#servereventname-namespace)
    * [emit](#serveremitname-params)
    * [eventList](#servereventlistnamespace---array)
    * [of](#serverofname---namespace)
    * [createError](#servercreateerrorcode-message-data---object)
    * [closeNamespace](#serverclosenamespacens---promise)
    * [close](#serverclose---promise)
    * [event:listening](#event-listening)
    * [event:connection](#event-connection)
    * [event:disconnection](#event-disconnection)
    * [event:close](#event-close-1)
    * [event:socket-error](#event-socket-error)
    * [event:error](#event-error-1)
* [RPCMethod](#rpcmethod)
    * [protected](#rpcmethodprotected)
    * [public](#rpcmethodpublic)
* [Namespaces](#namespaces)
    * [register](#namespaceregistermethod-handler---rpcmethod)
    * [event](#namespaceeventname)
    * [name](#get-namespacename---string)
    * [connected](#namespaceconnected---object)
    * [emit](#namespaceemitname-params)
    * [eventList](#namespaceeventlist---array)
    * [clients](#namespaceclients---array)

## Migrating to 3.x/4.x

Departing from version 2.x, there's been some minor API changes. A breaking change is a server.eventList method, which is not a getter method anymore, because of the inclusion of a namespaces system throughout the library. Other methods will work seamlessly.

## Migrating to 7.x

`client.login` now throws an error in case of failed login. Enclose the code using that method in a `try/catch` block to mitigate unhandled exceptions.

## Client

```js
var WebSocket = require('rpc-websockets').Client
var ws = new WebSocket('ws://localhost:8080')
```

### new WebSocket(address[, options]) -> Client

Instantiate a WebSocket client.

Parameters:
* `address` {String}: The URL of the WebSocket server. The URL path portion resolves to a server namespace. If the URL query key `socket_id` exists, it will be used as a socket identifier. Defaults to 'ws://localhost:8080'.
* `options` {Object}: Client options that are also forwarded to `ws`.
  * `autoconnect` {Boolean}: Client autoconnect upon Client class instantiation. Defaults to `true`.
  * `reconnect` {Boolean}: Whether client should reconnect automatically once the connection is down. Defaults to `true`.
  * `reconnect_interval` {Number}: Time between adjacent reconnects. Defaults to `1000`.
  * `max_reconnects` {Number}: Maximum number of times the client should try to reconnect. Defaults to `5`. `0` means unlimited.
  * Any other option allowed in <a href="https://github.com/websockets/ws/blob/master/doc/ws.md#new-websocketaddress-protocols-options" target="_blank">Node WebSocket</a>
* `generate_request_id` {Function} Custom function to generate request id instead of simple increment by default. Passes `method` and `params` to parameters.

### ws.connect()

Connects to a previously defined server if not connected already. Should only be used in case `autoconnect` was disabled.

### ws.call(method[, params[, timeout[, ws_options]]]) -> Promise

Calls a registered RPC method on server. Resolves once the response is ready. Throws if an RPC error was received. Throws if `method` resolves to `undefined` (JSON knows no `undefined` type, so the response will neither have `result` nor `error` properties which violates JSON-RPC 2.0).

Parameters:
* `method` {String}: An RPC method name to run on server-side.
* `params` {Object|Array}: Optional parameter(s) to be sent along the request.
* `timeout` {Number}: Optional RPC reply timeout in milliseconds.
* `ws_options` {Object}: Optional parameters passed to ws. Not available on web browsers.
  * `compress` {Boolean}: Specifies whether data should be compressed or not. Defaults to true when permessage-deflate is enabled.
  * `binary` {Boolean}: Specifies whether data should be sent as a binary or not. Default is autodetected.
  * `mask` {Boolean} Specifies whether data should be masked or not. Defaults to true when websocket is not a server client.
  * `fin` {Boolean} Specifies whether data is the last fragment of a message or not. Defaults to true.

### ws.login(params) -> Promise

Logins with the other side of the connection.

Parameters are used for authentication with another side of the connection and are user-defined.
Throws with a detailed message if the login fails.

### ws.listMethods() -> Promise

Fetches a list of client's methods registered on server.

### ws.notify(method[, params])

Sends a JSON-RPC 2.0 notification to server.

Parameters:
* `method` {String}: An RPC method name to run on server-side.
* `params` {Object|Array}: Optional parameter(s) to be sent along the request.

### ws.subscribe(event) -> Promise

Subscribes for a defined event. If single event is provided, it throws in case of errors. If multiple events are provided, it returns status data from the server.

Parameters:
* `event` {String|Array}: Event name.

### ws.unsubscribe(event) -> Promise

Unsubscribes from a defined event. If single event is provided, it throws in case of errors. If multiple events are provided, it returns status data from the server.

Parameters:
* `event` {String|Array}: Event name.

### ws.close([code[, data]])

Closes a WebSocket connection gracefully.

Parameters:
* `code` {Number}: Socket close code.
* `data` {String}: Optional data to be sent to socket before closing.

### Event: 'open'

Emits when the connection is opened and ready for use.

### Event: 'error'

* &lt;Error&gt;

Emits when a socket error is raised.

### Event: 'close'

Emits when the connection is closed.

### Event: &lt;Notification&gt;

* &lt;Object&gt;

Emits a notification event with possible parameters a client has subscribed to once the server sends it.

Example:
```js
ws.subscribe('feedUpdated')

ws.on('feedUpdated', handlerFunction)
```

## Server

```js
var WebSocketServer = require('rpc-websockets').Server

var server = new WebSocketServer({
  port: 8080,
  host: 'localhost'
})
```

### new WebSocketServer([options]) -> Server

Instantiate a WebSocket server.

Parameters:
* `options` {Object}: Server options that are also forwarded to `ws`.
  * `port` {Number}: Port number on which the server will listen for incoming requests.
  * `host` {String}: Address on which the server will listen for incoming requests.

Once the Server class is instantiated, you can use a `ws` library's instance via server.wss object.

### server.register(method, handler[, namespace]) -> RPCMethod

Registers an RPC method and returns the RPCMethod object to manage method permissions.

Parameters:
* `method` {String}: RPC method name.
* `handler` {Function}: RPC function that will be fired with a signature of `([params[, socket_id]])` once the method is called. Should not return `undefined` (see [ws.call](#wscallmethod-params-timeout-ws_options---promise)).
* `namespace` {String}: Namespace identifier. Defaults to ```/```.

### server.setAuth(handler[, namespace])

Sets a user-defined auth method. The handler function must return boolean true on auth success and boolean false on auth failure.

Parameters:
* `handler` {Function}: An auth function that will be used when the client calls the `login` method. Must return boolean true on auth success and boolean false on auth failure.
* `namespace` {String}: Namespace identifier. Defaults to ```/```.

### server.event(name[, namespace]) -> RPCMethod

Creates a new event that can be emitted to clients and returns the RPCMethod object to manage method permissions.

Parameters:
* `name` {String}: Name of the event.
* `namespace` {String}: Namespace identifier. Defaults to ```/```.

### server.emit(name[, ...params])

Emits a created event to clients.

Parameters:
* `name` {String}: Name of the event.
* `...params`: Parameters forwarded to clients. If an object (```{ }```) is provided, parameters delivered to a client will appear in a by-name fashion.

### server.eventList([namespace]) -> Array

Lists all created events.

Parameters:
* `namespace`: Namespace identifier. Defaults to ```/```.

### server.of(name) -> Namespace

Returns a Namespace object initialized by the provided pathname upon connecting (eg: ```/chat```).
Defaults to ```/```.

Parameters:
* `name` {String}: Namespace identifier.

More information on Namespaces below.

### server.createError(code, message[, data]) -> Object

Creates a structured error that can be thrown in a .register callback.

Parameters:
* `code` {Number}: Indicates the error type that occurred.
* `message` {String}: Provides a short description of the error.
* `data` {String|Object}: Details containing additional information about the error.

### server.closeNamespace(ns) -> Promise

Closes the given namespace and terminates all its clients.

### server.close() -> Promise

Closes the server and terminates all clients.

### Event: 'listening'

Emits when the server has started listening for requests.

### Event: 'connection'

* `socket` &lt;ws.WebSocket&gt;
* `request` &lt;http.IncomingMessage&gt;

Emits when the client has connected.

### Event: 'disconnection'

* `socket` &lt;ws.WebSocket&gt;

Emits when the client has disconnected.

### Event: 'close'

Emits when the server has closed.

### Event: 'socket-error'

* `socket` &lt;ws.WebSocket&gt;
* &lt;Error&gt;

Emits when a websocket error is raised.

### Event: 'error'

* &lt;Error&gt;

Emits when a server error is raised.

## IMethod

An object which is returned by .register. Includes functions that can require client authentication.

### IMethod.protected()

Marks an RPC method as protected. The method will only be reachable if the client has successfully authenticated with .login.

### IMethod.public()

Marks an RPC method as public. All clients, both authenticated and anonymous will be able to use the method. This is set by default on .register.

## IEvent

An object which is returned by .event. Includes functions that can require client authentication.

### IEvent.protected()

Marks an event as protected. The method will only be reachable if the client has successfully authenticated with .login.

### IEvent.public()

Marks an event as public. All clients, both authenticated and anonymous will be able to subscribe to the event. This is set by default on .event.

## Namespaces

Namespace represents a pool of sockets connected under a given scope identified by a pathname (eg: ```/chat```). Basically borrows ideas from ```socket.io```.

### namespace.register(method, handler) -> RPCMethod

A convenience method for server.register using this namespace.

### namespace.event(name)

A convenience method for server.event using this namespace.

### **get** namespace.name -> String

Returns a namespace identifier.

### namespace.connected() -> Object

Returns a hash of websocket objects connected to this namespace, identified by ```id```.

### namespace.emit(name[, ...params])

Emits a created event to clients connected to this namespace.

Parameters:
* `name` {String}: Name of the event.
* `...params`: Parameters forwarded to clients in this namespace.

### namespace.eventList -> Array

A convenience method that lists all created events in this namespace.

### namespace.clients() -> Array

Returns a list of client unique identifiers connected to this namespace.
