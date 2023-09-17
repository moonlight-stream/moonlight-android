/**
 * WebSocket implements a browser-side WebSocket specification.
 * @module Client
 */
import { EventEmitter } from "eventemitter3";
import { BrowserWebSocketType, NodeWebSocketType, IWSClientAdditionalOptions } from "./client.types";
declare class WebSocketBrowserImpl extends EventEmitter {
    socket: BrowserWebSocketType;
    /** Instantiate a WebSocket class
     * @constructor
     * @param {String} address - url to a websocket server
     * @param {(Object)} options - websocket options
     * @param {(String|Array)} protocols - a list of protocols
     * @return {WebSocketBrowserImpl} - returns a WebSocket instance
     */
    constructor(address: string, options: {}, protocols?: string | string[]);
    /**
     * Sends data through a websocket connection
     * @method
     * @param {(String|Object)} data - data to be sent via websocket
     * @param {Object} optionsOrCallback - ws options
     * @param {Function} callback - a callback called once the data is sent
     * @return {Undefined}
     */
    send(data: Parameters<BrowserWebSocketType["send"]>[0], optionsOrCallback: (error?: Error) => void | Parameters<NodeWebSocketType["send"]>[1], callback?: () => void): void;
    /**
     * Closes an underlying socket
     * @method
     * @param {Number} code - status code explaining why the connection is being closed
     * @param {String} reason - a description why the connection is closing
     * @return {Undefined}
     * @throws {Error}
     */
    close(code?: number, reason?: string): void;
    addEventListener<K extends keyof WebSocketEventMap>(type: K, listener: (ev: WebSocketEventMap[K]) => any, options?: boolean | AddEventListenerOptions): void;
}
/**
 * factory method for common WebSocket instance
 * @method
 * @param {String} address - url to a websocket server
 * @param {(Object)} options - websocket options
 * @return {Undefined}
 */
export default function (address: string, options: IWSClientAdditionalOptions): WebSocketBrowserImpl;
export {};
