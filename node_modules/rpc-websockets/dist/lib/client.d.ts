/**
 * "Client" wraps "ws" or a browser-implemented "WebSocket" library
 * according to the environment providing JSON RPC 2.0 support on top.
 * @module Client
 */
import { EventEmitter } from "eventemitter3";
import { NodeWebSocketType, ICommonWebSocketFactory } from "./client/client.types";
interface IQueueElement {
    promise: [
        Parameters<ConstructorParameters<typeof Promise>[0]>[0],
        Parameters<ConstructorParameters<typeof Promise>[0]>[1]
    ];
    timeout?: ReturnType<typeof setTimeout>;
}
export interface IQueue {
    [x: number]: IQueueElement;
}
export interface IWSRequestParams {
    [x: string]: any;
    [x: number]: any;
}
export default class CommonClient extends EventEmitter {
    private address;
    private rpc_id;
    private queue;
    private options;
    private autoconnect;
    private ready;
    private reconnect;
    private reconnect_timer_id;
    private reconnect_interval;
    private max_reconnects;
    private rest_options;
    private current_reconnects;
    private generate_request_id;
    private socket;
    private webSocketFactory;
    /**
     * Instantiate a Client class.
     * @constructor
     * @param {webSocketFactory} webSocketFactory - factory method for WebSocket
     * @param {String} address - url to a websocket server
     * @param {Object} options - ws options object with reconnect parameters
     * @param {Function} generate_request_id - custom generation request Id
     * @return {CommonClient}
     */
    constructor(webSocketFactory: ICommonWebSocketFactory, address?: string, { autoconnect, reconnect, reconnect_interval, max_reconnects, ...rest_options }?: {
        autoconnect?: boolean;
        reconnect?: boolean;
        reconnect_interval?: number;
        max_reconnects?: number;
    }, generate_request_id?: (method: string, params: object | Array<any>) => number);
    /**
     * Connects to a defined server if not connected already.
     * @method
     * @return {Undefined}
     */
    connect(): void;
    /**
     * Calls a registered RPC method on server.
     * @method
     * @param {String} method - RPC method name
     * @param {Object|Array} params - optional method parameters
     * @param {Number} timeout - RPC reply timeout value
     * @param {Object} ws_opts - options passed to ws
     * @return {Promise}
     */
    call(method: string, params?: IWSRequestParams, timeout?: number, ws_opts?: Parameters<NodeWebSocketType["send"]>[1]): Promise<unknown>;
    /**
     * Logins with the other side of the connection.
     * @method
     * @param {Object} params - Login credentials object
     * @return {Promise}
     */
    login(params: IWSRequestParams): Promise<unknown>;
    /**
     * Fetches a list of client's methods registered on server.
     * @method
     * @return {Array}
     */
    listMethods(): Promise<unknown>;
    /**
     * Sends a JSON-RPC 2.0 notification to server.
     * @method
     * @param {String} method - RPC method name
     * @param {Object} params - optional method parameters
     * @return {Promise}
     */
    notify(method: string, params?: IWSRequestParams): Promise<void>;
    /**
     * Subscribes for a defined event.
     * @method
     * @param {String|Array} event - event name
     * @return {Undefined}
     * @throws {Error}
     */
    subscribe(event: string | Array<string>): Promise<unknown>;
    /**
     * Unsubscribes from a defined event.
     * @method
     * @param {String|Array} event - event name
     * @return {Undefined}
     * @throws {Error}
     */
    unsubscribe(event: string | Array<string>): Promise<unknown>;
    /**
     * Closes a WebSocket connection gracefully.
     * @method
     * @param {Number} code - socket close code
     * @param {String} data - optional data to be sent before closing
     * @return {Undefined}
     */
    close(code?: number, data?: string): void;
    /**
     * Connection/Message handler.
     * @method
     * @private
     * @param {String} address - WebSocket API address
     * @param {Object} options - ws options object
     * @return {Undefined}
     */
    private _connect;
}
export {};
