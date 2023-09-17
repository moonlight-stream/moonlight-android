/**
 * "Server" wraps the "ws" library providing JSON RPC 2.0 support on top.
 * @module Server
 */
import { EventEmitter } from "eventemitter3";
import NodeWebSocket, { Server as WebSocketServer } from "ws";
interface INamespaceEvent {
    [x: string]: {
        sockets: Array<string>;
        protected: boolean;
    };
}
interface IMethod {
    public: () => void;
    protected: () => void;
}
interface IEvent {
    public: () => void;
    protected: () => void;
}
interface IRPCMethodParams {
    [x: string]: any;
}
interface IRPCMethod {
    [x: string]: {
        fn: (params: IRPCMethodParams, socket_id: string) => any;
        protected: boolean;
    };
}
interface IClientWebSocket extends NodeWebSocket {
    _id: string;
    _authenticated: boolean;
}
export default class Server extends EventEmitter {
    private namespaces;
    wss: InstanceType<typeof WebSocketServer>;
    /**
     * Instantiate a Server class.
     * @constructor
     * @param {Object} options - ws constructor's parameters with rpc
     * @return {Server} - returns a new Server instance
     */
    constructor(options: NodeWebSocket.ServerOptions);
    /**
     * Registers an RPC method.
     * @method
     * @param {String} name - method name
     * @param {Function} fn - a callee function
     * @param {String} ns - namespace identifier
     * @throws {TypeError}
     * @return {Object} - returns an IMethod object
     */
    register(name: string, fn: (params: IRPCMethodParams, socket_id: string) => void, ns?: string): IMethod;
    /**
     * Sets an auth method.
     * @method
     * @param {Function} fn - an arbitrary auth method
     * @param {String} ns - namespace identifier
     * @throws {TypeError}
     * @return {Undefined}
     */
    setAuth(fn: (params: IRPCMethodParams, socket_id: string) => Promise<boolean>, ns?: string): void;
    /**
     * Marks an RPC method as protected.
     * @method
     * @param {String} name - method name
     * @param {String} ns - namespace identifier
     * @return {Undefined}
     */
    private _makeProtectedMethod;
    /**
     * Marks an RPC method as public.
     * @method
     * @param {String} name - method name
     * @param {String} ns - namespace identifier
     * @return {Undefined}
     */
    private _makePublicMethod;
    /**
     * Marks an event as protected.
     * @method
     * @param {String} name - event name
     * @param {String} ns - namespace identifier
     * @return {Undefined}
     */
    private _makeProtectedEvent;
    /**
     * Marks an event as public.
     * @method
     * @param {String} name - event name
     * @param {String} ns - namespace identifier
     * @return {Undefined}
     */
    private _makePublicEvent;
    /**
     * Removes a namespace and closes all connections
     * @method
     * @param {String} ns - namespace identifier
     * @throws {TypeError}
     * @return {Undefined}
     */
    closeNamespace(ns: string): void;
    /**
     * Creates a new event that can be emitted to clients.
     * @method
     * @param {String} name - event name
     * @param {String} ns - namespace identifier
     * @throws {TypeError}
     * @return {Object} - returns an IEvent object
     */
    event(name: string, ns?: string): IEvent;
    /**
     * Returns a requested namespace object
     * @method
     * @param {String} name - namespace identifier
     * @throws {TypeError}
     * @return {Object} - namespace object
     */
    of(name: string): {
        register(fn_name: string, fn: (params: IRPCMethodParams) => void): IMethod;
        event(ev_name: string): IEvent;
        readonly eventList: string[];
        /**
         * Emits a specified event to this namespace.
         * @inner
         * @method
         * @param {String} event - event name
         * @param {Array} params - event parameters
         * @return {Undefined}
         */
        emit(event: string, ...params: Array<string>): void;
        /**
         * Returns a name of this namespace.
         * @inner
         * @method
         * @kind constant
         * @return {String}
         */
        readonly name: string;
        /**
         * Returns a hash of websocket objects connected to this namespace.
         * @inner
         * @method
         * @return {Object}
         */
        connected(): {};
        /**
         * Returns a list of client unique identifiers connected to this namespace.
         * @inner
         * @method
         * @return {Array}
         */
        clients(): {
            rpc_methods: IRPCMethod;
            clients: Map<string, IClientWebSocket>;
            events: INamespaceEvent;
        };
    };
    /**
     * Lists all created events in a given namespace. Defaults to "/".
     * @method
     * @param {String} ns - namespaces identifier
     * @readonly
     * @return {Array} - returns a list of created events
     */
    eventList(ns?: string): string[];
    /**
     * Creates a JSON-RPC 2.0 compliant error
     * @method
     * @param {Number} code - indicates the error type that occurred
     * @param {String} message - provides a short description of the error
     * @param {String|Object} data - details containing additional information about the error
     * @return {Object}
     */
    createError(code: number, message: string, data: string | object): {
        code: number;
        message: string;
        data: string | object;
    };
    /**
     * Closes the server and terminates all clients.
     * @method
     * @return {Promise}
     */
    close(): Promise<void>;
    /**
     * Handles all WebSocket JSON RPC 2.0 requests.
     * @private
     * @param {Object} socket - ws socket instance
     * @param {String} ns - namespaces identifier
     * @return {Undefined}
     */
    private _handleRPC;
    /**
     * Runs a defined RPC method.
     * @private
     * @param {Object} message - a message received
     * @param {Object} socket_id - user's socket id
     * @param {String} ns - namespaces identifier
     * @return {Object|undefined}
     */
    private _runMethod;
    /**
     * Generate a new namespace store.
     * Also preregister some special namespace methods.
     * @private
     * @param {String} name - namespaces identifier
     * @return {undefined}
     */
    private _generateNamespace;
}
export {};
