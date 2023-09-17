/**
 * WebSocket implements a browser-side WebSocket specification.
 * @module Client
 */
"use strict";
import { EventEmitter } from "eventemitter3";
class WebSocketBrowserImpl extends EventEmitter {
    /** Instantiate a WebSocket class
     * @constructor
     * @param {String} address - url to a websocket server
     * @param {(Object)} options - websocket options
     * @param {(String|Array)} protocols - a list of protocols
     * @return {WebSocketBrowserImpl} - returns a WebSocket instance
     */
    constructor(address, options, protocols) {
        super();
        this.socket = new window.WebSocket(address, protocols);
        this.socket.onopen = () => this.emit("open");
        this.socket.onmessage = (event) => this.emit("message", event.data);
        this.socket.onerror = (error) => this.emit("error", error);
        this.socket.onclose = (event) => {
            this.emit("close", event.code, event.reason);
        };
    }
    /**
     * Sends data through a websocket connection
     * @method
     * @param {(String|Object)} data - data to be sent via websocket
     * @param {Object} optionsOrCallback - ws options
     * @param {Function} callback - a callback called once the data is sent
     * @return {Undefined}
     */
    send(data, optionsOrCallback, callback) {
        const cb = callback || optionsOrCallback;
        try {
            this.socket.send(data);
            cb();
        }
        catch (error) {
            cb(error);
        }
    }
    /**
     * Closes an underlying socket
     * @method
     * @param {Number} code - status code explaining why the connection is being closed
     * @param {String} reason - a description why the connection is closing
     * @return {Undefined}
     * @throws {Error}
     */
    close(code, reason) {
        this.socket.close(code, reason);
    }
    addEventListener(type, listener, options) {
        this.socket.addEventListener(type, listener, options);
    }
}
/**
 * factory method for common WebSocket instance
 * @method
 * @param {String} address - url to a websocket server
 * @param {(Object)} options - websocket options
 * @return {Undefined}
 */
export default function (address, options) {
    return new WebSocketBrowserImpl(address, options);
}
