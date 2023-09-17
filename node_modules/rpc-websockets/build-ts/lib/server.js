/**
 * "Server" wraps the "ws" library providing JSON RPC 2.0 support on top.
 * @module Server
 */
"use strict";
// @ts-ignore
import { EventEmitter } from "eventemitter3";
import { Server as WebSocketServer } from "ws";
import { v1 as uuidv1 } from "uuid";
import url from "url";
import * as utils from "./utils";
export default class Server extends EventEmitter {
    /**
     * Instantiate a Server class.
     * @constructor
     * @param {Object} options - ws constructor's parameters with rpc
     * @return {Server} - returns a new Server instance
     */
    constructor(options) {
        super();
        /**
         * Stores all connected sockets with a universally unique identifier
         * in the appropriate namespace.
         * Stores all rpc methods to specific namespaces. "/" by default.
         * Stores all events as keys and subscribed users in array as value
         * @private
         * @name namespaces
         * @param {Object} namespaces.rpc_methods
         * @param {Map} namespaces.clients
         * @param {Object} namespaces.events
         */
        this.namespaces = {};
        this.wss = new WebSocketServer(options);
        this.wss.on("listening", () => this.emit("listening"));
        this.wss.on("connection", (socket, request) => {
            const u = url.parse(request.url, true);
            const ns = u.pathname;
            if (u.query.socket_id)
                socket._id = u.query.socket_id;
            else
                socket._id = uuidv1();
            // unauthenticated by default
            socket["_authenticated"] = false;
            // propagate socket errors
            socket.on("error", (error) => this.emit("socket-error", socket, error));
            // cleanup after the socket gets disconnected
            socket.on("close", () => {
                this.namespaces[ns].clients.delete(socket._id);
                for (const event of Object.keys(this.namespaces[ns].events)) {
                    const index = this.namespaces[ns].events[event].sockets.indexOf(socket._id);
                    if (index >= 0)
                        this.namespaces[ns].events[event].sockets.splice(index, 1);
                }
                this.emit("disconnection", socket);
            });
            if (!this.namespaces[ns])
                this._generateNamespace(ns);
            // store socket and method
            this.namespaces[ns].clients.set(socket._id, socket);
            this.emit("connection", socket, request);
            return this._handleRPC(socket, ns);
        });
        this.wss.on("error", (error) => this.emit("error", error));
    }
    /**
     * Registers an RPC method.
     * @method
     * @param {String} name - method name
     * @param {Function} fn - a callee function
     * @param {String} ns - namespace identifier
     * @throws {TypeError}
     * @return {Object} - returns an IMethod object
     */
    register(name, fn, ns = "/") {
        if (!this.namespaces[ns])
            this._generateNamespace(ns);
        this.namespaces[ns].rpc_methods[name] = {
            fn: fn,
            protected: false
        };
        return {
            protected: () => this._makeProtectedMethod(name, ns),
            public: () => this._makePublicMethod(name, ns)
        };
    }
    /**
     * Sets an auth method.
     * @method
     * @param {Function} fn - an arbitrary auth method
     * @param {String} ns - namespace identifier
     * @throws {TypeError}
     * @return {Undefined}
     */
    setAuth(fn, ns = "/") {
        this.register("rpc.login", fn, ns);
    }
    /**
     * Marks an RPC method as protected.
     * @method
     * @param {String} name - method name
     * @param {String} ns - namespace identifier
     * @return {Undefined}
     */
    _makeProtectedMethod(name, ns = "/") {
        this.namespaces[ns].rpc_methods[name].protected = true;
    }
    /**
     * Marks an RPC method as public.
     * @method
     * @param {String} name - method name
     * @param {String} ns - namespace identifier
     * @return {Undefined}
     */
    _makePublicMethod(name, ns = "/") {
        this.namespaces[ns].rpc_methods[name].protected = false;
    }
    /**
     * Marks an event as protected.
     * @method
     * @param {String} name - event name
     * @param {String} ns - namespace identifier
     * @return {Undefined}
     */
    _makeProtectedEvent(name, ns = "/") {
        this.namespaces[ns].events[name].protected = true;
    }
    /**
     * Marks an event as public.
     * @method
     * @param {String} name - event name
     * @param {String} ns - namespace identifier
     * @return {Undefined}
     */
    _makePublicEvent(name, ns = "/") {
        this.namespaces[ns].events[name].protected = false;
    }
    /**
     * Removes a namespace and closes all connections
     * @method
     * @param {String} ns - namespace identifier
     * @throws {TypeError}
     * @return {Undefined}
     */
    closeNamespace(ns) {
        const namespace = this.namespaces[ns];
        if (namespace) {
            delete namespace.rpc_methods;
            delete namespace.events;
            for (const socket of namespace.clients.values())
                socket.close();
            delete this.namespaces[ns];
        }
    }
    /**
     * Creates a new event that can be emitted to clients.
     * @method
     * @param {String} name - event name
     * @param {String} ns - namespace identifier
     * @throws {TypeError}
     * @return {Object} - returns an IEvent object
     */
    event(name, ns = "/") {
        if (!this.namespaces[ns])
            this._generateNamespace(ns);
        else {
            const index = this.namespaces[ns].events[name];
            if (index !== undefined)
                throw new Error(`Already registered event ${ns}${name}`);
        }
        this.namespaces[ns].events[name] = {
            sockets: [],
            protected: false
        };
        // forward emitted event to subscribers
        this.on(name, (...params) => {
            // flatten an object if no spreading is wanted
            if (params.length === 1 && params[0] instanceof Object)
                params = params[0];
            for (const socket_id of this.namespaces[ns].events[name].sockets) {
                const socket = this.namespaces[ns].clients.get(socket_id);
                if (!socket)
                    continue;
                socket.send(JSON.stringify({
                    notification: name,
                    params: params || null
                }));
            }
        });
        return {
            protected: () => this._makeProtectedEvent(name, ns),
            public: () => this._makePublicEvent(name, ns)
        };
    }
    /**
     * Returns a requested namespace object
     * @method
     * @param {String} name - namespace identifier
     * @throws {TypeError}
     * @return {Object} - namespace object
     */
    of(name) {
        if (!this.namespaces[name])
            this._generateNamespace(name);
        const self = this;
        return {
            // self.register convenience method
            register(fn_name, fn) {
                if (arguments.length !== 2)
                    throw new Error("must provide exactly two arguments");
                if (typeof fn_name !== "string")
                    throw new Error("name must be a string");
                if (typeof fn !== "function")
                    throw new Error("handler must be a function");
                return self.register(fn_name, fn, name);
            },
            // self.event convenience method
            event(ev_name) {
                if (arguments.length !== 1)
                    throw new Error("must provide exactly one argument");
                if (typeof ev_name !== "string")
                    throw new Error("name must be a string");
                return self.event(ev_name, name);
            },
            // self.eventList convenience method
            get eventList() {
                return Object.keys(self.namespaces[name].events);
            },
            /**
             * Emits a specified event to this namespace.
             * @inner
             * @method
             * @param {String} event - event name
             * @param {Array} params - event parameters
             * @return {Undefined}
             */
            emit(event, ...params) {
                const socket_ids = [...self.namespaces[name].clients.keys()];
                for (let i = 0, id; id = socket_ids[i]; ++i) {
                    self.namespaces[name].clients.get(id).send(JSON.stringify({
                        notification: event,
                        params: params || []
                    }));
                }
            },
            /**
             * Returns a name of this namespace.
             * @inner
             * @method
             * @kind constant
             * @return {String}
             */
            get name() {
                return name;
            },
            /**
             * Returns a hash of websocket objects connected to this namespace.
             * @inner
             * @method
             * @return {Object}
             */
            connected() {
                const socket_ids = [...self.namespaces[name].clients.keys()];
                return socket_ids.reduce((acc, curr) => (Object.assign(Object.assign({}, acc), { [curr]: self.namespaces[name].clients.get(curr) })), {});
            },
            /**
             * Returns a list of client unique identifiers connected to this namespace.
             * @inner
             * @method
             * @return {Array}
             */
            clients() {
                return self.namespaces[name];
            }
        };
    }
    /**
     * Lists all created events in a given namespace. Defaults to "/".
     * @method
     * @param {String} ns - namespaces identifier
     * @readonly
     * @return {Array} - returns a list of created events
     */
    eventList(ns = "/") {
        if (!this.namespaces[ns])
            return [];
        return Object.keys(this.namespaces[ns].events);
    }
    /**
     * Creates a JSON-RPC 2.0 compliant error
     * @method
     * @param {Number} code - indicates the error type that occurred
     * @param {String} message - provides a short description of the error
     * @param {String|Object} data - details containing additional information about the error
     * @return {Object}
     */
    createError(code, message, data) {
        return {
            code: code,
            message: message,
            data: data || null
        };
    }
    /**
     * Closes the server and terminates all clients.
     * @method
     * @return {Promise}
     */
    close() {
        return new Promise((resolve, reject) => {
            try {
                this.wss.close();
                this.emit("close");
                resolve();
            }
            catch (error) {
                reject(error);
            }
        });
    }
    /**
     * Handles all WebSocket JSON RPC 2.0 requests.
     * @private
     * @param {Object} socket - ws socket instance
     * @param {String} ns - namespaces identifier
     * @return {Undefined}
     */
    _handleRPC(socket, ns = "/") {
        socket.on("message", async (data) => {
            const msg_options = {};
            if (data instanceof ArrayBuffer) {
                msg_options.binary = true;
                data = Buffer.from(data).toString();
            }
            if (socket.readyState !== 1)
                return; // TODO: should have debug logs here
            let parsedData;
            try {
                parsedData = JSON.parse(data);
            }
            catch (error) {
                return socket.send(JSON.stringify({
                    jsonrpc: "2.0",
                    error: utils.createError(-32700, error.toString()),
                    id: null
                }), msg_options);
            }
            if (Array.isArray(parsedData)) {
                if (!parsedData.length)
                    return socket.send(JSON.stringify({
                        jsonrpc: "2.0",
                        error: utils.createError(-32600, "Invalid array"),
                        id: null
                    }), msg_options);
                const responses = [];
                for (const message of parsedData) {
                    const response = await this._runMethod(message, socket._id, ns);
                    if (!response)
                        continue;
                    responses.push(response);
                }
                if (!responses.length)
                    return;
                return socket.send(JSON.stringify(responses), msg_options);
            }
            const response = await this._runMethod(parsedData, socket._id, ns);
            if (!response)
                return;
            return socket.send(JSON.stringify(response), msg_options);
        });
    }
    /**
     * Runs a defined RPC method.
     * @private
     * @param {Object} message - a message received
     * @param {Object} socket_id - user's socket id
     * @param {String} ns - namespaces identifier
     * @return {Object|undefined}
     */
    async _runMethod(message, socket_id, ns = "/") {
        if (typeof message !== "object" || message === null)
            return {
                jsonrpc: "2.0",
                error: utils.createError(-32600),
                id: null
            };
        if (message.jsonrpc !== "2.0")
            return {
                jsonrpc: "2.0",
                error: utils.createError(-32600, "Invalid JSON RPC version"),
                id: message.id || null
            };
        if (!message.method)
            return {
                jsonrpc: "2.0",
                error: utils.createError(-32602, "Method not specified"),
                id: message.id || null
            };
        if (typeof message.method !== "string")
            return {
                jsonrpc: "2.0",
                error: utils.createError(-32600, "Invalid method name"),
                id: message.id || null
            };
        if (message.params && typeof message.params === "string")
            return {
                jsonrpc: "2.0",
                error: utils.createError(-32600),
                id: message.id || null
            };
        if (message.method === "rpc.on") {
            if (!message.params)
                return {
                    jsonrpc: "2.0",
                    error: utils.createError(-32000),
                    id: message.id || null
                };
            const results = {};
            const event_names = Object.keys(this.namespaces[ns].events);
            for (const name of message.params) {
                const index = event_names.indexOf(name);
                const namespace = this.namespaces[ns];
                if (index === -1) {
                    results[name] = "provided event invalid";
                    continue;
                }
                // reject request if event is protected and if client is not authenticated
                if (namespace.events[event_names[index]].protected === true &&
                    namespace.clients.get(socket_id)["_authenticated"] === false) {
                    return {
                        jsonrpc: "2.0",
                        error: utils.createError(-32606),
                        id: message.id || null
                    };
                }
                const socket_index = namespace.events[event_names[index]].sockets.indexOf(socket_id);
                if (socket_index >= 0) {
                    results[name] = "socket has already been subscribed to event";
                    continue;
                }
                namespace.events[event_names[index]].sockets.push(socket_id);
                results[name] = "ok";
            }
            return {
                jsonrpc: "2.0",
                result: results,
                id: message.id || null
            };
        }
        else if (message.method === "rpc.off") {
            if (!message.params)
                return {
                    jsonrpc: "2.0",
                    error: utils.createError(-32000),
                    id: message.id || null
                };
            const results = {};
            for (const name of message.params) {
                if (!this.namespaces[ns].events[name]) {
                    results[name] = "provided event invalid";
                    continue;
                }
                const index = this.namespaces[ns].events[name].sockets.indexOf(socket_id);
                if (index === -1) {
                    results[name] = "not subscribed";
                    continue;
                }
                this.namespaces[ns].events[name].sockets.splice(index, 1);
                results[name] = "ok";
            }
            return {
                jsonrpc: "2.0",
                result: results,
                id: message.id || null
            };
        }
        else if (message.method === "rpc.login") {
            if (!message.params)
                return {
                    jsonrpc: "2.0",
                    error: utils.createError(-32604),
                    id: message.id || null
                };
        }
        if (!this.namespaces[ns].rpc_methods[message.method]) {
            return {
                jsonrpc: "2.0",
                error: utils.createError(-32601),
                id: message.id || null
            };
        }
        let response = null;
        // reject request if method is protected and if client is not authenticated
        if (this.namespaces[ns].rpc_methods[message.method].protected === true &&
            this.namespaces[ns].clients.get(socket_id)["_authenticated"] === false) {
            return {
                jsonrpc: "2.0",
                error: utils.createError(-32605),
                id: message.id || null
            };
        }
        try {
            response = await this.namespaces[ns].rpc_methods[message.method]
                .fn(message.params, socket_id);
        }
        catch (error) {
            if (!message.id)
                return;
            if (error instanceof Error)
                return {
                    jsonrpc: "2.0",
                    error: {
                        code: -32000,
                        message: error.name,
                        data: error.message
                    },
                    id: message.id
                };
            return {
                jsonrpc: "2.0",
                error: error,
                id: message.id
            };
        }
        // client sent a notification, so we won't need a reply
        if (!message.id)
            return;
        // if login middleware returned true, set connection as authenticated
        if (message.method === "rpc.login" && response === true) {
            const s = this.namespaces[ns].clients.get(socket_id);
            s["_authenticated"] = true;
            this.namespaces[ns].clients.set(socket_id, s);
        }
        return {
            jsonrpc: "2.0",
            result: response,
            id: message.id
        };
    }
    /**
     * Generate a new namespace store.
     * Also preregister some special namespace methods.
     * @private
     * @param {String} name - namespaces identifier
     * @return {undefined}
     */
    _generateNamespace(name) {
        this.namespaces[name] = {
            rpc_methods: {
                "__listMethods": {
                    fn: () => Object.keys(this.namespaces[name].rpc_methods),
                    protected: false
                }
            },
            clients: new Map(),
            events: {}
        };
    }
}
